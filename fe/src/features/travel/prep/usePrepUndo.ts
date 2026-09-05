import { useQueryClient } from "@tanstack/react-query";

import { toast, toastUndo } from "@/shared/lib/toast";

import { usePendingPrepActions } from "../board/pendingActions";
import { travelKeys } from "../queryKeys";

/**
 * 되돌릴 수 있는 준비 항목 삭제. 일정과 같은 방식이다(§13).
 *
 * <p>화면에서는 즉시 사라지고 <b>실제 요청은 5초 뒤에</b> 나간다. 그 안에 되돌리면 요청
 * 자체가 나가지 않으므로 서버에는 애초에 아무 일도 없었던 게 된다 — 복원 API를 두지 않는
 * 이유가 그것이다.
 *
 * <p>보류함은 일정과 따로 쓴다. 준비 항목 5번과 일정 5번은 다른 것이다.
 */
export function usePrepUndo(tripId: number) {
  const queryClient = useQueryClient();
  const { defer, commit, cancel } = usePendingPrepActions();

  return (options: {
    itemId: number;
    message: string;
    run: () => Promise<unknown>;
  }) => {
    const { itemId, message, run } = options;

    defer(itemId, () => {
      // 보류가 풀린 뒤에야 요청이 나가므로 여기서 실패하면 알려 줄 사람이 없다.
      // 화면에서는 이미 지운 뒤라 조용히 두면 서버와 어긋난 채로 남는다.
      void run().catch(() => {
        toast("지우지 못했어요.", "error");
        void queryClient.invalidateQueries({
          queryKey: travelKeys.prep(tripId),
        });
      });
    });

    toastUndo(message, {
      onUndo: () => cancel(itemId),
      onCommit: () => commit(itemId),
    });
  };
}
