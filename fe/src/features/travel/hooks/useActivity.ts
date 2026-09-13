import { useQuery } from "@tanstack/react-query";

import { fetchActivity } from "../api/activities";
import { travelKeys } from "../queryKeys";

/**
 * 일정 상세. 보드 응답과 같은 형태라 편집 폼이 그대로 채워진다.
 *
 * <p>`enabled`는 일정이 있을 수도 없을 수도 있는 화면용이다 — 장소 검색은 교체 모드일 때만
 * 일정을 읽는다.
 */
export function useActivity(
  activityId: number,
  { enabled = true }: { enabled?: boolean } = {},
) {
  return useQuery({
    queryKey: travelKeys.activity(activityId),
    queryFn: () => fetchActivity(activityId),
    staleTime: 10 * 1000,
    enabled,
  });
}
