import { describe, expect, it } from "vitest";

import type { OrderingItem } from "@/features/flashcard/api/flashcards";

import {
  fisherYates,
  reorder,
  sameOrder,
  shuffleDeck,
  shuffleForReview,
} from "./ordering";

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

describe("shuffleDeck", () => {
  it("원소 집합을 보존한다(비파괴, 임의 타입)", () => {
    const deck = [10, 20, 30, 40];
    const copy = [...deck];
    const out = shuffleDeck(deck, seq([0.1, 0.9, 0.5]));
    expect(deck).toEqual(copy); // 원본 불변
    expect([...out].sort((a, b) => a - b)).toEqual([10, 20, 30, 40]);
  });

  it("rng를 주면 결정적으로 섞는다", () => {
    // rng=0이면 각 i에서 j=0 → 2장 덱이 뒤집힌다.
    expect(shuffleDeck([1, 2], () => 0)).toEqual([2, 1]);
  });

  it("작은 덱이 원본과 같아도 강제로 바꾸지 않는다(정답 셔플과 다른 목적)", () => {
    // rng=0.99면 항등 순열 → 그대로 둔다.
    expect(shuffleDeck([1, 2, 3], () => 0.99)).toEqual([1, 2, 3]);
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
