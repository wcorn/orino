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
      // 카드 삭제 시 복습 일정도 사라지므로 today뿐 아니라 캘린더도 갱신
      queryClient.invalidateQueries({ queryKey: reviewKeys.all });
    },
  });
}
