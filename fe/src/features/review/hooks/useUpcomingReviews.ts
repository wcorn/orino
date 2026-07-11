import { useInfiniteQuery } from "@tanstack/react-query";

import {
  fetchUpcomingReviews,
  type UpcomingReviewParams,
} from "../api/reviewHub";
import { reviewKeys } from "../queryKeys";

const PAGE_SIZE = 20;

type Filters = Omit<UpcomingReviewParams, "cursor" | "size">;

/** 앞으로의 복습 — 커서 기반 무한 스크롤. 필터 변경 시 첫 페이지부터 다시 로드. */
export function useUpcomingReviews(filters: Filters, enabled = true) {
  return useInfiniteQuery({
    queryKey: reviewKeys.upcoming(filters),
    queryFn: ({ pageParam }) =>
      fetchUpcomingReviews({ ...filters, cursor: pageParam, size: PAGE_SIZE }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) =>
      lastPage.hasNext ? lastPage.nextCursor : undefined,
    staleTime: 60 * 1000,
    enabled,
  });
}
