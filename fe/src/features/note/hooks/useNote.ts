import { useQuery } from "@tanstack/react-query";

import { fetchNote } from "../api/notes";
import { noteKeys } from "../queryKeys";

export function useNote(materialId: number) {
  return useQuery({
    queryKey: noteKeys.byMaterial(materialId),
    queryFn: () => fetchNote(materialId),
    staleTime: Infinity,
  });
}
