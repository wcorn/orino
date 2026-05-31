import { useQuery } from "@tanstack/react-query";

import { fetchNoteTree } from "../api/notes";
import { noteKeys } from "../queryKeys";

export function useNoteTree(materialId: number) {
  return useQuery({
    queryKey: noteKeys.tree(materialId),
    queryFn: () => fetchNoteTree(materialId),
  });
}
