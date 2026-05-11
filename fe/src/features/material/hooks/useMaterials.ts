import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  createMaterial,
  type CreateMaterialRequest,
  deleteMaterial,
  fetchMaterial,
  fetchMaterials,
  type MaterialDetail,
  type MaterialStatus,
  type MaterialSummary,
  updateMaterial,
  type UpdateMaterialRequest,
} from "../api/materials";

export const materialsQueryKey = (status?: MaterialStatus) =>
  ["planner", "materials", status ?? "ALL"] as const;

export const materialDetailQueryKey = (id: number) =>
  ["planner", "material", id] as const;

export function useMaterials(status?: MaterialStatus) {
  return useQuery<MaterialSummary[]>({
    queryKey: materialsQueryKey(status),
    queryFn: () => fetchMaterials(status),
  });
}

export function useMaterial(id: number) {
  return useQuery<MaterialDetail>({
    queryKey: materialDetailQueryKey(id),
    queryFn: () => fetchMaterial(id),
  });
}

export function useCreateMaterial() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateMaterialRequest) => createMaterial(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["planner", "materials"] });
    },
  });
}

export function useUpdateMaterial(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: UpdateMaterialRequest) => updateMaterial(id, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["planner", "material", id] });
      queryClient.invalidateQueries({ queryKey: ["planner", "materials"] });
    },
  });
}

export function useDeleteMaterial() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deleteMaterial(id),
    onSuccess: (_, id) => {
      queryClient.removeQueries({ queryKey: ["planner", "material", id] });
      queryClient.invalidateQueries({ queryKey: ["planner", "materials"] });
    },
  });
}
