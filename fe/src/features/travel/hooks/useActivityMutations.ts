import { useMutation, useQueryClient } from "@tanstack/react-query";

import {
  type Activity,
  type ActivityWriteRequest,
  type Board,
  createActivity,
  deleteActivity,
  deleteMove,
  type MoveWriteRequest,
  reorderActivities,
  saveMove,
  swapDayActivities,
  updateActivity,
} from "../api/activities";
import { activityWriteBodyFrom } from "../lib/activityWriteBody";
import { travelKeys } from "../queryKeys";

/**
 * 일정을 건드리면 그 여행의 보드 캐시를 통째로 비운다.
 *
 * <p>날짜별로 캐시가 나뉘어 있는데 일정 하나를 옮기면 <b>두 날짜와 보관함 건수·날짜 탭 건수가
 * 함께 바뀐다</b>. 보고 있는 날짜만 갱신하면 다른 탭이 옛 숫자를 들고 있게 된다.
 * 여행 목록·요약의 일정 수도 같이 어긋나므로 함께 무효화한다.
 */
function useInvalidateBoard(tripId: number) {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: travelKeys.boards(tripId) });
    void queryClient.invalidateQueries({ queryKey: travelKeys.summary });
    void queryClient.invalidateQueries({ queryKey: ["travel", "trips"] });
    void queryClient.invalidateQueries({ queryKey: travelKeys.trip(tripId) });
  };
}

export function useCreateActivity(tripId: number) {
  const invalidate = useInvalidateBoard(tripId);
  return useMutation({
    mutationFn: (body: ActivityWriteRequest) => createActivity(tripId, body),
    onSuccess: invalidate,
  });
}

export function useUpdateActivity(tripId: number) {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateBoard(tripId);
  return useMutation({
    mutationFn: ({
      activityId,
      body,
    }: {
      activityId: number;
      body: ActivityWriteRequest;
    }) => updateActivity(activityId, body),
    onSuccess: (_data, { activityId }) => {
      invalidate();
      void queryClient.invalidateQueries({
        queryKey: travelKeys.activity(activityId),
      });
    },
  });
}

/**
 * 일정 하나를 보드 캐시에서 먼저 뺀다. 요청은 곧바로 나가지만(#1432) 응답을 기다리면
 * 스와이프한 행이 제자리로 튀었다가 사라진다. 실패하면 돌려놓을 수 있게 전부 떠 둔다.
 *
 * <p>날짜별 캐시가 여럿이라 그 여행의 보드 전부에서 뺀다 — 지금 보는 날짜만 고치면
 * 다른 탭으로 갔을 때 지운 일정이 잠깐 보인다.
 */
function useOptimisticRemove(tripId: number) {
  const queryClient = useQueryClient();
  return {
    remove: async (activityId: number) => {
      const prefix = travelKeys.boards(tripId);
      await queryClient.cancelQueries({ queryKey: prefix });
      const snapshots = queryClient.getQueriesData<Board>({ queryKey: prefix });
      queryClient.setQueriesData<Board>({ queryKey: prefix }, (old) =>
        old
          ? {
              ...old,
              activities: old.activities.filter((a) => a.id !== activityId),
            }
          : old,
      );
      return { snapshots };
    },
    restore: (context?: {
      snapshots: [readonly unknown[], Board | undefined][];
    }) => {
      context?.snapshots.forEach(([key, data]) =>
        queryClient.setQueryData(key, data),
      );
    },
  };
}

/**
 * 삭제. <b>누르는 즉시 요청을 보낸다</b>(#1432) — 예전에는 실행취소를 위해 5초 미뤘다.
 * 서버에 복원 API가 없으니 이제 되돌리기는 없다.
 */
export function useDeleteActivity(tripId: number) {
  const invalidate = useInvalidateBoard(tripId);
  const { remove, restore } = useOptimisticRemove(tripId);
  return useMutation({
    mutationFn: (activityId: number) => deleteActivity(activityId),
    onMutate: remove,
    onError: (_error, _id, context) => restore(context),
    onSettled: invalidate,
  });
}

/**
 * 보관함으로 내리기. 삭제와 같은 이유로 바로 보내고 지금 날짜에서 먼저 뺀다(#1432).
 *
 * <p>수정은 전체 교체라 지금 값을 그대로 실어 보낸다 — 호출자가 일정 전체를 넘긴다.
 */
