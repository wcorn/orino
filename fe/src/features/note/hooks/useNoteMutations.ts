import { useMutation, useQueryClient } from "@tanstack/react-query";

import {
  createNote,
  deleteNote,
  type NoteCreateRequest,
  type NoteDetail,
} from "../api/notes";
import { noteKeys } from "../queryKeys";

export function useCreateNote(materialId: number) {
  const queryClient = useQueryClient();
  return useMutation<NoteDetail, Error, NoteCreateRequest>({
    mutationFn: (request) => createNote(materialId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: noteKeys.tree(materialId) });
    },
  });
}

export function useDeleteNote(materialId: number) {
  const queryClient = useQueryClient();
  return useMutation<void, Error, number>({
    mutationFn: deleteNote,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: noteKeys.tree(materialId) });
    },
  });
}
