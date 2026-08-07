import { useQuery } from "@tanstack/react-query";

import { fetchActivity } from "../api/activities";
import { travelKeys } from "../queryKeys";

/** 일정 상세. 보드 응답과 같은 형태라 편집 폼이 그대로 채워진다. */
export function useActivity(activityId: number) {
  return useQuery({
    queryKey: travelKeys.activity(activityId),
    queryFn: () => fetchActivity(activityId),
    staleTime: 10 * 1000,
  });
}
