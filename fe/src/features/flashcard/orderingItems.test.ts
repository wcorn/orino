import { describe, expect, it } from "vitest";

import type { OrderingItem } from "./api/flashcards";
import {
  ensureMinItems,
  isOrderingValid,
  itemsEqual,
  MAX_ORDERING_ITEMS,
  MIN_ORDERING_ITEMS,
  normalizeItems,
  reorderItems,
} from "./orderingItems";

const item = (id: string, text: string): OrderingItem => ({ id, text });

describe("ensureMinItems", () => {
  it("3개 미만이면 빈 항목으로 최소 개수를 채운다", () => {
    const result = ensureMinItems([item("a", "1")]);
    expect(result).toHaveLength(MIN_ORDERING_ITEMS);
    expect(result[0]).toEqual(item("a", "1"));
    expect(result[1].text).toBe("");
    expect(result[1].id).not.toBe("");
  });

  it("이미 3개 이상이면 그대로 둔다", () => {
    const src = [
      item("a", "1"),
      item("b", "2"),
      item("c", "3"),
      item("d", "4"),
    ];
    expect(ensureMinItems(src)).toEqual(src);
  });
});

describe("isOrderingValid", () => {
  const three = [item("a", "1"), item("b", "2"), item("c", "3")];

  it("3~7개 + 모든 text 1~1000자면 유효", () => {
    expect(isOrderingValid(three, 1000)).toBe(true);
  });

  it("3개 미만이면 무효", () => {
    expect(isOrderingValid(three.slice(0, 2), 1000)).toBe(false);
  });

  it("7개 초과면 무효", () => {
    const eight = Array.from({ length: MAX_ORDERING_ITEMS + 1 }, (_, i) =>
      item(`i${i}`, `t${i}`),
    );
    expect(isOrderingValid(eight, 1000)).toBe(false);
  });

  it("빈(공백) 항목이 있으면 무효", () => {
    expect(isOrderingValid([...three.slice(0, 2), item("c", "  ")], 1000)).toBe(
      false,
    );
  });

  it("최대 길이를 넘는 항목이 있으면 무효", () => {
    const long = item("c", "x".repeat(1001));
    expect(isOrderingValid([...three.slice(0, 2), long], 1000)).toBe(false);
  });
});

describe("reorderItems", () => {
  const src = [item("a", "1"), item("b", "2"), item("c", "3")];

  it("activeId를 overId 위치로 이동한다", () => {
    expect(reorderItems(src, "a", "c").map((i) => i.id)).toEqual([
      "b",
      "c",
      "a",
    ]);
  });

  it("같은 위치면 원본을 그대로 반환", () => {
    expect(reorderItems(src, "b", "b")).toBe(src);
  });

  it("존재하지 않는 id면 원본을 그대로 반환", () => {
    expect(reorderItems(src, "a", "zzz")).toBe(src);
  });
});

describe("normalizeItems", () => {
  it("각 text를 trim하고 순서는 유지한다", () => {
    const result = normalizeItems([item("a", "  x "), item("b", "y")]);
    expect(result).toEqual([item("a", "x"), item("b", "y")]);
  });
});

describe("itemsEqual", () => {
  const base = [item("a", "1"), item("b", "2")];

  it("id·text·순서가 같으면 true", () => {
    expect(itemsEqual(base, [item("a", "1"), item("b", "2")])).toBe(true);
  });

  it("순서가 다르면 false", () => {
    expect(itemsEqual(base, [item("b", "2"), item("a", "1")])).toBe(false);
  });

  it("text가 다르면 false", () => {
    expect(itemsEqual(base, [item("a", "1"), item("b", "changed")])).toBe(
      false,
    );
  });

  it("길이가 다르면 false", () => {
    expect(itemsEqual(base, [item("a", "1")])).toBe(false);
  });
});
