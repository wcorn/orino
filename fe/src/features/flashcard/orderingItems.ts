import { arrayMove } from "@dnd-kit/sortable";

import type { OrderingItem } from "./api/flashcards";

export const MIN_ORDERING_ITEMS = 3;
export const MAX_ORDERING_ITEMS = 7;

/** 순서 카드 항목의 드래그 안정 키. 카드 내에서 유일하면 되므로 UUID로 생성한다. */
export function newOrderingItemId(): string {
  return crypto.randomUUID();
}

export function createOrderingItem(text = ""): OrderingItem {
  return { id: newOrderingItemId(), text };
}

/** 3개 미만이면 편집 시작 시 최소 개수를 채운다(빈 항목). */
export function ensureMinItems(items: OrderingItem[]): OrderingItem[] {
  const filled = [...items];
  while (filled.length < MIN_ORDERING_ITEMS) {
    filled.push(createOrderingItem());
  }
  return filled;
}

/** 저장 페이로드용: 각 항목 text를 trim. 순서는 화면 순서를 그대로 유지. */
export function normalizeItems(items: OrderingItem[]): OrderingItem[] {
  return items.map((item) => ({ id: item.id, text: item.text.trim() }));
}

/** ORDERING 저장 가능 여부: 3~7개 + 모든 text 1~1000자. */
export function isOrderingValid(
  items: OrderingItem[],
  maxLen: number,
): boolean {
  if (items.length < MIN_ORDERING_ITEMS || items.length > MAX_ORDERING_ITEMS) {
    return false;
  }
  return items.every((item) => {
    const t = item.text.trim();
    return t.length >= 1 && t.length <= maxLen;
  });
}

/** 드래그 종료 시 activeId를 overId 위치로 이동한 새 배열. 대상이 없거나 동일하면 원본 유지. */
export function reorderItems(
  items: OrderingItem[],
  activeId: string,
  overId: string,
): OrderingItem[] {
  if (activeId === overId) return items;
  const from = items.findIndex((i) => i.id === activeId);
  const to = items.findIndex((i) => i.id === overId);
  if (from === -1 || to === -1) return items;
  return arrayMove(items, from, to);
}

/** 편집 dirty 판정: id+text+순서가 초기값과 동일한지. */
export function itemsEqual(a: OrderingItem[], b: OrderingItem[]): boolean {
  if (a.length !== b.length) return false;
  return a.every((item, i) => item.id === b[i].id && item.text === b[i].text);
}
