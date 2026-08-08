import { WifiOff } from "lucide-react";

/**
 * 오프라인 배너(§4.6).
 *
 * <p>편집 버튼을 감추기만 하면 사용자는 <b>앱이 고장 났다고 생각한다.</b> 왜 안 되는지와
 * 무엇은 되는지를 같이 말한다 — 조회는 그대로 된다.
 */
export function OfflineBanner() {
  return (
    <p className="bg-muted text-muted-foreground flex items-center gap-2 rounded-lg px-3 py-2 text-[13px]">
      <WifiOff className="size-3.5 shrink-0" />
      오프라인 · 일정 조회만 가능합니다
    </p>
  );
}
