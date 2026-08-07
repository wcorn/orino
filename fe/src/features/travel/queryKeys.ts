import type { TripStatus } from "./lib/tripStatus";

export const travelKeys = {
  all: ["travel"] as const,
  summary: ["travel", "summary"] as const,
  trips: (status?: TripStatus) => ["travel", "trips", status ?? "all"] as const,
  trip: (tripId: number) => ["travel", "trip", tripId] as const,
};
