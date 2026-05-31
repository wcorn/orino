import { useMutation, useQueryClient } from "@tanstack/react-query";

import { reviewKeys } from "@/features/review/queryKeys";

import {
  type Flashcard,
  type FlashcardUpdateRequest,
  updateFlashcard,
} from "../api/flashcards";
import { flashcardKeys } from "../queryKeys";

export function useUpdateFlashcard(materialId: number, flashcardId: number) {
  const queryClient = useQueryClient();
  return useMutation<Flashcard, Error, FlashcardUpdateRequest>({
    mutationFn: (request) => updateFlashcard(flashcardId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: flashcardKeys.byMaterial(materialId),
      });
      // 카드 앞면 텍스트는 today·캘린더에도 노출되므로 함께 갱신
      queryClient.invalidateQueries({ queryKey: reviewKeys.all });
    },
  });
}
