import { useMutation, useQueryClient } from "@tanstack/react-query";

import { toast } from "@/shared/lib/toast";

import { createRoutine } from "../api/routines";
import { plannerKeys, routineKeys } from "../queryKeys";

/**
 * 루틴 생성 후 시리즈 목록과 통합 피드를 invalidate해 갱신한다(서버가 캐시를 무효화하므로 재조회로 최신화).
 */
export function useCreateRoutine() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createRoutine,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: routineKeys.all });
      queryClient.invalidateQueries({ queryKey: plannerKeys.all });
      toast("루틴을 저장했습니다.", "success");
    },
    onError: () => toast("루틴 저장에 실패했습니다.", "error"),
  });
}
