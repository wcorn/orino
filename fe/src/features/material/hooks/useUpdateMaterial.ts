import { useMutation, useQueryClient } from "@tanstack/react-query";

import {
  type Material,
  type MaterialUpdateRequest,
  updateMaterial,
} from "../api/materials";
import { materialKeys } from "../queryKeys";

export function useUpdateMaterial(id: number) {
  const queryClient = useQueryClient();
  return useMutation<Material, Error, MaterialUpdateRequest>({
    mutationFn: (request) => updateMaterial(id, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: materialKeys.all });
    },
  });
}
