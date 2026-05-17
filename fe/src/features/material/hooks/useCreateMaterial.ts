import { useMutation, useQueryClient } from "@tanstack/react-query";

import {
  createMaterial,
  type MaterialCreateRequest,
  type MaterialCreateResponse,
} from "../api/materials";
import { materialKeys } from "../queryKeys";

export function useCreateMaterial() {
  const queryClient = useQueryClient();
  return useMutation<MaterialCreateResponse, Error, MaterialCreateRequest>({
    mutationFn: createMaterial,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: materialKeys.all });
    },
  });
}
