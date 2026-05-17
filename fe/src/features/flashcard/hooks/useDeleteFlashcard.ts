import { useMutation, useQueryClient } from "@tanstack/react-query";

import { materialKeys } from "@/features/material/queryKeys";
import { reviewKeys } from "@/features/review/queryKeys";

import { deleteFlashcard } from "../api/flashcards";
import { flashcardKeys } from "../queryKeys";

export function useDeleteFlashcard(materialId: number) {
  const queryClient = useQueryClient();
  return useMutation<void, Error, number>({
    mutationFn: deleteFlashcard,
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: flashcardKeys.byMaterial(materialId),
      });
      queryClient.invalidateQueries({ queryKey: materialKeys.all });
      queryClient.invalidateQueries({ queryKey: reviewKeys.today });
    },
  });
}
