import { useMutation, useQueryClient } from "@tanstack/react-query";

import { deleteMaterial } from "../api/materials";
import { materialKeys } from "../queryKeys";

export function useDeleteMaterial() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, number>({
    mutationFn: deleteMaterial,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: materialKeys.all });
    },
  });
}
