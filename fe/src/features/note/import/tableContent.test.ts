import type { JSONContent } from "@tiptap/react";
import { describe, expect, it } from "vitest";

import {
  buildTableNode,
  byteLength,
  MAX_COLS,
  MAX_ROWS,
  wouldExceedNoteLimit,
} from "./tableContent";

function cellText(cell: JSONContent): string {
  const p = cell.content?.[0];
  return p?.content?.[0]?.text ?? "";
}

describe("buildTableNode", () => {
  it("헤더가 있으면 첫 행은 tableHeader, 본문은 tableCell", () => {
    const { node, cols, rows } = buildTableNode({
      headers: ["A", "B"],
      rows: [
        ["1", "2"],
        ["3", "4"],
      ],
    });
    expect(node.type).toBe("table");
    expect(node.content).toHaveLength(3); // 헤더 + 2행
    expect(cols).toBe(2);
    expect(rows).toBe(2);

    const headerRow = node.content![0] as JSONContent;
    expect(headerRow.content!.every((c) => c.type === "tableHeader")).toBe(
      true,
    );
    expect(cellText(headerRow.content![0] as JSONContent)).toBe("A");

    const bodyRow = node.content![1] as JSONContent;
    expect(bodyRow.content!.every((c) => c.type === "tableCell")).toBe(true);
    expect(cellText(bodyRow.content![0] as JSONContent)).toBe("1");
  });

  it("헤더가 없으면 전부 tableCell", () => {
    const { node } = buildTableNode({ headers: null, rows: [["x"]] });
    const row = node.content![0] as JSONContent;
    expect((row.content![0] as JSONContent).type).toBe("tableCell");
  });

  it("빈 셀은 내용 없는 paragraph", () => {
    const { node } = buildTableNode({ headers: null, rows: [["", "v"]] });
    const row = node.content![0] as JSONContent;
    const emptyCell = row.content![0] as JSONContent;
    expect(emptyCell.content).toEqual([{ type: "paragraph" }]);
  });

  it("열 상한 초과 시 잘라내고 droppedCols 보고", () => {
    const wide = Array.from({ length: MAX_COLS + 5 }, (_, i) => String(i));
    const { cols, droppedCols } = buildTableNode({
      headers: null,
      rows: [wide],
    });
    expect(cols).toBe(MAX_COLS);
    expect(droppedCols).toBe(5);
  });

  it("행 상한 초과 시 잘라내고 droppedRows 보고", () => {
    const many = Array.from({ length: MAX_ROWS + 3 }, () => ["a"]);
    const { rows, droppedRows } = buildTableNode({ headers: null, rows: many });
    expect(rows).toBe(MAX_ROWS);
    expect(droppedRows).toBe(3);
  });
});

describe("wouldExceedNoteLimit", () => {
  it("작은 문서 + 작은 표는 초과하지 않는다", () => {
    const { node } = buildTableNode({ headers: ["A"], rows: [["1"]] });
    expect(wouldExceedNoteLimit({ type: "doc", content: [] }, node)).toBe(
      false,
    );
  });

  it("이미 큰 문서면 초과로 판정한다", () => {
    const { node } = buildTableNode({ headers: ["A"], rows: [["1"]] });
    const huge = { type: "doc", big: "x".repeat(1_000_001) };
    expect(wouldExceedNoteLimit(huge, node)).toBe(true);
  });

  it("byteLength는 UTF-8 바이트 수", () => {
    // JSON.stringify("한") = "한"(따옴표 2 + 3바이트 한글) = 5바이트
    expect(byteLength("한")).toBe(5);
  });
});
