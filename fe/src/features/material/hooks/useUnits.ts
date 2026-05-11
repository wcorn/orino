import { useMutation, useQueryClient } from "@tanstack/react-query";

import {
  completeUnit,
  createUnits,
  deleteUnit,
  type UnitItemInput,
  updateUnit,
  type UpdateUnitRequest,
} from "../api/units";

function invalidateMaterial(
  queryClient: ReturnType<typeof useQueryClient>,
  materialId: number,
) {
  queryClient.invalidateQueries({
    queryKey: ["planner", "material", materialId],
  });
  queryClient.invalidateQueries({ queryKey: ["planner", "materials"] });
  queryClient.invalidateQueries({ queryKey: ["planner", "reviews", "today"] });
}

export function useCreateUnits(materialId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (units: UnitItemInput[]) => createUnits(materialId, units),
    onSuccess: () => invalidateMaterial(queryClient, materialId),
  });
}

export function useUpdateUnit(materialId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      unitId,
      request,
    }: {
      unitId: number;
      request: UpdateUnitRequest;
    }) => updateUnit(unitId, request),
    onSuccess: () => invalidateMaterial(queryClient, materialId),
  });
}

export function useDeleteUnit(materialId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (unitId: number) => deleteUnit(unitId),
    onSuccess: () => invalidateMaterial(queryClient, materialId),
  });
}

export function useCompleteUnit(materialId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (unitId: number) => completeUnit(unitId),
    onSuccess: () => invalidateMaterial(queryClient, materialId),
  });
}
