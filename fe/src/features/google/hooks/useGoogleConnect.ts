import { useMutation } from "@tanstack/react-query";

import { toast } from "@/shared/lib/toast";

import { fetchGoogleAuthUrl } from "../api/googleApi";

/**
 * Google 연결 시작. 인증 URL을 받아 브라우저를 동의 화면으로 top-level redirect한다.
 * (콜백은 서버가 처리 후 `/planner?google=connected|error`로 되돌린다)
 */
export function useGoogleConnect() {
  return useMutation({
    mutationFn: fetchGoogleAuthUrl,
    onSuccess: (authorizationUrl) => {
      window.location.href = authorizationUrl;
    },
    onError: () => {
      toast("Google 연결을 시작하지 못했습니다. 다시 시도해 주세요.", "error");
    },
  });
}
