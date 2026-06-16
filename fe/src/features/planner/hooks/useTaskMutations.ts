import { useMutation, useQueryClient } from "@tanstack/react-query";

import { toast } from "@/shared/lib/toast";

import {
  createTask,
  deleteTask,
  type TaskUpdateRequest,
  updateTask,
} from "../api/tasks";
import { plannerKeys } from "../queryKeys";

export function useCreateTask() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createTask,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: plannerKeys.all });
      toast("할 일을 추가했습니다.", "success");
    },
    onError: () => toast("할 일 추가에 실패했습니다.", "error"),
  });
}

/** 완료 토글/수정. 토글이 잦아 성공 토스트는 생략하고 invalidate로만 갱신한다. */
export function useUpdateTask() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      taskId,
      request,
    }: {
      taskId: string;
      request: TaskUpdateRequest;
    }) => updateTask(taskId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: plannerKeys.all });
    },
    onError: () => toast("할 일 변경에 실패했습니다.", "error"),
  });
}

export function useDeleteTask() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteTask,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: plannerKeys.all });
      toast("할 일을 삭제했습니다.", "success");
    },
    onError: () => toast("할 일 삭제에 실패했습니다.", "error"),
  });
}
