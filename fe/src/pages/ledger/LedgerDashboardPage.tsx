import { ArrowRight, Plus, ReceiptText, Rows3 } from "lucide-react";
import { Link } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { LoadingText } from "@/components/ui/loading-text";
import type {
  LedgerDashboard,
  UpcomingItem,
} from "@/features/ledger/api/ledger";
import { OverdueAlert } from "@/features/ledger/components/OverdueAlert";
import { useTransactionModal } from "@/features/ledger/components/transactionModalContext";
import { useApplyTemplate } from "@/features/ledger/hooks/useLedgerMutations";
import {
  useLedgerBudget,
  useLedgerDashboard,
  useLedgerTemplates,
} from "@/features/ledger/hooks/useLedgerQueries";
import {
  balanceToneClass,
  formatDday,
  gaugeWidths,
  UPCOMING_KIND_LABELS,
} from "@/features/ledger/lib/balance";
import {
  formatAmount,
  formatBalance,
  MINUS,
} from "@/features/ledger/lib/money";
import { cn } from "@/lib/utils";

/**
 * 가계부 대시보드 `/ledger`.
 *
 * <p><b>2축 요약이 이 화면의 중심이다</b>(`LDG-053` · 확정 명세 §8.2). 좌 「이번 달 소비」는
 * <b>소비 시점</b> 기준이고 카드 대금이 빠진다. 우 「통장에서 나갈 돈」은 <b>출금 시점</b>
 * 기준이고 이체와 카드 대금이 들어간다. <b>두 카드를 섞지 않는다</b> — 다른 질문에 답한다.
 *
 * <p>v1에서는 이 자리와 미납·다가오는 결제가 통째로 없었다. 예정 없이는 그릴 수 없어서
 * 빈 카드를 만드느니 아예 안 그렸다(D-7). v1.5에서 예정이 생겨 채워진다.
 */
export function LedgerDashboardPage() {
  const { data, isPending, isError } = useLedgerDashboard();
  const { data: budget } = useLedgerBudget();
  const { data: templates } = useLedgerTemplates();
  const { openTransactionModal } = useTransactionModal();
  const apply = useApplyTemplate();

  return (
    <div className="mx-auto flex max-w-[880px] flex-col gap-6">
      <PageHeader
        title="가계부"
        description={data && periodLabel(data.period)}
        actions={
          <>
            <Button
              type="button"
              variant="outline"
              render={<Link to="/ledger/transactions" />}
            >
              내역 보기
            </Button>
            <Button type="button" onClick={openTransactionModal}>
              <Plus className="size-4" />
              입력 <kbd className="ml-1 text-[11px] opacity-70">N</kbd>
            </Button>
          </>
        }
      />

      {isPending && <LoadingText />}
      {isError && (
        <Alert variant="destructive">가계부를 불러오지 못했어요.</Alert>
      )}

      {data && (
        <>
          <OverdueAlert items={data.upcoming} />

          <div className="grid gap-4 md:grid-cols-2">
            <SpendingCard data={data} budgetTotal={budget?.totalAmount ?? 0} />
            <CashflowCard data={data} />
          </div>

          <div className="grid items-start gap-4 md:grid-cols-[1.35fr_1fr]">
            <UpcomingCard items={data.upcoming} />
            <div className="flex flex-col gap-4">
              <NetWorthCard netWorth={data.netWorth} />
              <IncomeCard amount={data.income.amount} />
              <TodoCard todo={data.todo} />
            </div>
          </div>

          {/*
            빠른 입력 칩. 한 번 누르면 오늘 날짜로 기록되고, 누를수록 위로 올라온다.
          */}
          {templates && templates.length > 0 && (
            <section className="flex flex-col gap-2">
              <h2 className="text-[13px] font-semibold">빠른 입력</h2>
              <div className="flex flex-wrap gap-2">
                {templates.slice(0, 6).map((template) => (
                  <button
                    key={template.id}
                    type="button"
                    disabled={apply.isPending}
                    onClick={() => apply.mutate(template.id)}
                    className="border-border hover:bg-muted flex h-9 items-center gap-2 rounded-lg border px-3 text-[13px] transition-colors disabled:opacity-50"
                  >
                    <span>{template.name}</span>
                    <span className="text-muted-foreground tabular-nums">
                      {formatAmount(template.amount)}
                    </span>
                  </button>
                ))}
              </div>
            </section>
          )}

          <section className="flex flex-col gap-2">
            <h2 className="text-[13px] font-semibold">바로 가기</h2>
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                variant="outline"
                render={<Link to="/ledger/upcoming" />}
              >
                예정
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
              {/* 카드 명세서를 보며 몰아 적을 때 쓴다. 파일로 못 받는 경우가 실제로 많다. */}
              <Button
                type="button"
                variant="outline"
                render={<Link to="/ledger/transactions/bulk" />}
              >
                <Rows3 className="size-4" />
                여러 건 적기
              </Button>
            </div>
          </section>
        </>
      )}
    </div>
  );
}

