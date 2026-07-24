import type { FeedFilters } from "./api/types";

export const lifelogKeys = {
  all: ["lifelog"] as const,
  feed: (filters: FeedFilters) => ["lifelog", "feed", filters] as const,
  moment: (id: number) => ["lifelog", "moment", id] as const,
  tags: (query: string) => ["lifelog", "tags", query] as const,
  geocodeReverse: (lat: number, lng: number) =>
    ["lifelog", "geocode", "reverse", lat, lng] as const,
  geocodeSearch: (query: string) =>
    ["lifelog", "geocode", "search", query] as const,
  flows: (status?: string) => ["lifelog", "flows", status ?? "all"] as const,
  flow: (id: number) => ["lifelog", "flow", id] as const,
};
