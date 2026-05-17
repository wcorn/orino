import { useQuery } from "@tanstack/react-query";

import { fetchFlashcards } from "../api/flashcards";
import { flashcardKeys } from "../queryKeys";

export function useFlashcards(materialId: number) {
  return useQuery({
    queryKey: flashcardKeys.byMaterial(materialId),
    queryFn: () => fetchFlashcards(materialId),
  });
}
