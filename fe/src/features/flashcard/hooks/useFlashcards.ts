import { useInfiniteQuery } from "@tanstack/react-query";

import { fetchFlashcards, type FlashcardListFilters } from "../api/flashcards";
import { flashcardKeys } from "../queryKeys";

const PAGE_SIZE = 30;

/**
 * 자료의 카드 목록 — 커서 기반 무한 스크롤.
 * 검색·필터·정렬은 서버가 적용한다(로드된 페이지만 재필터링하면 결과가 어긋나므로 FE에서 다시 거르지 않는다).
 */
export function useFlashcards(
  materialId: number,
  filters: FlashcardListFilters = {},
) {
  return useInfiniteQuery({
    queryKey: flashcardKeys.list(materialId, filters),
    queryFn: ({ pageParam }) =>
      fetchFlashcards(materialId, {
        ...filters,
        cursor: pageParam,
        size: PAGE_SIZE,
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) =>
      lastPage.hasNext ? lastPage.nextCursor : undefined,
  });
}
