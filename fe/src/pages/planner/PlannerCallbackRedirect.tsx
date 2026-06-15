import { useEffect, useRef } from "react";
import { Navigate, useSearchParams } from "react-router-dom";

import { toast } from "@/shared/lib/toast";

/**
 * OAuth 콜백 복귀 지점(`/planner?google=connected|error`). 결과 토스트를 띄우고
 * 통합 캘린더로 보낸다. (서버 콜백이 이 경로로 302 redirect)
 */
export function PlannerCallbackRedirect() {
  const [params] = useSearchParams();
  const google = params.get("google");
  const fired = useRef(false);

  useEffect(() => {
    if (fired.current) return;
    fired.current = true;
    if (google === "connected") {
      toast("Google 캘린더가 연결되었습니다.", "success");
    } else if (google === "error") {
      toast("Google 연결에 실패했습니다. 다시 시도해 주세요.", "error");
    }
  }, [google]);

  return <Navigate to="/planner/calendar" replace />;
}
