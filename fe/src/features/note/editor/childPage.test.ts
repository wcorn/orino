import { describe, expect, it } from "vitest";

import { collectChildPageIds } from "./childPage";

describe("collectChildPageIds", () => {
  it("중첩 doc에서 모든 childPage noteId를 수집한다", () => {
    const doc = {
      type: "doc",
      content: [
        { type: "paragraph", content: [{ type: "text", text: "hi" }] },
        { type: "childPage", attrs: { noteId: 11, title: "A" } },
        {
          type: "bulletList",
          content: [
            {
              type: "listItem",
              content: [
                { type: "childPage", attrs: { noteId: 22, title: "B" } },
              ],
            },
          ],
        },
      ],
    };

    const ids = collectChildPageIds(doc);
    expect([...ids].sort()).toEqual([11, 22]);
  });

  it("childPage가 없으면 빈 집합", () => {
    const doc = {
      type: "doc",
      content: [{ type: "paragraph", content: [{ type: "text", text: "x" }] }],
    };
    expect(collectChildPageIds(doc).size).toBe(0);
  });

  it("noteId가 number가 아니면 무시한다", () => {
    const doc = {
      type: "doc",
      content: [{ type: "childPage", attrs: { title: "noid" } }],
    };
    expect(collectChildPageIds(doc).size).toBe(0);
  });
});
