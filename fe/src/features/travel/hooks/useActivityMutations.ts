import { useMutation, useQueryClient } from "@tanstack/react-query";

import {
  type ActivityWriteRequest,
  createActivity,
  deleteActivity,
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
  const invalidate = useInvalidateBoard(tripId);
  return useMutation({
    mutationFn: ({
      activityId,
      body,
    }: {
      activityId: number;
      body: ActivityWriteRequest;
    }) => updateActivity(activityId, body),
    onSuccess: invalidate,
  });
}

export function useDeleteActivity(tripId: number) {
  const invalidate = useInvalidateBoard(tripId);
  return useMutation({
    mutationFn: (activityId: number) => deleteActivity(activityId),
    onSuccess: invalidate,
  });
}
