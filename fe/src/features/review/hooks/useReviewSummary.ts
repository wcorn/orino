import { useQuery } from "@tanstack/react-query";

import { fetchReviewSummary } from "../api/reviewHub";
import { reviewKeys } from "../queryKeys";

export function useReviewSummary() {
  return useQuery({
    queryKey: reviewKeys.summary,
    queryFn: fetchReviewSummary,
    staleTime: 60 * 1000,
  });
}
