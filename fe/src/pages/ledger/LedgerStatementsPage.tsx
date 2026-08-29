import { Check } from "lucide-react";
import { useState } from "react";
import { useParams } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingText } from "@/components/ui/loading-text";
import type { CardView, StatementView } from "@/features/ledger/api/ledger";
import { StatementPayModal } from "@/features/ledger/components/StatementPayModal";
import {
  useLedgerAssets,
  useLedgerCards,
  useLedgerStatements,
  useStatementTransactions,
} from "@/features/ledger/hooks/useLedgerQueries";
import {
  formatAmount,
  formatDateHeader,
  MINUS,
} from "@/features/ledger/lib/money";
import {
  breakdownRows,
  cycleLabel,
  STATEMENT_STATUS_LABELS,
  STATEMENT_STEPS,
} from "@/features/ledger/lib/statement";
import { cn } from "@/lib/utils";

/**
 * 카드 청구서 `/ledger/cards/:id/statements` — <b>이 모듈의 심장</b>.
 *
 * <p>여기서 가장 중요한 것은 합계가 아니라 <b>산식</b>이다. 카드사 앱과 금액이 다를 때
 * 어디가 다른지 알 수 있어야 하고, 그러려면 일곱 항목이 그대로 보여야 한다(확정 명세 §7.4).
 *
 * <p>화면은 산식을 <b>다시 계산하지 않는다</b> — 서버가 준 항목을 줄로 펼 뿐이다(D-13).
 * 두 곳에서 더하면 그중 하나만 틀려도 조용히 어긋난다.
 */
