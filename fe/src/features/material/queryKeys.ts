export const materialKeys = {
  all: ["materials"] as const,
  list: (status?: string) =>
    status
      ? (["materials", "list", status] as const)
      : (["materials", "list"] as const),
  detail: (id: number) => ["materials", "detail", id] as const,
};
