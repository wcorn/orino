import { useQuery } from "@tanstack/react-query";

import { fetchTodayReviews, type TodayReviewsResponse } from "../api/reviews";

export const todayReviewsQueryKey = ["planner", "reviews", "today"] as const;

export function useTodayReviews(enabled = true) {
  return useQuery<TodayReviewsResponse>({
    queryKey: todayReviewsQueryKey,
    queryFn: fetchTodayReviews,
    enabled,
    staleTime: 30_000,
  });
}
