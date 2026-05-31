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
      // 복습 완료는 상태 변경 + 다음 일정 생성이라 today·캘린더 모두 갱신
      queryClient.invalidateQueries({ queryKey: reviewKeys.all });
      queryClient.invalidateQueries({ queryKey: materialKeys.all });
    },
  });
}
