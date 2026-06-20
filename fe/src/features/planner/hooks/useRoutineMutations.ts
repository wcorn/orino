import { useMutation, useQueryClient } from "@tanstack/react-query";

import { toast } from "@/shared/lib/toast";

import {
  createRoutine,
  deleteRoutine,
  type RoutineEditRequest,
  type RoutineScope,
  updateRoutine,
} from "../api/routines";
import { plannerKeys, routineKeys } from "../queryKeys";

/** 루틴 시리즈 목록과 통합 피드를 함께 무효화한다(서버가 캐시를 무효화하므로 재조회로 최신화). */
function invalidateRoutines(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: routineKeys.all });
  queryClient.invalidateQueries({ queryKey: plannerKeys.all });
}

export function useCreateRoutine() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createRoutine,
    onSuccess: () => {
      invalidateRoutines(queryClient);
      toast("루틴을 저장했습니다.", "success");
    },
    onError: () => toast("루틴 저장에 실패했습니다.", "error"),
  });
}

export function useUpdateRoutine() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      eventId,
      request,
      scope,
      instanceDate,
    }: {
      eventId: string;
      request: RoutineEditRequest;
      scope: RoutineScope;
      instanceDate?: string;
    }) => updateRoutine(eventId, request, { scope, instanceDate }),
    onSuccess: () => {
      invalidateRoutines(queryClient);
      toast("루틴을 수정했습니다.", "success");
    },
    onError: () => toast("루틴 수정에 실패했습니다.", "error"),
  });
}

export function useDeleteRoutine() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      eventId,
      scope,
      instanceDate,
    }: {
      eventId: string;
      scope: RoutineScope;
      instanceDate?: string;
    }) => deleteRoutine(eventId, { scope, instanceDate }),
    onSuccess: () => {
      invalidateRoutines(queryClient);
      toast("루틴을 삭제했습니다.", "success");
    },
    onError: () => toast("루틴 삭제에 실패했습니다.", "error"),
  });
}
