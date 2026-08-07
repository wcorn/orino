import { useQuery } from "@tanstack/react-query";

import { fetchTrip } from "../api/travel";
import { travelKeys } from "../queryKeys";

/** 여행 상세. 요약에 없는 타임존·통화·기간이 필요할 때 쓴다. */
export function useTrip(tripId: number | null) {
  return useQuery({
    queryKey: travelKeys.trip(tripId ?? 0),
    queryFn: () => fetchTrip(tripId as number),
    enabled: tripId !== null,
    staleTime: 30 * 1000,
  });
}
