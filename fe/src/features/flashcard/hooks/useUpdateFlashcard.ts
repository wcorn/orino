import { useMutation, useQueryClient } from "@tanstack/react-query";

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
    },
  });
}
