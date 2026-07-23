import { arrayMove } from "@dnd-kit/sortable";

import type { OrderingItem } from "@/features/flashcard/api/flashcards";

/** id 배열의 순서가 같은지. */
export function sameOrder(a: OrderingItem[], b: OrderingItem[]): boolean {
  if (a.length !== b.length) return false;
  return a.every((item, i) => item.id === b[i].id);
}

/** Fisher–Yates 셔플(비파괴). rng는 [0,1) 난수. */
export function fisherYates<T>(items: T[], rng: () => number): T[] {
  const a = [...items];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

/**
 * 복습 덱(카드 목록) 셔플. 세션 진입 시 큐를 만들 때 1회만 호출한다(렌더마다 재셔플 금지).
 * 순서 암기를 막으려는 것이라 원본과 같은 배열이 나와도(작은 덱에서 가끔) 강제로 바꾸지 않는다 —
 * ORDERING 카드의 정답 셔플({@link shuffleForReview})과 목적이 다르다.
 */
export function shuffleDeck<T>(
  items: T[],
  rng: () => number = Math.random,
): T[] {
  return fisherYates(items, rng);
}

/**
 * 복습 시작용 셔플. 마운트 시 1회만 호출한다(렌더마다 재셔플 금지).
 * 정답 순서와 같으면 재셔플하며, 그래도 같으면(난수 편향 등) 앞 두 항목을 교환해 반드시 다르게 만든다.
 * 항목이 2개 미만이면 그대로 둔다(실제로는 최소 3개).
 */
export function shuffleForReview(
  items: OrderingItem[],
  rng: () => number = Math.random,
): OrderingItem[] {
  if (items.length < 2) return [...items];

  let shuffled = fisherYates(items, rng);
  for (let attempt = 0; attempt < 10 && sameOrder(shuffled, items); attempt++) {
    shuffled = fisherYates(items, rng);
  }
  if (sameOrder(shuffled, items)) {
    [shuffled[0], shuffled[1]] = [shuffled[1], shuffled[0]];
  }
  return shuffled;
}

/** 드래그 종료 시 재정렬(비파괴). 대상이 없거나 같으면 원본 유지. */
export function reorder(
  order: OrderingItem[],
  activeId: string,
  overId: string,
): OrderingItem[] {
  if (activeId === overId) return order;
  const from = order.findIndex((i) => i.id === activeId);
  const to = order.findIndex((i) => i.id === overId);
  if (from === -1 || to === -1) return order;
  return arrayMove(order, from, to);
}
