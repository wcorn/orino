import { useQuery } from "@tanstack/react-query";

import { searchPlaces } from "../api/places";
import { travelKeys } from "../queryKeys";

/**
 * 장소 검색.
 *
 * <p>호출 하나가 곧 비용이라(Places는 호출당 과금) 타이핑마다 부르지 않는다.
 * 검색은 사용자가 <b>제출</b>했을 때만 일어난다 — `q`가 비면 아무것도 부르지 않는다.
 */
export function usePlaceSearch(q: string, tripId?: number) {
  return useQuery({
    queryKey: travelKeys.placeSearch(q, tripId),
    queryFn: () => searchPlaces(q, tripId),
    enabled: q.trim().length > 0,
    // 서버가 1시간 캐시하지만, 뒤로 갔다 오는 동안 다시 부를 이유는 없다.
    staleTime: 5 * 60 * 1000,
  });
}
