import { useMutation, useQueryClient } from "@tanstack/react-query";

import { createTrip, type TripWriteRequest, updateTrip } from "../api/travel";
import { travelKeys } from "../queryKeys";

/**
 * 여행 생성·수정. 성공하면 여행 관련 캐시를 통째로 무효화한다 — 요약·목록·상세가 모두
 * 같은 여행을 다른 각도로 보여주고 있어서, 하나만 갱신하면 화면끼리 어긋난다.
 */
export function useCreateTrip() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: TripWriteRequest) => createTrip(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: travelKeys.all });
    },
  });
}

export function useUpdateTrip(tripId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: TripWriteRequest) => updateTrip(tripId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: travelKeys.all });
    },
  });
}
