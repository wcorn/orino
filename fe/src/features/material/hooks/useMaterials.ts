import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  createMaterial,
  type CreateMaterialRequest,
  fetchMaterials,
  type MaterialStatus,
  type MaterialSummary,
} from "../api/materials";

export const materialsQueryKey = (status?: MaterialStatus) =>
  ["planner", "materials", status ?? "ALL"] as const;

export function useMaterials(status?: MaterialStatus) {
  return useQuery<MaterialSummary[]>({
    queryKey: materialsQueryKey(status),
    queryFn: () => fetchMaterials(status),
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
