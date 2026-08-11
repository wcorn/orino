import { useQuery } from "@tanstack/react-query";

import { fetchCityLegs } from "../api/travel";
import { travelKeys } from "../queryKeys";

/**
 * 파생 구간(§2.4). 연속으로 같은 기준 도시인 날짜의 묶음이다.
 *
 * <p><b>저장된 값이 아니라 날짜에서 매번 계산된다</b> — 하루의 기준 도시를 바꾸면 다음
 * 조회에서 곧바로 반영된다. 그래서 앞뒤 구간의 번호까지 함께 달라질 수 있다.
 *
 * <p>지도의 `전체` 모드에서만 필요하다. `이 날짜`를 보는 동안 부르면 아무도 보지 않을 값을
 * 받아 오는 것이라 `enabled`로 막는다.
 */
export function useCityLegs(
  tripId: number,
  options: { enabled?: boolean } = {},
) {
  return useQuery({
    queryKey: travelKeys.cityLegs(tripId),
    queryFn: () => fetchCityLegs(tripId),
    staleTime: 10_000,
    enabled: options.enabled ?? true,
  });
}
