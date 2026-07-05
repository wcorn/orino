import { useQuery } from "@tanstack/react-query";

import { fetchMonthlyGoal } from "../api/monthlyGoal";
import { monthlyGoalKeys } from "../queryKeys";

/** 해당 년월 목표 조회(없으면 null). */
export function useMonthlyGoal(year: number, month: number) {
  return useQuery({
    queryKey: monthlyGoalKeys.ym(year, month),
    queryFn: () => fetchMonthlyGoal(year, month),
  });
}
