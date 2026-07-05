import { useMutation, useQueryClient } from "@tanstack/react-query";

import {
  deleteMonthlyGoal,
  type MonthlyGoal,
  saveMonthlyGoal,
} from "../api/monthlyGoal";
import { monthlyGoalKeys } from "../queryKeys";

/** 해당 년월 목표 저장(PUT)·삭제. 성공 시 그 년월 캐시를 무효화한다. */
export function useMonthlyGoalMutations(year: number, month: number) {
  const queryClient = useQueryClient();

  const invalidate = () =>
    queryClient.invalidateQueries({
      queryKey: monthlyGoalKeys.ym(year, month),
    });

  const save = useMutation<MonthlyGoal, Error, string>({
    mutationFn: (content) => saveMonthlyGoal(year, month, content),
    onSuccess: invalidate,
  });

  const remove = useMutation<void, Error, void>({
    mutationFn: () => deleteMonthlyGoal(year, month),
    onSuccess: invalidate,
  });

  return { save, remove };
}
