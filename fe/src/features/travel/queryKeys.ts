import type { TripStatus } from "./lib/tripStatus";

export const travelKeys = {
  all: ["travel"] as const,
  summary: ["travel", "summary"] as const,
  trips: (status?: TripStatus) => ["travel", "trips", status ?? "all"] as const,
  trip: (tripId: number) => ["travel", "trip", tripId] as const,
  /** 보드는 보는 날짜마다 따로 캐시한다. `day`는 날짜 문자열 또는 "archive". */
  board: (tripId: number, day: string | undefined) =>
    ["travel", "board", tripId, day ?? "default"] as const,
  boards: (tripId: number) => ["travel", "board", tripId] as const,
  activity: (activityId: number) => ["travel", "activity", activityId] as const,
  /** 장소 검색. 서버가 이미 캐시하지만, 뒤로 갔다 오면 결과가 그대로 있어야 한다. */
  placeSearch: (q: string, tripId?: number) =>
    ["travel", "places", "search", tripId ?? "none", q] as const,
  citySearch: (q: string) => ["travel", "places", "cities", q] as const,
  place: (placeId: number) => ["travel", "place", placeId] as const,
  weather: (tripId: number) => ["travel", "weather", tripId] as const,
  fx: (base: string, quote: string) => ["travel", "fx", base, quote] as const,
};
