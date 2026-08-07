import { useQuery } from "@tanstack/react-query";

import { fetchBoard } from "../api/activities";
import { travelKeys } from "../queryKeys";

/**
 * 보드 단일 조회. 날짜 탭·보관함 건수·선택 날짜의 일정이 한 응답에 담겨 온다 —
 * 오프라인 캐시(4단계)가 응답 하나로 성립해야 하기 때문이다.
 *
 * @param params `date`도 `archive`도 없으면 서버가 고른다(진행 중이면 현지 오늘, 아니면 1일차)
 */
export function useBoard(
  tripId: number,
  params: { date?: string; archive?: boolean },
  options: { enabled?: boolean } = {},
) {
  return useQuery({
    queryKey: travelKeys.board(
      tripId,
      params.archive ? "archive" : params.date,
    ),
    queryFn: () => fetchBoard(tripId, params),
    staleTime: 10 * 1000,
    enabled: options.enabled ?? true,
  });
}
