import { useQuery } from "@tanstack/react-query";

import { listRoutines } from "../api/routines";
import { routineKeys } from "../queryKeys";

/** 루틴 시리즈 목록 조회. 미연동(409)이면 에러로 떨어지므로 호출부가 연동 상태로 분기한다. */
export function useRoutineList(enabled = true) {
  return useQuery({
    queryKey: routineKeys.list(),
    queryFn: listRoutines,
    enabled,
    staleTime: 30 * 1000,
    retry: false,
  });
}
