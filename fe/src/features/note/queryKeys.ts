export const noteKeys = {
  all: ["notes"] as const,
  /** 자료 종속 트리는 materialId, 독립 트리는 "standalone"으로 구분. */
  tree: (materialId?: number | null) =>
    ["notes", "tree", materialId ?? "standalone"] as const,
  detail: (noteId: number) => ["notes", "detail", noteId] as const,
};
