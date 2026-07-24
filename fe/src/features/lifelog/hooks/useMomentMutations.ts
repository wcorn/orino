import { useMutation, useQueryClient } from "@tanstack/react-query";

import { createMoment, deleteMoment, updateMoment } from "../api/moments";
import type { MomentCard, MomentWriteRequest } from "../api/types";
import { lifelogKeys } from "../queryKeys";

/** 피드 전체를 무효화한다(필터별 캐시 포함). */
function invalidateFeed(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: lifelogKeys.all });
}

export function useCreateMoment() {
  const queryClient = useQueryClient();
  return useMutation<MomentCard, Error, MomentWriteRequest>({
    mutationFn: createMoment,
    onSuccess: () => invalidateFeed(queryClient),
  });
}

export function useUpdateMoment() {
  const queryClient = useQueryClient();
  return useMutation<
    MomentCard,
    Error,
    { id: number; request: MomentWriteRequest }
  >({
    mutationFn: ({ id, request }) => updateMoment(id, request),
    onSuccess: () => invalidateFeed(queryClient),
  });
}

export function useDeleteMoment() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, number>({
    mutationFn: deleteMoment,
    onSuccess: () => invalidateFeed(queryClient),
  });
}
