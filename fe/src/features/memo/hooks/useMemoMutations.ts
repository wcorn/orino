import { useMutation, useQueryClient } from "@tanstack/react-query";

import {
  createMemo,
  deleteMemo,
  type MemoCreateRequest,
  type MemoDetail,
} from "../api/memos";
import { memoKeys } from "../queryKeys";

export function useCreateMemo() {
  const queryClient = useQueryClient();
  return useMutation<MemoDetail, Error, MemoCreateRequest>({
    mutationFn: createMemo,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: memoKeys.tree() });
    },
  });
}

export function useDeleteMemo() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, number>({
    mutationFn: deleteMemo,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: memoKeys.tree() });
    },
  });
}
