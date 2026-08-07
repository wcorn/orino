import { useQuery } from "@tanstack/react-query";

import { fetchTrips } from "../api/travel";
import type { TripStatus } from "../lib/tripStatus";
import { travelKeys } from "../queryKeys";

/**
 * 여행 목록. `status`를 주면 서버가 걸러 주고, 탭 건수(`counts`)는 어느 탭을 보든
 * 항상 전체 기준으로 온다.
 */
export function useTrips(status?: TripStatus) {
  return useQuery({
    queryKey: travelKeys.trips(status),
    queryFn: () => fetchTrips(status),
    staleTime: 30 * 1000,
  });
}
