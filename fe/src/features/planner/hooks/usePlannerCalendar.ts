import { useQuery } from "@tanstack/react-query";

import { fetchPlannerCalendar } from "../api/feed";
import { plannerKeys } from "../queryKeys";

// 복귀(재포커스) 재검증 시 연사를 막는 dedupe 창. 이 시간 내 재포커스는 refetch를 건너뛴다.
const REVALIDATE_DEDUPE_MS = 8 * 1000;

export function usePlannerCalendar(from: string, to: string) {
  return useQuery({
    queryKey: plannerKeys.calendar(from, to),
    queryFn: () => fetchPlannerCalendar(from, to),
    // 복귀 시 보이는 캘린더만 SWR 재검증한다(옛 값 유지 후 조용히 교체). staleTime이 곧 연사 dedupe 창.
    staleTime: REVALIDATE_DEDUPE_MS,
    refetchOnWindowFocus: true,
  });
}
