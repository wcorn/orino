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

/**
 * 주간 템플릿 전량 교체 저장(자동 저장). 성공 시 캐시를 서버 응답으로 갱신한다.
 * 변경마다 호출되므로 성공 토스트는 띄우지 않고(소음 방지), 실패만 토스트로 알린다.
 */
export function useSaveWeeklyPlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (blocks: WeeklyPlanBlockInput[]) => saveWeeklyPlan(blocks),
    onSuccess: (blocks) => {
      queryClient.setQueryData(plannerKeys.weeklyPlan(), blocks);
    },
    onError: () => {
      toast("저장에 실패했습니다. 다시 시도해 주세요.", "error");
    },
  });
}
