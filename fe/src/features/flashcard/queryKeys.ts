import type { FlashcardListFilters } from "./api/flashcards";

export const flashcardKeys = {
  all: ["flashcards"] as const,
  byMaterial: (materialId: number) =>
    ["flashcards", "material", materialId] as const,
  /** 필터별 목록. mutation 무효화는 {@link byMaterial} 프리픽스 하나로 전부 걸린다. */
  list: (materialId: number, filters: FlashcardListFilters) =>
    ["flashcards", "material", materialId, filters] as const,
};
