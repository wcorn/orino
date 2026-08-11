import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  createStay,
  deleteStay,
  fetchStays,
  type StayWriteRequest,
  updateStay,
} from "../api/stays";
import { travelKeys } from "../queryKeys";

/**
 * 여행의 숙소 전체를 한 번에 읽는다.
 *
 * <p>날짜별로 나누지 않는 이유는 <b>어느 날짜에 어떤 숙소가 붙는지를 저장하지 않기</b>
 * 때문이다(기간에서 파생한다). 날짜별로 캐시하면 숙소 하나를 고쳤을 때 무효화할 날짜를
 * 다시 계산해야 하고, 그 계산이 곧 파생 규칙의 두 번째 사본이 된다.
 */
export function useStays(tripId: number, options: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: travelKeys.stays(tripId),
    queryFn: () => fetchStays(tripId),
    staleTime: 10_000,
    enabled: options.enabled ?? true,
  });
}

/**
 * 숙소가 바뀌면 <b>보드를 통째로</b> 무효화한다. 숙소 하나가 여러 날짜에 걸쳐 있어
 * `days[].stayTonight`·`stayCheckout`과 `stayMove`가 동시에 달라지기 때문이다 —
 * 보고 있던 날짜만 갱신하면 다른 탭이 옛 숙소를 계속 말한다.
 */
function useInvalidateStays(tripId: number) {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: travelKeys.stays(tripId) });
    void queryClient.invalidateQueries({ queryKey: travelKeys.boards(tripId) });
  };
}

export function useCreateStay(tripId: number) {
  const invalidate = useInvalidateStays(tripId);
  return useMutation({
    mutationFn: (body: StayWriteRequest) => createStay(tripId, body),
    onSuccess: invalidate,
  });
}

export function useUpdateStay(tripId: number) {
  const invalidate = useInvalidateStays(tripId);
  return useMutation({
    mutationFn: ({
      stayId,
      body,
    }: {
      stayId: number;
      body: StayWriteRequest;
    }) => updateStay(stayId, body),
    onSuccess: invalidate,
  });
}

export function useDeleteStay(tripId: number) {
  const invalidate = useInvalidateStays(tripId);
  return useMutation({
    mutationFn: (stayId: number) => deleteStay(stayId),
    onSuccess: invalidate,
  });
}
