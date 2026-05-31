import { useQuery } from "@tanstack/react-query";

import { fetchNote } from "../api/notes";
import { noteKeys } from "../queryKeys";

export function useNoteDetail(noteId: number | null) {
  return useQuery({
    queryKey: noteKeys.detail(noteId ?? 0),
    queryFn: () => fetchNote(noteId as number),
    enabled: noteId !== null,
    staleTime: Infinity,
  });
}