/**
 * 좌 「이번 달 소비」 — <b>쓴 날 기준 · 카드 대금 제외</b>.
 *
 * <p>예산 게이지는 <b>2단</b>이다. 확정분만 칠하면 「아직 절반 남았네」 하다가 25일에
 * 고정비가 빠지고 놀란다 — 아직 안 썼지만 나갈 게 확정된 돈이 어디까지 차지하는지 보여야 한다.
 */
function SpendingCard({
  data,
  budgetTotal,
}: {
  data: LedgerDashboard;
  budgetTotal: number;
}) {
  const { spent, scheduled, estimate } = data.spending;
  const widths = gaugeWidths(spent, scheduled, budgetTotal);

  return (
    <section className="bg-card ring-foreground/10 flex flex-col gap-3 rounded-xl p-5 ring-1">
      <header className="flex flex-col">
        <h2 className="text-sm font-semibold">이번 달 소비</h2>
        <p className="text-muted-foreground text-[13px]">
          쓴 날 기준 · 카드 대금 제외
        </p>
      </header>

      <dl className="flex flex-col gap-1 text-sm">
        <Row label="이미 쓴 돈" value={formatAmount(spent)} />
        <Row label="앞으로 쓸 돈" value={formatAmount(scheduled)} muted />
      </dl>

      <div className="border-border border-t pt-3">
        <div className="flex items-baseline justify-between">
          <span className="text-[13px]">이번 달 예상</span>
          <span className="text-[22px]/[1.15] font-semibold tabular-nums">
            {formatAmount(estimate)}
            {budgetTotal > 0 && (
              <span className="text-muted-foreground text-[13px] font-normal">
                {" "}
                / {formatAmount(budgetTotal)}
              </span>
            )}
          </span>
        </div>

        {budgetTotal > 0 && (
          <>
            <div
              className="bg-muted mt-2 flex h-2.5 overflow-hidden rounded-full"
              role="img"
              aria-label={`예산 ${formatAmount(budgetTotal)} 중 확정 ${formatAmount(spent)}, 예정 ${formatAmount(scheduled)}`}
            >
              <span
                className="bg-primary h-full"
                style={{ width: `${widths.spent}%` }}
              />
              <span
                className="h-full bg-[color-mix(in_oklab,var(--primary)_32%,var(--muted))]"
                style={{ width: `${widths.scheduled}%` }}
              />
            </div>
            <div className="text-muted-foreground mt-1.5 flex flex-wrap items-center justify-between gap-2 text-[13px]">
              <span>
                확정 {Math.round(widths.spent)}% · 예정{" "}
                {Math.round(widths.scheduled)}%
              </span>
              <span className="tabular-nums">
                남은 예산 {formatBalance(budgetTotal - estimate)}
              </span>
            </div>
          </>
        )}
      </div>
    </section>
  );
}

/**
 * 우 「통장에서 나갈 돈」 — <b>출금 시점 기준 · 이체 포함</b>.
 *
 * <p>각주가 중요하다: <b>예정 거래는 잔액을 바꾸지 않는다.</b> 이 불변 조건이 깨지면
 * 월말 예상 잔액이 현재 잔액과 같아져 아무 말도 하지 않게 된다.
 */
function CashflowCard({ data }: { data: LedgerDashboard }) {
  const { balance, remainingOutflow, monthEndBalance, minBalance } =
    data.cashflow;

  return (
    <section className="bg-card ring-foreground/10 flex flex-col gap-3 rounded-xl p-5 ring-1">
      <header className="flex flex-col">
        <h2 className="text-sm font-semibold">통장에서 나갈 돈</h2>
        <p className="text-muted-foreground text-[13px]">
          출금 시점 기준 · 이체 포함
        </p>
      </header>

      <div className="flex items-baseline justify-between text-sm">
        <span>남은 예정 출금</span>
        <span className="tabular-nums">{formatAmount(remainingOutflow)}</span>
      </div>

      <ul className="border-border text-muted-foreground flex flex-col gap-1 border-l-2 pl-2.5 text-[13px]">
        {data.upcoming.slice(0, 3).map((item) => (
          <li key={`${item.kind}-${item.date}-${item.amount}`}>
            <span className="flex items-center justify-between gap-2">
              <span className="truncate">
                {item.title ?? UPCOMING_KIND_LABELS[item.kind]}{" "}
                {item.date.slice(5).replace("-", "/")}
              </span>
              <span className="tabular-nums">{formatAmount(item.amount)}</span>
            </span>
          </li>
        ))}
        {data.upcoming.length === 0 && <li>남은 예정 출금이 없어요</li>}
      </ul>

      <div className="border-border border-t pt-3">
        <div className="flex items-baseline justify-between">
          <span className="text-[13px]">월말 예상 잔액</span>
          <span
            className={cn(
              "text-[22px]/[1.15] font-semibold tabular-nums",
              balanceToneClass(monthEndBalance),
            )}
          >
            {formatBalance(monthEndBalance)}
          </span>
        </div>
        <p className="text-muted-foreground mt-1.5 text-[13px]">
          현재 잔액 {formatAmount(balance)} {MINUS} 남은 예정 출금{" "}
          {formatAmount(remainingOutflow)}. 예정 거래는 잔액을 바꾸지 않습니다.
        </p>
        {minBalance.reason && (
          <p className="text-muted-foreground mt-1 text-[13px]">
            가장 낮은 지점 {formatBalance(minBalance.amount)} ·{" "}
            {minBalance.date.slice(5).replace("-", "/")} 「{minBalance.reason}」
            직후
          </p>
        )}
      </div>
    </section>
  );
}

