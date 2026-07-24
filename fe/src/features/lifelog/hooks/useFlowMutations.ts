import { useMutation, useQueryClient } from "@tanstack/react-query";

import {
  addMomentsToFlow,
  createFlow,
  deleteFlow,
  type FlowCreateRequest,
  type FlowSummary,
  type FlowUpdateRequest,
  removeMomentFromFlow,
  reorderFlowMoments,
  updateFlow,
} from "../api/flows";
import { lifelogKeys } from "../queryKeys";

type QueryClient = ReturnType<typeof useQueryClient>;

function invalidateFlows(queryClient: QueryClient) {
  queryClient.invalidateQueries({ queryKey: lifelogKeys.all });
}

export function useCreateFlow() {
  const queryClient = useQueryClient();
  return useMutation<FlowSummary, Error, FlowCreateRequest>({
    mutationFn: createFlow,
    onSuccess: () => invalidateFlows(queryClient),
  });
}

export function useUpdateFlow() {
  const queryClient = useQueryClient();
  return useMutation<
    FlowSummary,
    Error,
    { id: number; request: FlowUpdateRequest }
  >({
    mutationFn: ({ id, request }) => updateFlow(id, request),
    onSuccess: () => invalidateFlows(queryClient),
  });
}

export function useDeleteFlow() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, number>({
    mutationFn: deleteFlow,
    onSuccess: () => invalidateFlows(queryClient),
  });
}

export function useAddMoments(flowId: number) {
  const queryClient = useQueryClient();
  return useMutation<unknown, Error, number[]>({
    mutationFn: (momentIds) => addMomentsToFlow(flowId, momentIds),
    onSuccess: () => invalidateFlows(queryClient),
  });
}

export function useRemoveMoment(flowId: number) {
  const queryClient = useQueryClient();
  return useMutation<void, Error, number>({
    mutationFn: (momentId) => removeMomentFromFlow(flowId, momentId),
    onSuccess: () => invalidateFlows(queryClient),
  });
}

export function useReorderMoments(flowId: number) {
  const queryClient = useQueryClient();
  return useMutation<unknown, Error, number[]>({
    mutationFn: (momentIds) => reorderFlowMoments(flowId, momentIds),
    onSuccess: () => invalidateFlows(queryClient),
  });
}
