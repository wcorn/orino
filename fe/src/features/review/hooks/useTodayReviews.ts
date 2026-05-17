import { useQuery } from "@tanstack/react-query";

import { fetchTodayReviews } from "../api/reviews";
import { reviewKeys } from "../queryKeys";

export function useTodayReviews() {
  return useQuery({
    queryKey: reviewKeys.today,
    queryFn: fetchTodayReviews,
    staleTime: 60 * 1000,
  });
}