export function LedgerStatementsPage() {
  const { cardId } = useParams();
  const id = Number(cardId);
  const { data: cards } = useLedgerCards();
  const { data: statements, isPending, isError } = useLedgerStatements(id);
  const { data: assetList } = useLedgerAssets();
  const [payTarget, setPayTarget] = useState<StatementView | null>(null);
  const [openStatementId, setOpenStatementId] = useState<number | null>(null);

  const card = cards?.cards.find((row) => row.id === id);
  const current = statements?.[0];
  const assets = (assetList?.groups ?? [])
    .flatMap((group) => group.assets)
    .filter((asset) => asset.balance !== null);

  return (
    <div className="mx-auto flex max-w-[880px] flex-col gap-4">
      <PageHeader
        title={card?.name ?? "카드 청구서"}
        description={card && describeCard(card)}
        actions={
          current &&
          current.breakdown.remaining > 0 && (
            <Button type="button" onClick={() => setPayTarget(current)}>
              결제 처리
            </Button>
          )
        }
      />

      {isPending && <LoadingText />}
      {isError && (
        <Alert variant="destructive">청구서를 불러오지 못했어요.</Alert>
      )}

      {statements && statements.length === 0 && (
        <EmptyState className="min-h-[30svh]">
          <p className="text-muted-foreground text-sm">
            아직 청구서가 없어요. 이 카드로 결제하면 그 순간 사이클에
            편입됩니다.
          </p>
        </EmptyState>
      )}

      {current && card && (
        <>
          {current.overdue && (
            <Alert variant="destructive">
              <AlertTitle>
                결제일 {current.paymentDate}이 지났는데 아직 안 냈어요
              </AlertTitle>
              <AlertDescription>
                <p>
                  미납은 저장된 표시가 아니라 판정입니다 — 결제 처리를 해야
                  사라져요.
                </p>
              </AlertDescription>
            </Alert>
          )}

          <StatsStrip card={card} statement={current} />
          <StatusStepper statement={current} />
          <BreakdownCard
            statement={current}
            open={openStatementId === current.id}
            onToggle={() =>
              setOpenStatementId(
                openStatementId === current.id ? null : current.id,
              )
            }
          />
        </>
      )}

      {statements && statements.length > 1 && (
        <section className="flex flex-col gap-2">
          <h2 className="text-[13px] font-semibold">지난 청구서</h2>
          <ul className="flex flex-col">
            {statements.slice(1).map((statement) => (
              <li
                key={statement.id}
                className="border-border grid grid-cols-[minmax(0,1fr)_auto_auto] items-center gap-3 border-b py-2.5 text-sm last:border-b-0"
              >
                <span className="truncate">
                  {statement.cycleStart} ~ {statement.cycleEnd}
                </span>
                <Badge
                  variant={
                    statement.status === "PAID" ? "secondary" : "outline"
                  }
                >
                  {STATEMENT_STATUS_LABELS[statement.status]}
                </Badge>
                <span className="text-right tabular-nums">
                  {formatAmount(statement.breakdown.billed)}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <StatementPayModal
        statement={payTarget}
        assets={assets}
        defaultAssetId={card?.paymentAssetId ?? null}
        onClose={() => setPayTarget(null)}
      />
    </div>
  );
}

/** 확정 명세 §7.7의 질문들에 답한다 — 「이 카드로 얼마 썼나」부터 「이월이 얼마나」까지. */
function StatsStrip({
  card,
  statement,
}: {
  card: CardView;
  statement: StatementView;
}) {
  return (
    <div className="grid grid-cols-[repeat(auto-fit,minmax(150px,1fr))] gap-3">
      <Stat
        label="이번 달 이 카드로"
        value={formatAmount(statement.breakdown.usage)}
      />
      <Stat
        label={`${statement.paymentDate}에 빠질 돈`}
        value={formatAmount(statement.breakdown.remaining)}
      />
      <Stat
        label="할부 회차"
        value={formatAmount(statement.breakdown.installment)}
      />
      {/* 이월은 따로 보여야 한다 — 합계에 섞이면 「왜 이렇게 많지」에 답할 수 없다. */}
      <Stat
        label="이월 잔액"
        value={formatAmount(statement.breakdown.carriedOver)}
      />
      <Stat
        label="미결제 (부채)"
        value={`${MINUS}${formatAmount(card.unpaidAmount)}`}
        tone="text-destructive"
      />
    </div>
  );
}

function Stat({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone?: string;
}) {
  return (
    <div className="bg-card ring-foreground/10 flex flex-col gap-1 rounded-xl p-4 ring-1">
      <span className="text-muted-foreground text-[13px]">{label}</span>
      <span
        className={cn(
          "text-[19px]/[1.2] font-semibold tracking-[-0.02em] tabular-nums",
          tone,
        )}
      >
        {value}
      </span>
    </div>
  );
}

/** 4단계. 돈이 나가는 순서 그대로다 — 지금 어디인지 한 눈에 보여야 다음 할 일이 정해진다. */
function StatusStepper({ statement }: { statement: StatementView }) {
  const currentIndex = STATEMENT_STEPS.findIndex(
    (step) => step.value === statement.status,
  );
  return (
    <ol className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[13px]">
      {STATEMENT_STEPS.map((step, index) => (
        <li key={step.value} className="flex items-center gap-1.5">
          {index > 0 && <span className="text-muted-foreground">›</span>}
          <span
            className={cn(
              index === currentIndex
                ? "text-primary font-semibold"
                : "text-muted-foreground",
            )}
          >
            {index < currentIndex && <Check className="mr-0.5 inline size-3" />}
            {step.label}
          </span>
        </li>
      ))}
    </ol>
  );
}

/** 산식을 그대로. 합계만 주면 카드사 앱과 다를 때 어디가 다른지 알 방법이 없다. */
function BreakdownCard({
  statement,
  open,
  onToggle,
}: {
  statement: StatementView;
  open: boolean;
  onToggle: () => void;
}) {
  const { data: transactions } = useStatementTransactions(
    open ? statement.id : null,
  );

  return (
    <section className="bg-card ring-foreground/10 flex flex-col rounded-xl ring-1">
      <ul className="flex flex-col p-5 pb-0">
        {breakdownRows(statement.breakdown).map((row) => (
          <li
            key={row.key}
            className="flex items-baseline justify-between py-1.5 text-sm"
          >
            {/* 부호는 금액 쪽에만 붙인다 — 이름 앞에도 붙이면 같은 말을 두 번 한다. */}
            <span className="text-muted-foreground">{row.label}</span>
            <span className="tabular-nums">
              {row.sign === "−" ? MINUS : ""}
              {formatAmount(row.amount)}
            </span>
          </li>
        ))}
      </ul>

      <div className="bg-muted mt-3 flex items-baseline justify-between rounded-b-xl px-5 py-4">
        <span className="text-sm font-medium">청구액</span>
        <span className="text-2xl font-semibold tabular-nums">
          {formatAmount(statement.breakdown.billed)}
        </span>
      </div>

      {statement.breakdown.paid > 0 && (
        <p className="text-muted-foreground px-5 py-2 text-[13px]">
          {statement.paidOn && `${formatDateHeader(statement.paidOn)}에 `}
          {formatAmount(statement.breakdown.paid)} 납부 · 남은{" "}
          {formatAmount(statement.breakdown.remaining)}
          {statement.carriedToStatementId !== null &&
            " — 남은 금액은 다음 청구서로 이월됐어요"}
        </p>
      )}

      <div className="px-5 pb-4">
        <Button type="button" variant="ghost" size="sm" onClick={onToggle}>
          {open ? "편입된 거래 접기" : "편입된 거래 보기"}
        </Button>
        {open && transactions && (
          <ul className="mt-2 flex flex-col">
            {transactions.map((transaction) => (
              <li
                key={transaction.id}
                className="border-border flex items-center justify-between gap-3 border-b py-1.5 text-[13px] last:border-b-0"
              >
                <span className="truncate">
                  {transaction.occurredOn} · {transaction.title ?? "제목 없음"}
                </span>
                <span className="tabular-nums">
                  {formatAmount(transaction.amount)}
                </span>
              </li>
            ))}
            {transactions.length === 0 && (
              <li className="text-muted-foreground py-1.5 text-[13px]">
                이 사이클에 편입된 거래가 없어요.
              </li>
            )}
          </ul>
        )}
      </div>
    </section>
  );
}

function describeCard(card: CardView): string {
  const parts = [
    cycleLabel(card.cycleStartDay, card.cycleCloseDay, card.paymentDay),
  ];
  if (card.paymentAssetName) {
    parts.push(`결제 계좌 ${card.paymentAssetName}`);
  }
  if (card.creditLimit) {
    parts.push(`한도 ${formatAmount(card.creditLimit)}`);
  }
  return parts.join(" · ");
}
