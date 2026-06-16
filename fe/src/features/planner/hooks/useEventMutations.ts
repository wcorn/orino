import { useMutation, useQueryClient } from "@tanstack/react-query";

import { toast } from "@/shared/lib/toast";

import {
  createEvent,
  deleteEvent,
  type EventWriteRequest,
  updateEvent,
} from "../api/events";
import { plannerKeys } from "../queryKeys";

/**
 * 일정 쓰기 후 통합 피드를 invalidate해 갱신한다(서버가 캐시를 무효화하므로 재조회로 최신화).
 */
export function useCreateEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createEvent,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: plannerKeys.all });
      toast("일정을 저장했습니다.", "success");
    },
    onError: () => toast("일정 저장에 실패했습니다.", "error"),
  });
}

export function useUpdateEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      eventId,
      request,
    }: {
      eventId: string;
      request: EventWriteRequest;
    }) => updateEvent(eventId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: plannerKeys.all });
      toast("일정을 수정했습니다.", "success");
    },
    onError: () => toast("일정 수정에 실패했습니다.", "error"),
  });
}

export function useDeleteEvent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteEvent,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: plannerKeys.all });
      toast("일정을 삭제했습니다.", "success");
    },
    onError: () => toast("일정 삭제에 실패했습니다.", "error"),
  });
}
