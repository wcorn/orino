export const flashcardKeys = {
  all: ["flashcards"] as const,
  byMaterial: (materialId: number) =>
    ["flashcards", "material", materialId] as const,
};
