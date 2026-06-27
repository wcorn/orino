import { useMutation, useQueryClient } from "@tanstack/react-query";

import { toast } from "@/shared/lib/toast";

import { setReviewMirror } from "../api/googleApi";
import { googleKeys } from "../queryKeys";

/**
 * 복습 미러 on/off 토글. ON이면 서버가 보조 캘린더 생성 + 백필을 수행하므로 isPending이 그 진행을 덮는다.
 * 성공 시 status 쿼리를 무효화해 reviewMirrorEnabled를 서버 진실로 갱신한다.
 */
export function useReviewMirrorToggle() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (enabled: boolean) => setReviewMirror(enabled),
    onSuccess: (_result, enabled) => {
      queryClient.invalidateQueries({ queryKey: googleKeys.status });
      toast(
        enabled
          ? "복습 일정을 Google 캘린더에 표시합니다."
          : "복습 캘린더 표시를 껐습니다.",
        "success",
      );
    },
    onError: () => {
      toast("설정 변경에 실패했습니다. 다시 시도해 주세요.", "error");
    },
  });
}
