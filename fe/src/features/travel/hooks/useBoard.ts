import { keepPreviousData, useQuery } from "@tanstack/react-query";

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
    // 날짜를 옮기면 쿼리 키가 바뀐다. 그때 data가 undefined로 떨어지면 보드가 통째로
    // 언마운트되어 <b>페이지를 새로고침한 것처럼</b> 보인다 — 방금 누른 날짜 칸까지 사라진다.
    // 이전 응답을 붙들고 있다가 새 응답으로 갈아끼운다(그 사이 무엇을 감출지는 화면이 정한다).
    placeholderData: keepPreviousData,
    staleTime: 10 * 1000,
    enabled: options.enabled ?? true,
  });
}
