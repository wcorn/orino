import { useQuery } from "@tanstack/react-query";

import { fetchTravelSummary } from "../api/travel";
import { travelKeys } from "../queryKeys";

interface Options {
  /**
   * 기본 true. 일상 워크스페이스에서는 false로 꺼서 여행 API를 부르지 않는다 —
   * 사이드바처럼 두 워크스페이스가 공유하는 컴포넌트는 훅을 조건부로 호출할 수 없기 때문이다.
   */
  enabled?: boolean;
}

/**
 * 여행 요약. `/select` 카드와 여행 홈·사이드바가 같은 캐시를 공유한다 — 선택 화면에서 이미
 * 받아둔 값으로 이후 화면이 즉시 그려진다.
 */
export function useTravelSummary({ enabled = true }: Options = {}) {
  return useQuery({
    queryKey: travelKeys.summary,
    queryFn: fetchTravelSummary,
    staleTime: 60 * 1000,
    enabled,
  });
}
