import { useQueryClient } from "@tanstack/react-query";

import { toast, toastUndo } from "@/shared/lib/toast";

import { travelKeys } from "../queryKeys";
import { usePendingActions } from "./pendingActions";

/**
 * 일정 하나에 대한 되돌릴 수 있는 동작. 보드와 일정 상세가 같은 방식으로 쓴다.
 *
 * <p>화면에서는 즉시 반영되고, 실제 요청은 5초 뒤에 나간다. 그 안에 되돌리면 요청 자체가
 * 나가지 않는다.
 */
export function useUndoableAction(tripId: number) {
  const queryClient = useQueryClient();
  const { defer, commit, cancel } = usePendingActions();

  return (options: {
    activityId: number;
    message: string;
    run: () => Promise<unknown>;
  }) => {
    const { activityId, message, run } = options;

    defer(activityId, () => {
      // 보류가 풀린 뒤에야 요청이 나가므로 여기서 실패하면 알려 줄 사람이 없다.
      // 화면에서는 이미 지운 뒤라 조용히 두면 서버와 어긋난 채로 남는다.
      void run().catch(() => {
        toast("변경을 저장하지 못했어요.", "error");
        void queryClient.invalidateQueries({
          queryKey: travelKeys.boards(tripId),
        });
      });
    });

    toastUndo(message, {
      onUndo: () => cancel(activityId),
      onCommit: () => commit(activityId),
    });
  };
}
