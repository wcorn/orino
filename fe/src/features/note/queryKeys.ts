export const noteKeys = {
  all: ["notes"] as const,
  byMaterial: (materialId: number) =>
    ["notes", "material", materialId] as const,
};
