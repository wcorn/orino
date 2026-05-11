import { useMutation, useQueryClient } from "@tanstack/react-query";

import { completeReview, type Rating } from "../api/reviews";
import { todayReviewsQueryKey } from "./useTodayReviews";

export function useCompleteReview() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ reviewId, rating }: { reviewId: number; rating: Rating }) =>
      completeReview(reviewId, rating),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: todayReviewsQueryKey });
      queryClient.invalidateQueries({ queryKey: ["planner", "materials"] });
      queryClient.invalidateQueries({ queryKey: ["planner", "material"] });
    },
  });
}
