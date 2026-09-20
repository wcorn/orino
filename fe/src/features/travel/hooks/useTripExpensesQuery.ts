import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { toast } from "@/shared/lib/toast";

import {
  createTripExpense,
  deleteTripExpense,
  type ExpenseCreateBody,
  type ExpenseUpdateBody,
  fetchTripExpenses,
  putTripBudget,
  updateTripExpense,
} from "../api/expenses";
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
 * 쓰고 나면 <b>경비와 여행 요약만</b> 지운다.
 *
 * <p>예전에는 가계부 캐시도 함께 지웠다(`invalidateLedger`) — 지출이 원장에 쌓였으니
 * 가계부 목록의 「여행」 배지와 월 합계가 함께 움직였기 때문이다. 이제 여행 지출은
 * 여행 안에서만 산다. 남의 캐시를 지울 이유가 없다.
 *
 * <p>요약은 지운다. 사이드바 여행 트리와 홈 카드가 「경비 41.2만」을 그 값으로 읽는다 —
 * 한쪽만 갱신하면 방금 적은 줄을 모르는 화면이 생긴다.
 */
function useExpenseInvalidation(tripId: number) {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({
      queryKey: travelKeys.expenses(tripId),
    });
    void queryClient.invalidateQueries({ queryKey: travelKeys.summary });
  };
}

export function useCreateTripExpense(tripId: number) {
  const invalidate = useExpenseInvalidation(tripId);

  return useMutation({
    mutationFn: (body: ExpenseCreateBody) => createTripExpense(tripId, body),
    onError: () => toast("지출을 적지 못했어요.", "error"),
    onSettled: invalidate,
  });
}

export function useUpdateTripExpense(tripId: number) {
  const invalidate = useExpenseInvalidation(tripId);

  return useMutation({
    mutationFn: ({
      expenseId,
      body,
    }: {
      expenseId: number;
      body: ExpenseUpdateBody;
    }) => updateTripExpense(tripId, expenseId, body),
    onError: () => toast("고치지 못했어요.", "error"),
    onSettled: invalidate,
  });
}

/**
 * 지운다. <b>되돌리기는 토스트에서 즉시만</b> 연다(§4.4) — 휴지통 화면을 만들지 않는다.
 *
 * <p>되돌리기는 같은 값으로 다시 적는 것이다. 지운 행을 살리는 API가 따로 없고,
 * 만들면 그건 휴지통의 시작이다.
 */
export function useDeleteTripExpense(tripId: number) {
  const invalidate = useExpenseInvalidation(tripId);

  return useMutation({
    mutationFn: (expenseId: number) => deleteTripExpense(tripId, expenseId),
    onError: () => toast("지우지 못했어요.", "error"),
    onSettled: invalidate,
  });
}

/**
 * 여행 예산. 지출과 달리 <b>여행에 붙은 값</b>이라({@code trip.budget_amount})
 * 경비 독립의 영향을 받지 않았다.
 */
export function usePutTripBudget(tripId: number) {
  const invalidate = useExpenseInvalidation(tripId);

  return useMutation({
    mutationFn: (amount: number | null) => putTripBudget(tripId, amount),
    onSuccess: (result) =>
      toast(
        result.amount === null ? "예산을 지웠어요" : "예산을 저장했어요",
        "success",
      ),
    onError: () => toast("예산을 저장하지 못했어요.", "error"),
    onSettled: invalidate,
  });
}
