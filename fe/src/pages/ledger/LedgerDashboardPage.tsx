import { PageHeader } from "@/components/PageHeader";

/**
 * 가계부 대시보드 `/ledger`.
 *
 * <p>이 변경(#1258)은 <b>진입 동선까지만</b> 만든다 — `/select` 4카드와 사이드바 스위처가
 * 갈 곳이 있어야 하므로 라우트와 껍데기를 먼저 세운다. 2축 요약 · 미납 경고 · 다가오는 결제는
 * <b>#1261</b>(v1) · <b>#1265</b>(v1.5)에서 이 자리에 붙는다.
 *
 * <p>껍데기가 필요한 이유는 하나 더 있다: `/ledger/*` 폴백만 두면 splat이 `/ledger` 자신도
 * 잡아 <b>같은 경로로 되돌리는 리다이렉트가 반복된다</b>. 워크스페이스에는 착지점이 있어야 한다.
 *
 * <p>화면 설계 §4의 본문 래퍼(`flex flex-col gap-6`)와 제목은 여기서 확정한다 —
 * 다음 변경이 레이아웃부터 다시 정하지 않도록.
 */
export function LedgerDashboardPage() {
  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="가계부"
        description="이번 달 얼마 쓰게 되고, 월말에 얼마 남나"
      />
      <p className="text-muted-foreground text-sm">
        자산 · 내역 · 입력 모달은 다음 작업(#1259 · #1260)에서 붙습니다.
      </p>
    </div>
  );
}
