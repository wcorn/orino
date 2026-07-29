import { describe, expect, it } from "vitest";

import type { Flashcard } from "./api/flashcards";
import { groupFlashcards } from "./grouping";

function card(
  id: number,
  front: string,
  siblingGroupId: number | null = null,
): Flashcard {
  return {
    id,
    materialId: 1,
    type: "BASIC",
    front,
    back: `${front}-뒤`,
    items: null,
    siblingGroupId,
    nextReview: null,
    createdAt: "2026-05-18T00:00:00",
  };
}

describe("groupFlashcards", () => {
  it("단방향 카드는 각각 한 행이 된다", () => {
    const rows = groupFlashcards([card(1, "A"), card(2, "B")]);

    expect(rows).toHaveLength(2);
    expect(rows.every((r) => r.kind === "single")).toBe(true);
  });

  it("같은 siblingGroupId 2장은 한 행(pair)으로 묶인다", () => {
    const rows = groupFlashcards([card(1, "정의", 1), card(2, "설명", 1)]);

    expect(rows).toHaveLength(1);
    expect(rows[0].kind).toBe("pair");
    expect(rows[0].kind === "pair" && rows[0].cards.map((c) => c.id)).toEqual([
      1, 2,
    ]);
  });

  it("짝 행은 먼저 등장한 카드의 자리를 차지한다", () => {
    const rows = groupFlashcards([
      card(1, "단독"),
      card(2, "정의", 2),
      card(3, "다른 단독"),
      card(4, "설명", 2),
    ]);

    expect(rows.map((r) => r.kind)).toEqual(["single", "pair", "single"]);
  });

  it("페이지 경계로 짝이 한 장만 로드된 동안은 단독 행으로 보인다", () => {
    const rows = groupFlashcards([card(1, "정의", 1)]);

    expect(rows).toHaveLength(1);
    expect(rows[0].kind).toBe("single");
  });

  it("다음 페이지에서 짝이 도착하면 한 행으로 합쳐진다", () => {
    const firstPage = [card(1, "정의", 1)];
    const afterNextPage = [...firstPage, card(2, "설명", 1)];

    expect(groupFlashcards(firstPage)).toHaveLength(1);
    expect(groupFlashcards(afterNextPage)[0].kind).toBe("pair");
  });

  it("행 key는 카드/짝마다 고유하다", () => {
    const rows = groupFlashcards([
      card(1, "정의", 1),
      card(2, "설명", 1),
      card(3, "단독"),
    ]);

    expect(new Set(rows.map((r) => r.key)).size).toBe(rows.length);
  });
});
