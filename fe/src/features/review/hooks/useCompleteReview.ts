import { useMutation, useQueryClient } from "@tanstack/react-query";

import { materialKeys } from "@/features/material/queryKeys";

import {
  completeReview,
  type CompleteReviewResponse,
  type Rating,
} from "../api/reviews";
import { reviewKeys } from "../queryKeys";

interface Variables {
  reviewId: number;
  rating: Rating;
}

export function useCompleteReview() {
  const queryClient = useQueryClient();
  return useMutation<CompleteReviewResponse, Error, Variables>({
    mutationFn: ({ reviewId, rating }) => completeReview(reviewId, rating),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: reviewKeys.today });
      queryClient.invalidateQueries({ queryKey: materialKeys.all });
    },
  });
}
