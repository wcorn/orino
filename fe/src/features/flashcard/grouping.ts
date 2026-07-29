import type { Flashcard } from "./api/flashcards";

/** 목록의 한 행. 양방향 짝 2장은 `pair` 한 행으로 합쳐 리스트 길이를 절반으로 줄인다. */
export type FlashcardRow =
  | { kind: "single"; key: string; card: Flashcard }
  | { kind: "pair"; key: string; cards: [Flashcard, Flashcard] };

/**
 * 로드된 카드들을 표시용 행으로 묶는다. `siblingGroupId`가 같은 2장은 한 행이 된다.
 *
 * 페이지 경계에 짝이 걸쳐 한 장만 로드된 동안은 단독 행으로 보이고,
 * 다음 페이지가 로드되면 자연히 합쳐진다(무한 스크롤이라 최종 상태는 항상 묶인 형태).
 * 짝 행의 위치는 먼저 등장한 카드의 자리를 따른다.
 */
export function groupFlashcards(cards: Flashcard[]): FlashcardRow[] {
  const byGroup = new Map<number, Flashcard[]>();
  for (const card of cards) {
    if (card.siblingGroupId == null) continue;
    const bucket = byGroup.get(card.siblingGroupId);
    if (bucket) {
      bucket.push(card);
    } else {
      byGroup.set(card.siblingGroupId, [card]);
    }
  }

  const consumed = new Set<number>();
  const rows: FlashcardRow[] = [];
  for (const card of cards) {
    const group =
      card.siblingGroupId == null
        ? undefined
        : byGroup.get(card.siblingGroupId);

    // 짝이 둘 다 로드된 경우에만 합친다. 3장 이상은 데이터상 있을 수 없지만,
    // 생기더라도 앞의 2장만 짝으로 묶고 나머지는 단독 행으로 흘린다.
    if (group && group.length >= 2 && group.slice(0, 2).includes(card)) {
      if (consumed.has(card.siblingGroupId!)) continue;
      consumed.add(card.siblingGroupId!);
      rows.push({
        kind: "pair",
        key: `pair-${card.siblingGroupId}`,
        cards: [group[0], group[1]],
      });
      continue;
    }
    rows.push({ kind: "single", key: `card-${card.id}`, card });
  }
  return rows;
}
