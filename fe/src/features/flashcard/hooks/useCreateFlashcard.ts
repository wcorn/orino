import { useMutation, useQueryClient } from "@tanstack/react-query";

import { materialKeys } from "@/features/material/queryKeys";
import { reviewKeys } from "@/features/review/queryKeys";

import {
  createFlashcard,
  type FlashcardCreateRequest,
  type FlashcardCreateResponse,
} from "../api/flashcards";
import { flashcardKeys } from "../queryKeys";

export function useCreateFlashcard(materialId: number) {
  const queryClient = useQueryClient();
  return useMutation<FlashcardCreateResponse, Error, FlashcardCreateRequest>({
    mutationFn: (request) => createFlashcard(materialId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: flashcardKeys.byMaterial(materialId),
      });
      queryClient.invalidateQueries({ queryKey: materialKeys.all });
      // 카드 생성 시 복습 일정이 함께 생성되므로 today뿐 아니라 캘린더도 갱신
      queryClient.invalidateQueries({ queryKey: reviewKeys.all });
    },
  });
}
