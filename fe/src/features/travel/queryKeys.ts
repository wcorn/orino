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
};
