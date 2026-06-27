import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { toast } from "@/shared/lib/toast";

import {
  fetchWeeklyPlan,
  saveWeeklyPlan,
  type WeeklyPlanBlockInput,
} from "../api/weeklyPlan";
import { plannerKeys } from "../queryKeys";

/** 주간 템플릿 조회. */
export function useWeeklyPlan() {
  return useQuery({
    queryKey: plannerKeys.weeklyPlan(),
    queryFn: fetchWeeklyPlan,
  });
}

/** 주간 템플릿 전량 교체 저장. 성공 시 캐시를 갱신하고 토스트를 띄운다. */
export function useSaveWeeklyPlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (blocks: WeeklyPlanBlockInput[]) => saveWeeklyPlan(blocks),
    onSuccess: (blocks) => {
      queryClient.setQueryData(plannerKeys.weeklyPlan(), blocks);
      toast("주간 계획표를 저장했습니다.", "success");
    },
    onError: () => {
      toast("저장에 실패했습니다. 다시 시도해 주세요.", "error");
    },
  });
}
