import type { JSONContent } from "@tiptap/react";

import { columnCount } from "./datasetColumns";
import type { NormalizedTable } from "./tableContent";

/**
 * 레거시 native Tiptap 표(`table` 노드)를 datasetTable로 옮길 때 발생하는 손실.
 * dataset 셀은 평문 문자열이라 서식/이미지/병합은 그대로 보존되지 않는다.
 */
export interface MigrationWarning {
  kind: "colspan" | "rowspan" | "image" | "emptyTable";
  detail: string;
}

/** 노드 트리에서 텍스트만 모은다(서식 제거). 이미지 노드는 경고로 남긴다. */
function collectText(
  node: JSONContent,
  parts: string[],
  warnings: MigrationWarning[],
): void {
  if (!node || typeof node !== "object") return;
  if (node.type === "text" && typeof node.text === "string") {
    parts.push(node.text);
  } else if (node.type === "image") {
    warnings.push({
      kind: "image",
      detail: "셀 안의 이미지는 텍스트로 보존되지 않습니다",
    });
  }
  if (Array.isArray(node.content)) {
    for (const child of node.content) {
      collectText(child as JSONContent, parts, warnings);
    }
  }
}

/** 셀(tableHeader|tableCell)의 텍스트. 블록(문단 등)은 줄바꿈으로 잇는다. */
function cellText(cell: JSONContent, warnings: MigrationWarning[]): string {
  const blocks = Array.isArray(cell.content) ? cell.content : [];
  return blocks
    .map((block) => {
      const parts: string[] = [];
      collectText(block as JSONContent, parts, warnings);
      return parts.join("");
    })
    .join("\n");
}

/**
 * native `table` 노드 → NormalizedTable + 손실 경고.
 * 첫 행의 셀이 전부 tableHeader면 headers로 승격하고 나머지를 본문으로 둔다.
 * colspan은 빈 열을 채워 정렬을 맞추고, rowspan/이미지는 경고만 남긴다.
 */
export function tableNodeToNormalized(table: JSONContent): {
  normalized: NormalizedTable;
  warnings: MigrationWarning[];
} {
  const warnings: MigrationWarning[] = [];
  const tableRows = (Array.isArray(table.content) ? table.content : []).filter(
    (r): r is JSONContent =>
      !!r && typeof r === "object" && (r as JSONContent).type === "tableRow",
  );

  let headers: string[] | null = null;
  const rows: string[][] = [];

  tableRows.forEach((row, rowIndex) => {
    const cells = Array.isArray(row.content)
      ? (row.content as JSONContent[])
      : [];
    const allHeader =
      cells.length > 0 && cells.every((c) => c.type === "tableHeader");

    const line: string[] = [];
    for (const cell of cells) {
      const colspan = Number(cell.attrs?.colspan ?? 1) || 1;
      const rowspan = Number(cell.attrs?.rowspan ?? 1) || 1;
      if (colspan > 1) {
        warnings.push({
          kind: "colspan",
          detail: `병합 셀(colspan=${colspan})을 빈 열로 펼쳤습니다`,
        });
      }
      if (rowspan > 1) {
        warnings.push({
          kind: "rowspan",
          detail: `세로 병합(rowspan=${rowspan})은 첫 행에만 값이 남습니다`,
        });
      }
      line.push(cellText(cell, warnings));
      for (let k = 1; k < colspan; k++) line.push("");
    }

    if (rowIndex === 0 && allHeader) headers = line;
    else rows.push(line);
  });

  return { normalized: { headers, rows }, warnings };
}

export interface MigrationResult {
  /** 변환된 새 doc. 원본은 수정하지 않는다. */
  doc: JSONContent;
  /** datasetTable로 치환된 표 개수. */
  converted: number;
  warnings: MigrationWarning[];
}

/**
 * doc을 순회하며 모든 `table` 노드를 datasetTable 참조 노드로 치환한다.
 * convert(정규화표)는 dataset을 만들고 datasetId를 돌려주는 주입 함수다
 * (앱에선 createDatasetFromTable, dry-run에선 로컬 스텁). 열이 0개인 표는 건너뛴다.
 */
export async function migrateDocTables(
  doc: JSONContent,
  convert: (table: NormalizedTable) => Promise<number>,
): Promise<MigrationResult> {
  const warnings: MigrationWarning[] = [];
  let converted = 0;

  const walk = async (node: JSONContent): Promise<JSONContent> => {
    if (node.type === "table") {
      const { normalized, warnings: w } = tableNodeToNormalized(node);
      warnings.push(...w);
      if (columnCount(normalized) === 0) {
        warnings.push({
          kind: "emptyTable",
          detail: "열이 없는 표라 변환하지 않고 그대로 두었습니다",
        });
        return node;
      }
      const datasetId = await convert(normalized);
      converted += 1;
      return { type: "datasetTable", attrs: { datasetId } };
    }
    if (Array.isArray(node.content)) {
      const content: JSONContent[] = [];
      for (const child of node.content as JSONContent[]) {
        content.push(await walk(child));
      }
      return { ...node, content };
    }
    return node;
  };

  const migrated = await walk(doc);
  return { doc: migrated, converted, warnings };
}
