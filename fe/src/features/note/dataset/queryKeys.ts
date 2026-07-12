export const datasetKeys = {
  all: ["datasets"] as const,
  meta: (id: number) => ["datasets", id, "meta"] as const,
  rows: (id: number, offset: number, limit: number) =>
    ["datasets", id, "rows", offset, limit] as const,
};
