import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { useSearchParams } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";
import { GoogleConnectionCard } from "@/features/google/components/GoogleConnectionCard";
import { googleKeys } from "@/features/google/queryKeys";
import { toast } from "@/shared/lib/toast";

/**
 * 연동 설정 페이지(공용, `/integrations`). Google 연결 관리 카드를 보여준다.
 * 캘린더·루틴·복습 미러가 공유하는 공용 연결이라 플래너 밖에 둔다.
 *
 * OAuth 콜백 복귀 지점이기도 하다 — 서버가 `/integrations?google=connected|error`로
 * 302 리다이렉트하면 결과 토스트를 띄우고 연결 상태 쿼리를 무효화한 뒤, 새로고침 시
 * 재발생하지 않도록 쿼리 파라미터를 정리한다.
 */
export function IntegrationsPage() {
  const [params, setParams] = useSearchParams();
  const queryClient = useQueryClient();
  const fired = useRef(false);

  useEffect(() => {
    const google = params.get("google");
    if (!google || fired.current) return;
    fired.current = true;

    if (google === "connected") {
      toast("Google 계정이 연결되었습니다.", "success");
      void queryClient.invalidateQueries({ queryKey: googleKeys.status });
    } else if (google === "error") {
      toast("Google 연결에 실패했습니다. 다시 시도해 주세요.", "error");
    }
    setParams({}, { replace: true });
  }, [params, queryClient, setParams]);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="연동 설정" />
      <div className="max-w-md">
        <GoogleConnectionCard />
      </div>
    </div>
  );
}
