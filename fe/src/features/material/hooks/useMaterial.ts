import { useQuery } from "@tanstack/react-query";

import { fetchMaterial } from "../api/materials";
import { materialKeys } from "../queryKeys";

export function useMaterial(id: number) {
  return useQuery({
    queryKey: materialKeys.detail(id),
    queryFn: () => fetchMaterial(id),
  });
}
