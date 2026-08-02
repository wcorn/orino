import type { Slice } from "@tiptap/pm/model";
import type { DOMOutputSpec } from "@tiptap/pm/model";
import {
  DOMSerializer,
  type Node as PMNode,
  type Schema,
} from "@tiptap/pm/model";
import type { EditorView } from "@tiptap/pm/view";
import type { Editor } from "@tiptap/react";

import {
  fetchDatasetMeta,
  fetchDatasetRows,
  setDatasetName,
} from "../dataset/api/datasets";
import { getTableSnapshot, type TableSnapshot } from "../dataset/tableSnapshot";
import { createDatasetFromTable } from "../import/datasetImport";

/**
 * 표는 노트 문서가 아니라 별도 dataset에 산다. 그래서 표 블록을 그대로 복사하면 빈 껍데기가
 * 나가고(붙여넣으면 아무것도 안 생김), 같은 datasetId를 재사용하면 두 블록이 한 표를 공유해
 * 한쪽을 지울 때 다른 쪽 데이터까지 사라진다.
 *
 * 그래서 **표 모양으로 주고받는다**: 복사할 땐 진짜 `<table>`(과 마크다운 텍스트)로 내보내고,
 * 붙여넣을 땐 그 모양으로 **새 표를 만든다**. 덤으로 엑셀·노션에서 복사한 표도 그대로 붙는다.
 *
 * 값만 넘어간다 — 수식은 계산된 값으로 굳고, 셀 배경색·정렬·병합·열 너비는 따라가지 않는다.
 */

/** 복사한 표에 원본 id를 실어 둔다 — 앱 안에서 붙여넣을 땐 이걸로 원본 전체를 다시 읽는다. */
const SOURCE_ID_ATTR = "data-dataset-id";

/**
 * 표 스냅샷 → `<table>` 스펙. ProseMirror 직렬화기는 DOM 엘리먼트가 아니라 배열 스펙만 받는다
 * (`renderSpec`이 `structure[0]`을 태그명으로 읽는다).
 */
function snapshotToTableSpec(
  snapshot: TableSnapshot,
  datasetId: number | null,
): DOMOutputSpec {
  const cell = (tag: "th" | "td", value: string): DOMOutputSpec =>
    value ? [tag, {}, value] : [tag, {}];
  const attrs: Record<string, string> = {};
  if (datasetId != null) attrs[SOURCE_ID_ATTR] = String(datasetId);

  const children: DOMOutputSpec[] = [];
  if (snapshot.name) children.push(["caption", {}, snapshot.name]);
  if (snapshot.headers.length > 0) {
    children.push([
      "thead",
      {},
      ["tr", {}, ...snapshot.headers.map((h) => cell("th", h))],
    ]);
  }
  children.push([
    "tbody",
    {},
    ...snapshot.rows.map((row) => ["tr", {}, ...row.map((v) => cell("td", v))]),
  ]);
  return ["table", attrs, ...children];
}

/** 표 블록만 `<table>`로 바꿔 내보내는 직렬화기. 그 외 노드는 기본 그대로. */
function buildSerializer(schema: Schema): DOMSerializer {
  const base = DOMSerializer.fromSchema(schema);
  return new DOMSerializer(
    {
      ...base.nodes,
      datasetTable: (node: PMNode) => {
        const datasetId = node.attrs.datasetId as number | null;
        const snapshot = datasetId == null ? null : getTableSnapshot(datasetId);
        return snapshotToTableSpec(
          snapshot ?? { name: null, headers: [], rows: [] },
          datasetId,
        );
      },
    },
    base.marks,
  );
}

/** 마크다운 표 한 줄. 파이프는 escape한다. */
const mdRow = (cells: string[]) =>
  `| ${cells.map((c) => c.replace(/\|/g, "\\|")).join(" | ")} |`;

/** 표를 마크다운 표로 적는다 — 메모장·슬랙 등 텍스트만 받는 곳에서도 표 모양이 남는다. */
function snapshotToMarkdown(snapshot: TableSnapshot): string {
  const width = Math.max(
    snapshot.headers.length,
    ...snapshot.rows.map((r) => r.length),
    0,
  );
  if (width === 0) return "";
  const pad = (cells: string[]) =>
    Array.from({ length: width }, (_, i) => cells[i] ?? "");
  const lines: string[] = [];
  if (snapshot.name) lines.push(snapshot.name);
  lines.push(mdRow(pad(snapshot.headers)));
  lines.push(`| ${Array.from({ length: width }, () => "---").join(" | ")} |`);
  snapshot.rows.forEach((row) => lines.push(mdRow(pad(row))));
  return lines.join("\n");
}

/** 텍스트 플레이버 직렬화 — 표만 마크다운으로 바꾸고 나머지는 기본(문단 사이 빈 줄)과 같게. */
function tableAwareClipboardText(slice: Slice): string {
  const parts: string[] = [];
  slice.content.forEach((node) => {
    if (node.type.name === "datasetTable") {
      const datasetId = node.attrs.datasetId as number | null;
      const snapshot = datasetId == null ? null : getTableSnapshot(datasetId);
      parts.push(snapshot ? snapshotToMarkdown(snapshot) : "");
      return;
    }
    parts.push(node.textBetween(0, node.content.size, "\n"));
  });
  return parts.join("\n\n");
}

