import { useQuery } from "@tanstack/react-query";

import { searchPlaces } from "../api/places";
import { travelKeys } from "../queryKeys";

/**
 * 장소 검색.
 *
 * <p>호출 하나가 곧 비용이라(Places는 호출당 과금) 타이핑마다 부르지 않는다.
 * 검색은 사용자가 <b>제출</b>했을 때만 일어난다 — `q`가 비면 아무것도 부르지 않는다.
 *
 * <p>`ready`는 <b>기준 도시가 정해졌는가</b>다. 도시를 모르는 채로 먼저 한 번 부르면 편향
 * 없는 결과가 잠깐 보였다가 도시가 정해지며 다시 부르게 된다 — 틀린 결과를 보여주고
 * 호출도 두 배로 낸다.
 */
export function usePlaceSearch(
  q: string,
  tripId?: number,
  cityPlaceId?: number | null,
  ready = true,
) {
  return useQuery({
    // 도시가 키에 들어간다 — 같은 검색어라도 도시가 다르면 다른 결과다.
    queryKey: travelKeys.placeSearch(q, tripId, cityPlaceId),
    queryFn: () => searchPlaces(q, tripId, cityPlaceId),
    enabled: ready && q.trim().length > 0,
    // 서버가 1시간 캐시하지만, 뒤로 갔다 오는 동안 다시 부를 이유는 없다.
    staleTime: 5 * 60 * 1000,
  });
}
