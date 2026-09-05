import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { toast } from "@/shared/lib/toast";

import { fetchTripExpenses, putTripBudget } from "../api/expenses";
import { travelKeys } from "../queryKeys";

/** 경비 화면 한 벌. 그룹도 합계도 한 응답에서 나온다. */
export function useTripExpenses(tripId: number) {
  return useQuery({
    queryKey: travelKeys.expenses(tripId),
    queryFn: () => fetchTripExpenses(tripId),
    staleTime: 30 * 1000,
  });
}

/**
 * 여행 예산. <b>여행이 갖는 유일한 경비 쓰기</b>다.
 *
 * <p>가계부 쪽은 무효화하지 않는다 — 예산은 여행에만 사는 값이고 원장을 건드리지 않는다.
 * 반대로 홈 카드가 이 값을 읽게 될 자리라 여행 요약은 함께 지운다.
 */
export function usePutTripBudget(tripId: number) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (amount: number | null) => putTripBudget(tripId, amount),
    onSuccess: (result) =>
      toast(
        result.amount === null ? "예산을 지웠어요" : "예산을 저장했어요",
        "success",
      ),
    onError: () => toast("예산을 저장하지 못했어요.", "error"),
    onSettled: () => {
      void queryClient.invalidateQueries({
        queryKey: travelKeys.expenses(tripId),
      });
      void queryClient.invalidateQueries({ queryKey: travelKeys.summary });
    },
  });
}
