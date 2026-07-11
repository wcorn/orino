import { useInfiniteQuery } from "@tanstack/react-query";

import {
  type CompletedReviewParams,
  fetchCompletedReviews,
} from "../api/reviewHub";
import { reviewKeys } from "../queryKeys";

const PAGE_SIZE = 20;

type Filters = Omit<CompletedReviewParams, "cursor" | "size">;

/** 완료된 복습 — 커서 기반 무한 스크롤. 최근 복습 순. */
export function useCompletedReviews(filters: Filters, enabled = true) {
  return useInfiniteQuery({
    queryKey: reviewKeys.completed(filters),
    queryFn: ({ pageParam }) =>
      fetchCompletedReviews({ ...filters, cursor: pageParam, size: PAGE_SIZE }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) =>
      lastPage.hasNext ? lastPage.nextCursor : undefined,
    staleTime: 60 * 1000,
    enabled,
  });
}
