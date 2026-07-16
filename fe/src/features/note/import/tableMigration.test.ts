import type { JSONContent } from "@tiptap/react";
import { describe, expect, it, vi } from "vitest";

import { migrateDocTables, tableNodeToNormalized } from "./tableMigration";

const para = (text?: string): JSONContent =>
  text == null
    ? { type: "paragraph" }
    : { type: "paragraph", content: [{ type: "text", text }] };

const cell = (
  text: string | undefined,
  attrs?: Record<string, unknown>,
): JSONContent => ({
  type: "tableCell",
  content: [para(text)],
  ...(attrs ? { attrs } : {}),
});

const header = (text: string): JSONContent => ({
  type: "tableHeader",
  content: [para(text)],
});

const row = (cells: JSONContent[]): JSONContent => ({
  type: "tableRow",
  content: cells,
});

describe("tableNodeToNormalized", () => {
  it("첫 행이 전부 tableHeader면 headers로 승격하고 나머지는 본문", () => {
    const table: JSONContent = {
      type: "table",
      content: [
        row([header("H1"), header("H2")]),
        row([cell("a"), cell("b")]),
        row([cell("c"), cell("d")]),
      ],
    };

    const { normalized, warnings } = tableNodeToNormalized(table);

    expect(normalized.headers).toEqual(["H1", "H2"]);
    expect(normalized.rows).toEqual([
      ["a", "b"],
      ["c", "d"],
    ]);
    expect(warnings).toHaveLength(0);
  });

  it("헤더 행이 없으면 headers=null, 전부 본문", () => {
    const table: JSONContent = {
      type: "table",
      content: [row([cell("a"), cell("b")]), row([cell("c"), cell("d")])],
    };

    const { normalized } = tableNodeToNormalized(table);

    expect(normalized.headers).toBeNull();
    expect(normalized.rows).toEqual([
      ["a", "b"],
      ["c", "d"],
    ]);
  });

  it("여러 문단 셀은 줄바꿈으로 잇고 빈 문단은 빈 문자열", () => {
    const table: JSONContent = {
      type: "table",
      content: [
        row([
          { type: "tableCell", content: [para("l1"), para("l2")] },
          cell(undefined),
        ]),
      ],
    };

    const { normalized } = tableNodeToNormalized(table);

    expect(normalized.rows).toEqual([["l1\nl2", ""]]);
  });

  it("colspan은 빈 열로 펼치고 경고를 남긴다", () => {
    const table: JSONContent = {
      type: "table",
      content: [row([cell("x", { colspan: 2 }), cell("y")])],
    };

    const { normalized, warnings } = tableNodeToNormalized(table);

    expect(normalized.rows).toEqual([["x", "", "y"]]);
    expect(warnings.map((w) => w.kind)).toContain("colspan");
  });

  it("rowspan과 셀 내 이미지는 경고로 남기되 텍스트는 보존", () => {
    const table: JSONContent = {
      type: "table",
      content: [
        row([
          {
            type: "tableCell",
            attrs: { rowspan: 2 },
            content: [
              {
                type: "paragraph",
                content: [
                  { type: "image", attrs: { src: "x" } },
                  { type: "text", text: "설명" },
                ],
              },
            ],
          },
        ]),
      ],
    };

    const { normalized, warnings } = tableNodeToNormalized(table);

    expect(normalized.rows).toEqual([["설명"]]);
    const kinds = warnings.map((w) => w.kind);
    expect(kinds).toContain("rowspan");
    expect(kinds).toContain("image");
  });
});

describe("migrateDocTables", () => {
  it("본문 중 table을 datasetTable로 치환하고 주변 노드는 보존한다", async () => {
    const doc: JSONContent = {
      type: "doc",
      content: [
        para("앞 문단"),
        {
          type: "table",
          content: [row([header("A")]), row([cell("1")])],
        },
        para("뒤 문단"),
      ],
    };
    const convert = vi.fn(async () => 42);

    const {
      doc: out,
      converted,
      warnings,
    } = await migrateDocTables(doc, convert);

    expect(converted).toBe(1);
    expect(warnings).toHaveLength(0);
    expect(out.content).toEqual([
      para("앞 문단"),
      { type: "datasetTable", attrs: { datasetId: 42 } },
      para("뒤 문단"),
    ]);
    expect(convert).toHaveBeenCalledTimes(1);
    expect(convert).toHaveBeenCalledWith({
      headers: ["A"],
      rows: [["1"]],
    });
  });

  it("표가 여러 개면 순서대로 각기 다른 datasetId로 치환한다", async () => {
    const table = (v: string): JSONContent => ({
      type: "table",
      content: [row([cell(v)])],
    });
    const doc: JSONContent = {
      type: "doc",
      content: [table("first"), table("second")],
    };
    let next = 100;
    const convert = vi.fn(async () => next++);

    const { doc: out, converted } = await migrateDocTables(doc, convert);

    expect(converted).toBe(2);
    expect(out.content).toEqual([
      { type: "datasetTable", attrs: { datasetId: 100 } },
      { type: "datasetTable", attrs: { datasetId: 101 } },
    ]);
  });

  it("표가 없으면 doc을 그대로 두고 convert를 호출하지 않는다", async () => {
    const doc: JSONContent = {
      type: "doc",
      content: [para("그냥 텍스트")],
    };
    const convert = vi.fn(async () => 1);

    const { converted, doc: out } = await migrateDocTables(doc, convert);

    expect(converted).toBe(0);
    expect(convert).not.toHaveBeenCalled();
    expect(out).toEqual(doc);
  });

  it("열이 없는 표는 건너뛰고 emptyTable 경고를 남긴다", async () => {
    const doc: JSONContent = {
      type: "doc",
      content: [
        { type: "table", content: [{ type: "tableRow", content: [] }] },
      ],
    };
    const convert = vi.fn(async () => 1);

    const { converted, warnings } = await migrateDocTables(doc, convert);

    expect(converted).toBe(0);
    expect(convert).not.toHaveBeenCalled();
    expect(warnings.map((w) => w.kind)).toContain("emptyTable");
  });

  it("원본 doc은 변경하지 않는다(불변)", async () => {
    const doc: JSONContent = {
      type: "doc",
      content: [{ type: "table", content: [row([cell("x")])] }],
    };
    const snapshot = JSON.stringify(doc);

    await migrateDocTables(doc, async () => 7);

    expect(JSON.stringify(doc)).toBe(snapshot);
  });
});
