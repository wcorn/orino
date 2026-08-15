import { useMutation, useQueryClient } from "@tanstack/react-query";

import {
  type ActivityWriteRequest,
  type Board,
  createActivity,
  deleteActivity,
  deleteMove,
  type MoveWriteRequest,
  reorderActivities,
  saveMove,
  updateActivity,
} from "../api/activities";
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

export function useDeleteActivity(tripId: number) {
  const invalidate = useInvalidateBoard(tripId);
  return useMutation({
    mutationFn: (activityId: number) => deleteActivity(activityId),
    onSuccess: invalidate,
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
