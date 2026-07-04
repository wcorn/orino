export const memoKeys = {
  all: ["memos"] as const,
  tree: () => ["memos", "tree"] as const,
  detail: (memoId: number) => ["memos", "detail", memoId] as const,
};
