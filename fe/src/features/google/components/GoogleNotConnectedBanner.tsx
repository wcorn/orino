import { AlertTriangle } from "lucide-react";

import { useGoogleStatus } from "../hooks/useGoogleStatus";
import { GoogleConnectButton } from "./GoogleConnectButton";

/**
 * 미연동 CTA 배너. 통합 캘린더 상단에 노출한다.
 * 로딩/에러/연결됨 상태에서는 렌더하지 않는다(복습은 그대로 표시).
 */
export function GoogleNotConnectedBanner() {
  const { data: status } = useGoogleStatus();

  if (!status || status.connected) {
    return null;
  }

  return (
    <div
      role="alert"
      className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm dark:border-amber-900/60 dark:bg-amber-950/40"
    >
      <span className="flex items-center gap-2 text-amber-700 dark:text-amber-300">
        <AlertTriangle className="size-4" />
        Google 캘린더가 연결되지 않았습니다.
      </span>
      <GoogleConnectButton variant="outline" size="sm" />
    </div>
  );
}
