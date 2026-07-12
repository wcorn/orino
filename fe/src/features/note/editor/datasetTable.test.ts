import { describe, expect, it } from "vitest";

import { collectDatasetIds, DatasetTable } from "./datasetTable";

describe("collectDatasetIds", () => {
  it("중첩 doc에서 모든 datasetTable datasetId를 수집한다", () => {
    const doc = {
      type: "doc",
      content: [
        { type: "paragraph", content: [{ type: "text", text: "hi" }] },
        { type: "datasetTable", attrs: { datasetId: 7 } },
        {
          type: "bulletList",
          content: [
            {
              type: "listItem",
              content: [{ type: "datasetTable", attrs: { datasetId: 8 } }],
            },
          ],
        },
      ],
    };

    expect([...collectDatasetIds(doc)].sort()).toEqual([7, 8]);
  });

  it("datasetTable이 없으면 빈 집합", () => {
    const doc = {
      type: "doc",
      content: [{ type: "paragraph", content: [{ type: "text", text: "x" }] }],
    };
    expect(collectDatasetIds(doc).size).toBe(0);
  });

  it("datasetId가 number가 아니면 무시한다", () => {
    const doc = {
      type: "doc",
      content: [{ type: "datasetTable", attrs: {} }],
    };
    expect(collectDatasetIds(doc).size).toBe(0);
  });
});

describe("DatasetTable 노드 스펙", () => {
  it("atom block 이고 draggable=true 이다", () => {
    expect(DatasetTable.config.atom).toBe(true);
    expect(DatasetTable.config.draggable).toBe(true);
  });
});
