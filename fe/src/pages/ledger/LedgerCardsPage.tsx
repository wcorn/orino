import { ArrowRight, CreditCard } from "lucide-react";
import { Link } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingText } from "@/components/ui/loading-text";
import type { CardView, UsageGoalView } from "@/features/ledger/api/ledger";
import { useLedgerCards } from "@/features/ledger/hooks/useLedgerQueries";
import { formatAmount, MINUS } from "@/features/ledger/lib/money";
import { cycleLabel, limitUsage } from "@/features/ledger/lib/statement";
import { cn } from "@/lib/utils";

/**
 * 카드 목록 `/ledger/cards`.
 *
 * <p>PRD에 없던 경로다. 사이드바가 가리킬 곳이 필요해 신설했다([D-11]) — 청구서는
 * `/ledger/cards/:id/statements`라 카드를 고르는 자리가 없으면 들어갈 문이 없다.
 *
 * <p>여기 나오는 금액은 전부 <b>부채</b>다. 카드에는 잔액이 없다 — 미결제 사용액이 있을 뿐이고,
 * 그래서 숫자 앞에 마이너스가 붙는다.
 */
export function LedgerCardsPage() {
  const { data, isPending, isError } = useLedgerCards();

  return (
    <div className="mx-auto flex max-w-[880px] flex-col gap-4">
      <PageHeader
        title="카드 청구서"
        description={
          data &&
          data.installmentOutstanding > 0 &&
          `할부 잔여 원금 ${formatAmount(data.installmentOutstanding)} — 아직 청구되지 않은 회차도 이미 갚기로 한 돈입니다`
        }
      />

      {isPending && <LoadingText />}
      {isError && (
        <Alert variant="destructive">카드를 불러오지 못했어요.</Alert>
      )}

      {data && data.cards.length === 0 && (
        <EmptyState className="min-h-[30svh]">
          <p className="text-muted-foreground text-sm">
            등록된 신용카드가 없어요.
          </p>
          <Button
            type="button"
            variant="outline"
            render={<Link to="/ledger/assets" />}
          >
            자산에서 카드 만들기
          </Button>
        </EmptyState>
      )}

      {data && data.cards.length > 0 && (
        <ul className="flex flex-col gap-3">
          {data.cards.map((card) => (
            <li key={card.id}>
              <CardRow card={card} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function CardRow({ card }: { card: CardView }) {
  const usage = limitUsage(card.unpaidAmount, card.creditLimit);

  return (
    <Link
      to={`/ledger/cards/${card.id}/statements`}
      className="bg-card ring-foreground/10 hover:bg-muted/40 flex flex-col gap-2 rounded-xl p-5 ring-1 transition-colors"
    >
      <div className="flex items-start justify-between gap-3">
        <span className="flex min-w-0 items-center gap-2">
          <CreditCard className="text-muted-foreground size-4 shrink-0" />
          <span className="truncate text-sm font-semibold">{card.name}</span>
          {card.accountLast4 && (
            <span className="text-muted-foreground text-[13px] tabular-nums">
              ···{card.accountLast4}
            </span>
          )}
          {/* 사이클이 없으면 청구서가 만들어지지 않는다. 오류가 아니라 상태다. */}
          {!card.hasCycle && <Badge variant="warning">사이클 미등록</Badge>}
          {card.currentStatement?.overdue && (
            <Badge variant="destructive">미납</Badge>
          )}
        </span>
        <span className="text-destructive shrink-0 text-sm font-semibold tabular-nums">
          {MINUS}
          {formatAmount(card.unpaidAmount)}
        </span>
      </div>

      <p className="text-muted-foreground text-[13px]">
        {cycleLabel(card.cycleStartDay, card.cycleCloseDay, card.paymentDay)}
        {card.paymentAssetName && ` · 결제 계좌 ${card.paymentAssetName}`}
      </p>

      {usage !== null && (
        <div className="flex items-center gap-2">
          <span className="bg-muted h-1.5 flex-1 overflow-hidden rounded-full">
            <span
              className="bg-primary block h-full"
              style={{ width: `${usage}%` }}
            />
          </span>
          <span className="text-muted-foreground text-[13px] tabular-nums">
            한도 {formatAmount(card.creditLimit as number)} 중{" "}
            {Math.round(usage)}%
          </span>
        </div>
      )}

      {/* 실적은 카드마다 기준이 다르다 — 배지가 어느 기준인지 말한다(§7.6). */}
      {card.usageGoal && <UsageGoal goal={card.usageGoal} />}

      {card.currentStatement && (
        <span className="text-muted-foreground flex items-center gap-1 text-[13px]">
          {card.currentStatement.paymentDate}에{" "}
          {formatAmount(card.currentStatement.breakdown.remaining)} 예정
          <ArrowRight className="size-3.5" />
        </span>
      )}
    </Link>
  );
}

/**
 * 카드 실적 진행(`LDG-037`).
 *
 * <p><b>기준을 배지로 적는다.</b> 승인이냐 청구냐는 카드사·상품마다 다르고, 어느 기준인지
 * 모르면 채운 금액이 맞는지 사람이 확인할 방법이 없다.
 *
 * <p>조건을 안 걸어 둔 카드는 이 블록 자체가 없다 — 0%로 그리면 「하나도 못 채웠다」로
 * 읽히는데 사실은 「조건이 없다」다.
 */
function UsageGoal({ goal }: { goal: UsageGoalView }) {
  const filled = Math.min((goal.counted / goal.goalAmount) * 100, 100);
  return (
    <div className="flex flex-col gap-1.5">
      <span className="flex flex-wrap items-center justify-between gap-2 text-[13px]">
        <span className="flex items-center gap-2">
          실적
          <Badge variant="outline">
            {goal.basis === "APPROVAL" ? "승인 기준" : "청구 기준"}
          </Badge>
        </span>
        <span className="tabular-nums">
          {formatAmount(goal.counted)} / {formatAmount(goal.goalAmount)}
        </span>
      </span>
      <span className="bg-muted h-1.5 overflow-hidden rounded-full">
        <span
          className={cn(
            "block h-full rounded-full",
            goal.achieved ? "bg-success" : "bg-primary",
          )}
          style={{ width: `${filled}%` }}
        />
      </span>
      <span className="text-muted-foreground text-[13px]">
        {goal.achieved
          ? "이번 달 조건을 채웠어요"
          : `${formatAmount(goal.remaining)}원 더 쓰면 다음 달 조건 충족`}
      </span>
    </div>
  );
}
