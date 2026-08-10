import { useMutation, useQueryClient } from "@tanstack/react-query";

import { type DayUpdateRequest, updateDay } from "../api/days";
import { travelKeys } from "../queryKeys";

/**
 * 기준 도시 변경 · 도시 메모.
 *
 * <p>성공하면 <b>그 여행의 보드를 통째로</b> 무효화한다. 하루를 바꾸면 구간이 다시 나뉘어
 * 앞뒤 날짜의 `legIndex`·`cityChanged`까지 달라지고, 타임존이 바뀐 날짜는 시각 표시와 날씨도
 * 달라진다 — 바꾼 날짜만 갱신하면 탭이 서로 어긋난 채 남는다.
 */
export function useUpdateDay(tripId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ dayId, body }: { dayId: number; body: DayUpdateRequest }) =>
      updateDay(dayId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: travelKeys.boards(tripId),
      });
    },
  });
}