/** `<table>` 엘리먼트 → 헤더/행. thead가 있거나 첫 행이 전부 th면 헤더로 본다. */
function readTableElement(table: HTMLTableElement): {
  name: string | null;
  headers: string[] | null;
  rows: string[][];
} {
  const name = table.querySelector("caption")?.textContent?.trim() || null;
  const trs = [...table.querySelectorAll("tr")];
  const cellsOf = (tr: HTMLTableRowElement) =>
    [...tr.children].map((c) => (c.textContent ?? "").trim());

  const first = trs[0];
  const firstIsHeader =
    !!first &&
    first.children.length > 0 &&
    [...first.children].every((c) => c.tagName === "TH");

  const headers = firstIsHeader ? cellsOf(first) : null;
  const bodyRows = (firstIsHeader ? trs.slice(1) : trs).map(cellsOf);
  return { name, headers, rows: bodyRows };
}

/**
 * 붙여넣을 표의 내용. 앱 안에서 복사한 것(원본 id가 있고 아직 살아 있음)이면 API로 원본
 * 전체를 읽는다 — 화면에 안 뜬 행까지 온전히 따라오게. 실패하면 붙여넣은 HTML로 되돌아간다.
 */
async function resolveTable(table: HTMLTableElement) {
  const fromHtml = readTableElement(table);
  const sourceId = Number(table.getAttribute(SOURCE_ID_ATTR));
  if (!Number.isFinite(sourceId) || sourceId <= 0) return fromHtml;

  try {
    const meta = await fetchDatasetMeta(sourceId);
    const page = await fetchDatasetRows(sourceId, 0, meta.rowCount);
    return {
      name: meta.name ?? fromHtml.name,
      headers: meta.columns.map((c) => c.label),
      rows: page.rows.map((row) =>
        meta.columns.map((_c, i) => row.cells[i] ?? ""),
      ),
    };
  } catch {
    // 원본이 지워졌거나 접근 불가 — 클립보드에 담겨 온 모양 그대로 만든다.
    return fromHtml;
  }
}

/**
 * 붙여넣은 HTML 안의 `<table>`을 각각 **새 표**로 만들고, 그 자리에 표 블록을 끼운 HTML을 돌려준다.
 * 표가 없으면 null(= 우리가 처리할 일이 아님).
 */
export async function buildPasteHtmlWithTables(
  html: string,
): Promise<string | null> {
  const doc = new DOMParser().parseFromString(html, "text/html");
  const tables = [...doc.querySelectorAll("table")];
  if (tables.length === 0) return null;

  for (const table of tables) {
    const { name, headers, rows } = await resolveTable(table);
    // 값이 하나도 없는 표는 만들지 않는다(빈 <table> 조각이 섞여 온 경우).
    if (rows.length === 0 && !headers) {
      table.remove();
      continue;
    }
    const datasetId = await createDatasetFromTable({ headers, rows });
    if (name) await setDatasetName(datasetId, name);

    const block = doc.createElement("div");
    block.setAttribute("data-dataset-table", "");
    block.setAttribute("data-dataset-id", String(datasetId));
    table.replaceWith(block);
  }
  return doc.body.innerHTML;
}

/**
 * 표가 든 복사·잘라내기를 처리한다. 처리했으면 true(호출부가 기본 동작을 막는다).
 *
 * `clipboardSerializer`로 갈아끼우지 않고 복사 이벤트를 직접 잡는 이유: 표가 없는 평범한
 * 복사는 ProseMirror 기본 경로를 그대로 태워야 slice 정보(data-pm-slice)가 보존된다.
 * 표가 낀 복사에서만 우리가 나선다.
 */
export function handleTableCopy(
  view: EditorView,
  event: ClipboardEvent,
): boolean {
  const slice = view.state.selection.content();
  let hasTable = false;
  slice.content.descendants((node) => {
    if (node.type.name === "datasetTable") hasTable = true;
    return !hasTable;
  });
  if (!hasTable || !event.clipboardData) return false;

  const wrap = document.createElement("div");
  wrap.append(
    buildSerializer(view.state.schema).serializeFragment(slice.content),
  );
  event.clipboardData.setData("text/html", wrap.innerHTML);
  event.clipboardData.setData("text/plain", tableAwareClipboardText(slice));
  event.preventDefault();
  return true;
}

/**
 * 표가 든 붙여넣기를 처리한다. 처리했으면 true.
 * 새 표를 만드는 데 네트워크가 필요해 비동기다 — 호출부는 이벤트를 막고 이 결과를 기다리지 않는다.
 */
export function handleTablePaste(editor: Editor, html: string): boolean {
  if (!/<table[\s>]/i.test(html)) return false;
  void (async () => {
    const next = await buildPasteHtmlWithTables(html);
    if (next == null) return;
    // insertContent는 transformPasted(표 제거)를 타지 않으므로 표 블록이 그대로 들어간다.
    editor.chain().focus().insertContent(next).run();
  })();
  return true;
}
