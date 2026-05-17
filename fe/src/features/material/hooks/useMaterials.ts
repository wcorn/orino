import { useQuery } from "@tanstack/react-query";

import { fetchMaterials, type MaterialStatus } from "../api/materials";
import { materialKeys } from "../queryKeys";

export function useMaterials(status?: MaterialStatus) {
  return useQuery({
    queryKey: materialKeys.list(status),
    queryFn: () => fetchMaterials(status),
  });
}
