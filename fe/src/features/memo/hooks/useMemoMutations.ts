import { useMutation, useQueryClient } from "@tanstack/react-query";

import {
  createMemo,
  deleteMemo,
  type MemoCreateRequest,
  type MemoDetail,
  updateMemo,
} from "../api/memos";
import { memoKeys } from "../queryKeys";
import type { MovePlan } from "../treeMove";

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

/**
 * 트리 드래그 이동/정렬. 새 부모의 자식 순서(orderedIds)를 0..n으로 재배치하고,
 * 드래그 노드는 parentId까지 함께 PATCH한다. BE는 단건 PATCH만 지원하므로
 * 그룹 내 각 노드를 순차 PATCH한 뒤 트리를 한 번 무효화한다.
 * (옮겨진 노드가 빠진 기존 부모 그룹은 sortOrder에 빈틈이 생겨도 순서는 유지된다.)
 */
export function useMoveMemo() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, { plan: MovePlan; dragId: number }>({
    mutationFn: async ({ plan, dragId }) => {
      for (let i = 0; i < plan.orderedIds.length; i++) {
        const id = plan.orderedIds[i];
        await updateMemo(
          id,
          id === dragId
            ? { parentId: plan.parentId, sortOrder: i }
            : { sortOrder: i },
        );
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: memoKeys.tree() });
    },
  });
}
