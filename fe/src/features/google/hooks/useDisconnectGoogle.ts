import { useMutation, useQueryClient } from "@tanstack/react-query";

import { toast } from "@/shared/lib/toast";

import { disconnectGoogle } from "../api/googleApi";
import { googleKeys } from "../queryKeys";

export function useDisconnectGoogle() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: disconnectGoogle,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: googleKeys.all });
      toast("Google 연동을 해제했습니다.", "success");
    },
    onError: () => {
      toast("연동 해제에 실패했습니다. 다시 시도해 주세요.", "error");
    },
  });
}