export function useArchiveActivity(tripId: number) {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateBoard(tripId);
  const { remove, restore } = useOptimisticRemove(tripId);
  return useMutation({
    mutationFn: (activity: Activity) =>
      updateActivity(
        activity.id,
        activityWriteBodyFrom(activity, { activityDate: null }),
      ),
    onMutate: (activity) => remove(activity.id),
    onError: (_error, _activity, context) => restore(context),
    onSettled: (_data, _error, activity) => {
      invalidate();
      void queryClient.invalidateQueries({
        queryKey: travelKeys.activity(activity.id),
      });
    },
  });
}

/**
 * 같은 날짜 안의 순서 변경. <b>낙관적으로 먼저 반영하고 실패하면 되돌린다</b> —
 * 드래그는 손을 뗀 순간 결과가 보여야 하고, 왕복을 기다리면 행이 제자리로 튀었다가
 * 다시 움직이는 것처럼 보인다.
 */
export function useReorderActivities(tripId: number) {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateBoard(tripId);

  return useMutation({
    mutationFn: ({
      date,
      activityIds,
    }: {
      date: string | null;
      activityIds: number[];
    }) => reorderActivities(tripId, [{ date, activityIds }]),

    onMutate: async ({ date, activityIds }) => {
      const key = travelKeys.board(tripId, date ?? "archive");
      await queryClient.cancelQueries({ queryKey: key });
      const snapshot = queryClient.getQueryData<Board>(key);
      if (snapshot) {
        const byId = new Map(snapshot.activities.map((a) => [a.id, a]));
        queryClient.setQueryData<Board>(key, {
          ...snapshot,
          activities: activityIds
            .map((id) => byId.get(id))
            .filter((a): a is NonNullable<typeof a> => Boolean(a)),
        });
      }
      return { key, snapshot };
    },

    // 서버가 다시 이어 준 이동을 곧바로 반영한다. onSettled의 재조회를 기다리면
    // 순서만 먼저 바뀌고 이동이 뒤늦게 따라붙어 화면이 두 번 움직인다.
    onSuccess: (moves, { date }) => {
      const key = travelKeys.board(tripId, date ?? "archive");
      queryClient.setQueryData<Board>(key, (old) =>
        old ? { ...old, moves } : old,
      );
    },

    onError: (_error, _vars, context) => {
      if (context?.snapshot) {
        queryClient.setQueryData(context.key, context.snapshot);
      }
    },

    onSettled: invalidate,
  });
}

/**
 * 하루 통째로 교체. 캐시를 좁혀 손대지 않고 보드를 통째로 비운다 — 두 날짜의 목록과
 * 날짜 탭 건수가 한꺼번에 바뀌고, 이동·알림도 서버가 다시 짠다.
 */
export function useSwapDayActivities(tripId: number) {
  const invalidate = useInvalidateBoard(tripId);
  return useMutation({
    mutationFn: (body: { date: string; withDate: string }) =>
      swapDayActivities(tripId, body),
    onSuccess: invalidate,
  });
}

/**
 * 이동 저장. 보드 캐시를 통째로 비운다 — 같은 장소 쌍을 잇는 <b>다른 날짜의 이동도 함께</b>
 * 바뀌기 때문이다. 보고 있는 날짜만 갱신하면 다른 탭이 옛 값을 들고 있게 된다.
 *
 * <p>출발 알림 시각도 이 값에 걸려 있어 서버가 다시 짠다 — 화면에는 안 보이지만
 * 무효화 범위를 좁히면 안 되는 이유가 하나 더 있는 셈이다.
 */
export function useSaveMove(tripId: number) {
  const invalidate = useInvalidateBoard(tripId);
  return useMutation({
    mutationFn: (body: MoveWriteRequest) => saveMove(tripId, body),
    onSuccess: invalidate,
  });
}

export function useDeleteMove(tripId: number) {
  const invalidate = useInvalidateBoard(tripId);
  return useMutation({
    mutationFn: ({
      from,
      to,
    }: {
      from: number;
      to: { activityId: number } | { stayId: number };
    }) => deleteMove(tripId, from, to),
    onSuccess: invalidate,
  });
}
