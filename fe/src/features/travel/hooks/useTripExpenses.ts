import { useMutation, useQueryClient } from "@tanstack/react-query";

import { invalidateLedger } from "@/features/ledger/hooks/useLedgerMutations";
import { toast } from "@/shared/lib/toast";

import { attachExpensesToTrip } from "../api/travel";
import { travelKeys } from "../queryKeys";

/**
 * 고른 지출을 여행에 붙인다(명세 v2.2 §18).
 *
 * <p><b>양쪽을 다 무효화한다.</b> 붙이면 가계부 목록의 「여행」 배지가 붙고, 여행 쪽에서는
 * 홈 카드와 경비 화면이 그 합계를 읽는다 — 한쪽만 갱신하면 건너간 화면이 방금 한 일을
 * 모르는 상태가 된다.
 */
export function useAttachExpensesToTrip() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      tripId,
      transactionIds,
    }: {
      tripId: number;
      transactionIds: number[];
    }) => attachExpensesToTrip(tripId, transactionIds),
    onSuccess: (result) =>
      toast(`${result.affected}건을 여행에 붙였어요`, "success"),
    onError: () => toast("붙이지 못했어요.", "error"),
    onSettled: () => {
      invalidateLedger(queryClient);
      void queryClient.invalidateQueries({ queryKey: travelKeys.all });
    },
  });
}
