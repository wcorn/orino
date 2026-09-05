import { WifiOff } from "lucide-react";

interface OfflineBannerProps {
  /**
   * 무엇은 되는지. 화면마다 다르므로 받는다 — 「일정 조회만」이라고 적힌 배너가 준비
   * 화면에 뜨면, 무엇이 되는지 말해 주려던 문장이 오히려 헷갈리게 만든다.
   */
  what?: string;
}

/**
 * 오프라인 배너(§4.6).
 *
 * <p>편집 버튼을 감추기만 하면 사용자는 <b>앱이 고장 났다고 생각한다.</b> 왜 안 되는지와
 * 무엇은 되는지를 같이 말한다 — 조회는 그대로 된다.
 */
export function OfflineBanner({ what = "일정" }: OfflineBannerProps) {
  return (
    <p className="bg-muted text-muted-foreground flex items-center gap-2 rounded-lg px-3 py-2 text-[13px]">
      <WifiOff className="size-3.5 shrink-0" />
      오프라인 · {what} 조회만 가능합니다
    </p>
  );
}
