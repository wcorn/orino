import { useQuery } from "@tanstack/react-query";

import { fetchNoteTree } from "../api/notes";
import { noteKeys } from "../queryKeys";

/** materialId가 있으면 자료 종속 노트, 없으면 독립 노트 트리. */
export function useNoteTree(materialId?: number) {
  return useQuery({
    queryKey: noteKeys.tree(materialId),
    queryFn: () => fetchNoteTree(materialId),
  });
}
