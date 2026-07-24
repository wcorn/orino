import type { FeedFilters } from "./api/types";

export const lifelogKeys = {
  all: ["lifelog"] as const,
  feed: (filters: FeedFilters) => ["lifelog", "feed", filters] as const,
  moment: (id: number) => ["lifelog", "moment", id] as const,
  tags: (query: string) => ["lifelog", "tags", query] as const,
};