function UpcomingCard({ items }: { items: UpcomingItem[] }) {
  return (
    <section className="bg-card ring-foreground/10 flex flex-col gap-2 rounded-xl p-5 ring-1">
      <header className="flex items-center justify-between">
        <h2 className="text-sm font-semibold">다가오는 결제</h2>
        <Button
          type="button"
          variant="ghost"
          size="xs"
          render={<Link to="/ledger/upcoming" />}
        >
          전체
          <ArrowRight className="size-3.5" />
        </Button>
      </header>
      {items.length === 0 ? (
        <p className="text-muted-foreground text-[13px]">
          앞으로 30일 안에 나갈 돈이 없어요.
        </p>
      ) : (
        <ul className="flex flex-col">
          {items.map((item) => (
            <li
              key={`${item.kind}-${item.date}-${item.amount}`}
              className="grid grid-cols-[56px_minmax(0,1fr)_auto] items-center gap-2 py-1.5"
            >
              <span className="text-muted-foreground text-[13px] tabular-nums">
                {formatDday(item.dday)}
              </span>
              <span className="flex min-w-0 items-center gap-2">
                <span className="truncate text-sm">
                  {item.title ?? UPCOMING_KIND_LABELS[item.kind]}
                </span>
                {item.isTransfer && <Badge variant="outline">이체</Badge>}
                {item.overdue && <Badge variant="destructive">미납</Badge>}
              </span>
              <span className="text-right text-sm tabular-nums">
                {formatAmount(item.amount)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/**
 * 자산 요약 — <b>3줄</b>이다.
 *
 * <p>순자산만 크게 보여주지 않는다. 부채가 얼마인지 보이지 않으면 순자산이 좋아 보이는
 * 이유가 자산이 늘어서인지 빚을 안 갚아서인지 알 수 없다.
 */
function NetWorthCard({ netWorth }: { netWorth: LedgerDashboard["netWorth"] }) {
  return (
    <section className="bg-card ring-foreground/10 flex flex-col gap-1.5 rounded-xl p-5 ring-1">
      <h2 className="text-sm font-semibold">자산 요약</h2>
      <Row label="총자산" value={formatAmount(netWorth.totalAssets)} />
      <Row
        label="부채"
        value={`${MINUS}${formatAmount(netWorth.liabilities)}`}
        tone="text-destructive"
      />
      <Row
        label="순자산"
        value={formatBalance(netWorth.netWorth)}
        strong
        tone={balanceToneClass(netWorth.netWorth)}
      />
      <p className="text-muted-foreground text-[13px]">
        카드 미결제와 할부 잔여를 부채로 반영합니다.
      </p>
    </section>
  );
}

function IncomeCard({ amount }: { amount: number }) {
  return (
    <section className="bg-card ring-foreground/10 flex flex-col gap-1 rounded-xl p-5 ring-1">
      <span className="text-muted-foreground text-[13px]">이번 달 수입</span>
      <span className="text-success text-[22px]/[1.15] font-semibold tabular-nums">
        {formatAmount(amount)}
      </span>
    </section>
  );
}

/**
 * 정리할 것.
 *
 * <p>0건일 때는 그 줄을 그리지 않는다 — 할 일이 없다는 사실이 할 일처럼 보이면 안 된다.
 */
function TodoCard({ todo }: { todo: LedgerDashboard["todo"] }) {
  if (todo.uncategorized === 0 && todo.overdue === 0) {
    return null;
  }
  return (
    <section className="bg-card ring-foreground/10 flex flex-col gap-2 rounded-xl p-5 ring-1">
      <h2 className="text-sm font-semibold">정리할 것</h2>
      <div className="flex flex-wrap items-center gap-2">
        {todo.uncategorized > 0 && (
          <Badge variant="secondary">미분류 {todo.uncategorized}건</Badge>
        )}
        {todo.overdue > 0 && (
          <Badge variant="destructive">미납 {todo.overdue}건</Badge>
        )}
      </div>
      {todo.uncategorized > 0 && (
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="self-start"
          render={<Link to="/ledger/transactions?uncategorized=1" />}
        >
          <ReceiptText className="size-3.5" />
          정리하기
        </Button>
      )}
    </section>
  );
}

function Row({
  label,
  value,
  muted,
  strong,
  tone,
}: {
  label: string;
  value: string;
  muted?: boolean;
  strong?: boolean;
  tone?: string;
}) {
  return (
    <div className="flex items-baseline justify-between text-sm">
      <span className={cn(muted && "text-muted-foreground")}>{label}</span>
      <span
        className={cn(
          "tabular-nums",
          muted && "text-muted-foreground",
          strong && "font-semibold",
          tone,
        )}
      >
        {value}
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
