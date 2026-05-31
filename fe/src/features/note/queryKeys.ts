export const noteKeys = {
  all: ["notes"] as const,
  tree: (materialId: number) => ["notes", "tree", materialId] as const,
  detail: (noteId: number) => ["notes", "detail", noteId] as const,
};
