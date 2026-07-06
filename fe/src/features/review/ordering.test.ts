import { describe, expect, it } from "vitest";

import type { OrderingItem } from "@/features/flashcard/api/flashcards";

import { fisherYates, reorder, sameOrder, shuffleForReview } from "./ordering";

const items: OrderingItem[] = [
  { id: "a", text: "1" },
  { id: "b", text: "2" },
  { id: "c", text: "3" },
  { id: "d", text: "4" },
];

/** 정해진 시퀀스를 순서대로 뱉는 rng 스텁. */
function seq(values: number[]): () => number {
  let i = 0;
  return () => values[i++ % values.length];
}

describe("sameOrder", () => {
  it("id 순서가 같으면 true, 다르면 false", () => {
    expect(sameOrder(items, [...items])).toBe(true);
    expect(sameOrder(items, [items[1], items[0], items[2], items[3]])).toBe(
      false,
    );
  });
});

describe("fisherYates", () => {
  it("항목을 보존하는 순열이다(비파괴)", () => {
    const copy = [...items];
    const out = fisherYates(items, seq([0.99, 0.5, 0.1]));
    expect(items).toEqual(copy); // 원본 불변
    expect([...out].map((i) => i.id).sort()).toEqual(["a", "b", "c", "d"]);
  });
});

describe("shuffleForReview", () => {
  it("항목 집합을 보존한다", () => {
    const out = shuffleForReview(items, Math.random);
    expect(out.map((i) => i.id).sort()).toEqual(["a", "b", "c", "d"]);
  });

  it("항상 정답 순서와 다르게 만든다", () => {
    for (let s = 0; s < 50; s++) {
      const out = shuffleForReview(items, seq([s / 50, ((s * 7) % 11) / 11]));
      expect(sameOrder(out, items)).toBe(false);
    }
  });

  it("rng가 매번 항등 순열을 내도 앞 두 항목 교환으로 정답을 회피한다", () => {
    // rng=0이면 Fisher–Yates가 항등 순열 → 재셔플해도 동일 → 스왑 폴백
    const out = shuffleForReview(items, () => 0);
    expect(sameOrder(out, items)).toBe(false);
    expect(out.map((i) => i.id).sort()).toEqual(["a", "b", "c", "d"]);
  });

  it("항목이 2개 미만이면 그대로 둔다", () => {
    const one = [{ id: "x", text: "only" }];
    expect(shuffleForReview(one, () => 0)).toEqual(one);
  });
});

describe("reorder", () => {
  it("activeId를 overId 위치로 이동한다", () => {
    expect(reorder(items, "a", "c").map((i) => i.id)).toEqual([
      "b",
      "c",
      "a",
      "d",
    ]);
  });

  it("같은 id면 원본을 그대로 반환", () => {
    expect(reorder(items, "b", "b")).toBe(items);
  });

  it("존재하지 않는 id면 원본을 그대로 반환", () => {
    expect(reorder(items, "a", "zzz")).toBe(items);
  });
});
