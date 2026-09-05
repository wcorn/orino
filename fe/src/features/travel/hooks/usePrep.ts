import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { toast } from "@/shared/lib/toast";

import {
  createPrepItem,
  deletePrepItem,
  fetchPrep,
  type PrepCreateRequest,
  type PrepPatchRequest,
  type PrepResponse,
  updatePrepItem,
} from "../api/prep";
import { travelKeys } from "../queryKeys";

/**
 * 준비 목록. 화면 한 벌을 한 번에 읽는다.
 *
 * <p>기내에서도 열려야 하므로(§13) 캐시를 오래 신선하다고 본다 — 오프라인에서는 어차피
 * 다시 못 받아오고, 그때 필요한 것은 「최신」이 아니라 「보인다」다.
 */
export function usePrep(tripId: number) {
  return useQuery({
    queryKey: travelKeys.prep(tripId),
    queryFn: () => fetchPrep(tripId),
    staleTime: 30 * 1000,
  });
}

/**
 * 준비가 바뀌면 <b>요약도 함께</b> 무효화한다.
 *
 * <p>사이드바 배지와 홈 카드의 준비 줄은 요약(`/travel/summary`)을 읽는다. 준비만 무효화하면
 * 기한 지난 항목을 체크했을 때 화면의 경고는 사라지는데 배지에는 1이 그대로 남는다 —
 * 사용자는 무엇을 더 눌러야 배지가 사라지는지 알 수 없다(§13에서 막으려던 바로 그 상태다).
 */
function useInvalidatePrep(tripId: number) {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: travelKeys.prep(tripId) });
    void queryClient.invalidateQueries({ queryKey: travelKeys.summary });
  };
}

/**
 * 항목 추가.
 *
 * <p>성공 토스트를 띄우지 않는다 — 붙박이 입력줄은 스무 개를 연달아 치는 자리라 매번
 * 스낵바가 뜨면 그게 곧 방해다. 추가됐다는 것은 목록에 줄이 하나 늘어난 것으로 보인다.
 */
export function useCreatePrepItem(tripId: number) {
  const invalidate = useInvalidatePrep(tripId);
  return useMutation({
    mutationFn: (body: PrepCreateRequest) => createPrepItem(tripId, body),
    onError: () => toast("추가하지 못했어요.", "error"),
    onSettled: invalidate,
  });
}

/**
 * 항목 수정 · 체크 토글.
 *
 * <p><b>체크는 낙관적으로 반영한다.</b> 짐을 싸면서 스무 번 누르는 동작이라 왕복을 기다리면
 * 손보다 화면이 늦다. 실패하면 되돌리고 알린다 — 조용히 두면 체크한 줄 알고 가방을 닫는다.
 *
 * <p>다만 진행률·기한 지남 개수는 낙관적으로 고치지 않고 <b>서버가 돌려준 집계</b>를 쓴다.
 * 기한 지남은 「첫날 기준 도시의 오늘」로 판정하는데 그 시각이 브라우저에 없다.
 */
export function useUpdatePrepItem(tripId: number) {
  const queryClient = useQueryClient();
  const invalidate = useInvalidatePrep(tripId);

  return useMutation({
    mutationFn: ({
      itemId,
      body,
    }: {
      itemId: number;
      body: PrepPatchRequest;
    }) => updatePrepItem(itemId, body),

    onMutate: async ({ itemId, body }) => {
      if (body.done === undefined) return undefined;
      const key = travelKeys.prep(tripId);
      await queryClient.cancelQueries({ queryKey: key });
      const snapshot = queryClient.getQueryData<PrepResponse>(key);
      if (snapshot) {
        queryClient.setQueryData<PrepResponse>(key, {
          ...snapshot,
          groups: snapshot.groups.map((group) => ({
            ...group,
            items: group.items.map((item) =>
              item.id === itemId
                ? { ...item, done: body.done as boolean }
                : item,
            ),
          })),
        });
      }
      return { key, snapshot };
    },

    onError: (_error, _variables, context) => {
      if (context?.snapshot) {
        queryClient.setQueryData(context.key, context.snapshot);
      }
      toast("저장하지 못했어요.", "error");
    },

    onSettled: invalidate,
  });
}

/** 되돌리기는 화면이 5초 미뤄서 한다 — 여기까지 오면 이미 되돌릴 뜻이 없다. */
export function useDeletePrepItem(tripId: number) {
  const invalidate = useInvalidatePrep(tripId);
  return useMutation({
    mutationFn: (itemId: number) => deletePrepItem(itemId),
    onSettled: invalidate,
  });
}
