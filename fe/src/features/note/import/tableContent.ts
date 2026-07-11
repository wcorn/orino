import type { JSONContent } from "@tiptap/react";

/** 표 상한(초과 시 잘라서 삽입 + 경고). */
export const MAX_COLS = 30;
export const MAX_ROWS = 200;
/** 노트 content 직렬화 크기 상한(BE 1MB 제한). 근접 시 삽입을 막는다. */
export const NOTE_MAX_BYTES = 1_000_000;

/** 정규화된 표 입력. headers가 있으면 헤더 행으로, 없으면 전부 본문. */
export interface NormalizedTable {
  headers: string[] | null;
  rows: string[][];
}

export interface TableBuildResult {
  node: JSONContent;
  /** 상한 초과로 잘린 열 수. */
  droppedCols: number;
  /** 상한 초과로 잘린 본문 행 수. */
  droppedRows: number;
  /** 삽입되는 최종 열 수. */
  cols: number;
  /** 삽입되는 최종 본문 행 수(헤더 제외). */
  rows: number;
}

/** 정규화된 2D 표를 Tiptap `table` 노드로 변환한다. 상한을 넘으면 잘라내고 잘린 수를 보고한다. */
export function buildTableNode(input: NormalizedTable): TableBuildResult {
  const totalCols = Math.max(
    input.headers?.length ?? 0,
    ...input.rows.map((r) => r.length),
    0,
  );
  const cols = Math.min(totalCols, MAX_COLS);
  const droppedCols = Math.max(0, totalCols - cols);

  const keptBody = input.rows.slice(0, MAX_ROWS);
  const droppedRows = input.rows.length - keptBody.length;

  const content: JSONContent[] = [];
  if (input.headers) content.push(rowNode(input.headers, cols, true));
  for (const row of keptBody) content.push(rowNode(row, cols, false));
  if (content.length === 0) content.push(rowNode([], Math.max(cols, 1), false));

  return {
    node: { type: "table", content },
    droppedCols,
    droppedRows,
    cols,
    rows: keptBody.length,
  };
}

function rowNode(cells: string[], cols: number, header: boolean): JSONContent {
  const cellType = header ? "tableHeader" : "tableCell";
  const content: JSONContent[] = [];
  for (let c = 0; c < cols; c++) {
    content.push({ type: cellType, content: [paragraph(cells[c] ?? "")] });
  }
  return { type: "tableRow", content };
}

function paragraph(text: string): JSONContent {
  const trimmed = text ?? "";
  return trimmed === ""
    ? { type: "paragraph" }
    : { type: "paragraph", content: [{ type: "text", text: trimmed }] };
}

/** 값의 JSON 직렬화 UTF-8 바이트 수. */
export function byteLength(value: unknown): number {
  return new TextEncoder().encode(JSON.stringify(value)).length;
}

/** 현재 문서에 노드를 삽입하면 노트 크기 상한을 넘는지. */
export function wouldExceedNoteLimit(
  currentDoc: unknown,
  node: JSONContent,
): boolean {
  return byteLength(currentDoc) + byteLength(node) > NOTE_MAX_BYTES;
}
