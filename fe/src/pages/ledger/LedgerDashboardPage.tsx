import { ArrowRight, Plus, ReceiptText } from "lucide-react";
import { Link } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { LoadingText } from "@/components/ui/loading-text";
import { useTransactionModal } from "@/features/ledger/components/transactionModalContext";
import { useLedgerDashboard } from "@/features/ledger/hooks/useLedgerQueries";
import { formatAmount } from "@/features/ledger/lib/money";

/**
 * 가계부 대시보드 `/ledger`.
 *
 * <p><b>v1은 껍데기다 — 알고 만든다</b>([D-7](https://github.com/wcorn/orino/wiki/Ledger-Open-Items)).
 * 이 모듈에서 가장 중요한 블록인 2축 요약·미납 경고·다가오는 결제는 전부 v1.5다. 예정 없이는
 * 그릴 수 없고, 예정은 정기 항목·청구서에 딸려 있다.
 *
 * <p>그래서 그 자리를 <b>비워 두지 않고 아예 그리지 않는다.</b> 빈 카드가 있으면 고장난 것처럼
 * 보인다. 서버도 그 필드를 내리지 않는다 — 화면과 API가 같은 결정을 공유한다.
 *
 * <p>v1에 남는 것: 이미 쓴 돈 · 이번 달 수입 · 정리할 내역 · 빠른 입력.
 */
export function LedgerDashboardPage() {
  const { data, isPending, isError } = useLedgerDashboard();
  const { openTransactionModal } = useTransactionModal();

  return (
    <div className="mx-auto flex max-w-[880px] flex-col gap-6">
      <PageHeader
        title="가계부"
        description={data && periodLabel(data.period)}
        actions={
          <Button type="button" onClick={openTransactionModal}>
            <Plus className="size-4" />
            입력 <kbd className="ml-1 text-[11px] opacity-70">N</kbd>
          </Button>
        }
      />

      {isPending && <LoadingText />}
      {isError && (
        <Alert variant="destructive">가계부를 불러오지 못했어요.</Alert>
      )}

      {data && (
        <>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <StatCard label="이미 쓴 돈" value={data.spending.spent} />
            <StatCard
              label="이번 달 수입"
              value={data.income.amount}
              tone="text-success"
            />
          </div>

          {/*
            정리할 내역이 있을 때만 나온다. 0건일 때 「0건」을 그리면 할 일이 없다는 사실이
            할 일처럼 보인다. 목표는 월말 기준 5% 미만이다(확정 명세 §17).
          */}
          {data.todo.uncategorized > 0 && (
            <Alert variant="warning">
              <ReceiptText />
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span>
                  정리할 내역 {data.todo.uncategorized}건 — 카테고리만 채우면
                  됩니다.
                </span>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  render={<Link to="/ledger/transactions?uncategorized=1" />}
                >
                  정리하기
                  <ArrowRight className="size-3.5" />
                </Button>
              </div>
            </Alert>
          )}

          <section className="flex flex-col gap-2">
            <h2 className="text-[13px] font-semibold">바로 가기</h2>
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                variant="outline"
                render={<Link to="/ledger/transactions" />}
              >
                내역 보기
              </Button>
              <Button
                type="button"
                variant="outline"
                render={<Link to="/ledger/assets" />}
              >
                자산
              </Button>
              <Button
                type="button"
                variant="outline"
                render={<Link to="/ledger/stats" />}
              >
                통계
              </Button>
            </div>
          </section>
        </>
      )}
    </div>
  );
}

function StatCard({
  label,
  value,
  tone,
}: {
  label: string;
  value: number;
  tone?: string;
}) {
  return (
    <div className="bg-card ring-foreground/10 flex flex-col gap-1 rounded-xl p-5 ring-1">
      <span className="text-muted-foreground text-[13px]">{label}</span>
      <span
        className={`text-[28px]/[1.1] font-semibold tracking-[-0.02em] tabular-nums ${tone ?? ""}`}
      >
        {formatAmount(value)}
      </span>
    </div>
  );
}

/** `2026년 8월 · 월 시작일 1일`. 시작일이 1이 아니면 구간이 두 달에 걸친다. */
function periodLabel(period: {
  start: string;
  end: string;
  monthStartDay: number;
}): string {
  const start = new Date(`${period.start}T00:00:00`);
  const day =
    period.monthStartDay === 99 ? "말일" : `${period.monthStartDay}일`;
  return `${start.getFullYear()}년 ${start.getMonth() + 1}월 · 월 시작일 ${day}`;
}
