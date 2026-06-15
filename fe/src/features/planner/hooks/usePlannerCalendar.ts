import { useQuery } from "@tanstack/react-query";

import { fetchPlannerCalendar } from "../api/feed";
import { plannerKeys } from "../queryKeys";

export function usePlannerCalendar(from: string, to: string) {
  return useQuery({
    queryKey: plannerKeys.calendar(from, to),
    queryFn: () => fetchPlannerCalendar(from, to),
    staleTime: 60 * 1000,
  });
}
