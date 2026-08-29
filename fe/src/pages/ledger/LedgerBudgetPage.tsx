import { Plane } from "lucide-react";
import { useState } from "react";

import { PageHeader } from "@/components/PageHeader";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { LoadingText } from "@/components/ui/loading-text";
import type {
  BudgetCategoryProgress,
  BudgetResponse,
} from "@/features/ledger/api/ledger";
import { usePutBudget } from "@/features/ledger/hooks/useLedgerMutations";
import { useLedgerBudget } from "@/features/ledger/hooks/useLedgerQueries";
import { gaugeWidths } from "@/features/ledger/lib/balance";
import {
  formatAmount,
  formatBalance,
  MINUS,
} from "@/features/ledger/lib/money";
import { budgetTone } from "@/features/ledger/lib/recurrence";
import { cn } from "@/lib/utils";

/**
 * 예산 `/ledger/budget`.
 *
 * <p>게이지는 <b>2단</b>이다 — 확정분만 칠하면 「아직 절반 남았네」 하다가 25일에 고정비가
 * 나가고 놀란다(확정 명세 §8.2).
 *
 * <p><b>고정비를 미리 뺀다.</b> 매달 나가기로 돼 있는 돈까지 쓸 수 있다고 착각하지 않게
 * 「쓸 수 있는 돈」만 남긴다(§9).
 */
export function LedgerBudgetPage() {
  const { data, isPending, isError } = useLedgerBudget();
  const [editing, setEditing] = useState(false);

  return (
    <div className="mx-auto flex max-w-[880px] flex-col gap-4">
      <PageHeader
        title="예산"
        description={data && `${data.periodStart} ~ ${data.periodEnd}`}
        actions={
          data && (
            <Button
              type="button"
              variant="outline"
              onClick={() => setEditing((value) => !value)}
            >
              {editing ? "닫기" : "예산 정하기"}
            </Button>
          )
        }
      />

      {isPending && <LoadingText />}
      {isError && (
        <Alert variant="destructive">예산을 불러오지 못했어요.</Alert>
      )}

      {data && (
        <>
          {editing && (
            <BudgetEditor budget={data} onDone={() => setEditing(false)} />
          )}

          <div className="grid items-start gap-4 md:grid-cols-2">
            <ProgressCard budget={data} />
            <SpendableCard budget={data} />
          </div>

          {data.categories.length > 0 && (
            <section className="flex flex-col gap-2">
              <h2 className="text-[13px] font-semibold">카테고리</h2>
              <ul className="flex flex-col gap-3">
                {data.categories.map((category) => (
                  <li key={category.categoryId ?? "none"}>
                    <CategoryBar category={category} />
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}
    </div>
  );
}

function ProgressCard({ budget }: { budget: BudgetResponse }) {
  const widths = gaugeWidths(
    budget.spent,
    budget.scheduled,
    budget.totalAmount,
  );

  return (
    <section className="bg-card ring-foreground/10 flex flex-col gap-3 rounded-xl p-5 ring-1">
      <header className="flex flex-col">
        <h2 className="text-sm font-semibold">월 예산 진행</h2>
        <p className="text-muted-foreground text-[13px]">
          확정은 진하게, 나갈 게 확정된 예정은 연하게
        </p>
      </header>

      {budget.totalAmount === 0 ? (
        <p className="text-muted-foreground text-[13px]">
          아직 예산을 정하지 않았어요. 이번 구간에 {formatAmount(budget.spent)}{" "}
          썼습니다.
        </p>
      ) : (
        <>
          <div
            className="bg-muted flex h-2.5 overflow-hidden rounded-full"
            role="img"
            aria-label={`예산 ${formatAmount(budget.totalAmount)} 중 확정 ${formatAmount(budget.spent)}, 예정 ${formatAmount(budget.scheduled)}`}
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
          <div className="text-muted-foreground flex flex-wrap items-center justify-between gap-2 text-[13px]">
            <span>
              확정 {Math.round(widths.spent)}% · 예정{" "}
              {Math.round(widths.scheduled)}%
            </span>
            <span className="tabular-nums">
              남은 예산 {formatBalance(budget.remaining)}
            </span>
          </div>

          <div className="border-border flex items-baseline justify-between border-t pt-3">
            <span className="text-[13px]">하루 사용 가능액</span>
            <span className="text-[22px]/[1.15] font-semibold tabular-nums">
              {formatAmount(budget.dailyAllowance)}
            </span>
          </div>
          <p className="text-muted-foreground text-[13px]">
            남은 {formatBalance(budget.remaining)} ÷ 남은 {budget.daysLeft}일.
            나갈 게 확정된 예정까지 빼고 나눕니다.
          </p>
        </>
      )}
    </section>
  );
}

/** 고정비 자동 반영 — 매달 나가기로 된 돈까지 쓸 수 있다고 착각하지 않게. */
function SpendableCard({ budget }: { budget: BudgetResponse }) {
  return (
    <section className="bg-card ring-foreground/10 flex flex-col gap-3 rounded-xl p-5 ring-1">
      <h2 className="text-sm font-semibold">쓸 수 있는 돈</h2>
      <dl className="flex flex-col gap-1 text-sm">
        <Row label="월 예산" value={formatAmount(budget.totalAmount)} />
        <Row
          label="정기 항목 합계"
          value={`${MINUS}${formatAmount(budget.fixedCostTotal)}`}
          muted
        />
      </dl>
      <div className="border-border flex items-baseline justify-between border-t pt-3">
        <span className="text-[13px]">쓸 수 있는 돈</span>
        <span className="text-[22px]/[1.15] font-semibold tabular-nums">
          {formatBalance(budget.spendable)}
        </span>
      </div>

      <Alert variant="info">
        <Plane />
        <AlertTitle>여행 경비 예산은 월 예산과 분리됩니다</AlertTitle>
        <AlertDescription>
          <p>월 예산이 여행 때문에 항상 터지지 않게 따로 셉니다.</p>
        </AlertDescription>
      </Alert>
    </section>
  );
}

function CategoryBar({ category }: { category: BudgetCategoryProgress }) {
  const used = category.spent + category.scheduled;
  const tone = budgetTone(used, category.amount);
  const widths = gaugeWidths(
    category.spent,
    category.scheduled,
    category.amount,
  );

  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-center justify-between gap-2 text-sm">
        <span className="flex items-center gap-2">
          <span className="truncate">{category.name}</span>
          {/* 초과는 경고가 아니라 사실이다. 색으로만 말하면 지나친다. */}
          {tone === "over" && <Badge variant="destructive">초과</Badge>}
        </span>
        <span className="text-muted-foreground tabular-nums">
          {formatAmount(used)}
          {category.amount > 0 && ` / ${formatAmount(category.amount)}`}
        </span>
      </div>
      {category.amount > 0 && (
        <span className="bg-muted flex h-1.5 overflow-hidden rounded-full">
          <span
            className={cn(
              "h-full",
              tone === "over"
                ? "bg-destructive"
                : tone === "near"
                  ? "bg-warning"
                  : "bg-primary",
            )}
            style={{ width: `${widths.spent}%` }}
          />
          <span
            className="h-full bg-[color-mix(in_oklab,var(--primary)_32%,var(--muted))]"
            style={{ width: `${widths.scheduled}%` }}
          />
        </span>
      )}
    </div>
  );
}

/** 통째로 갈아 끼운다 — 보낸 카테고리 목록이 곧 그 달의 전부다. */
function BudgetEditor({
  budget,
  onDone,
}: {
  budget: BudgetResponse;
  onDone: () => void;
}) {
  const [total, setTotal] = useState(String(budget.totalAmount));
  const put = usePutBudget();

  return (
    <section className="bg-card ring-foreground/10 flex flex-wrap items-end gap-3 rounded-xl p-5 ring-1">
      <div className="flex flex-1 flex-col gap-1.5">
        <Label htmlFor="budget-total">월 예산</Label>
        <Input
          id="budget-total"
          inputMode="numeric"
          value={total}
          onChange={(event) => setTotal(event.target.value)}
        />
      </div>
      <Button
        type="button"
        disabled={put.isPending}
        onClick={() =>
          put.mutate(
            {
              period: budget.period,
              body: {
                totalAmount: Number(total.replace(/[^0-9]/g, "")) || 0,
                categories: budget.categories
                  .filter((row) => row.categoryId !== null && row.amount > 0)
                  .map((row) => ({
                    categoryId: row.categoryId as number,
                    amount: row.amount,
                  })),
              },
            },
            { onSuccess: onDone },
          )
        }
      >
        저장
      </Button>
    </section>
  );
}

function Row({
  label,
  value,
  muted,
}: {
  label: string;
  value: string;
  muted?: boolean;
}) {
  return (
    <div className="flex items-baseline justify-between">
      <span className={cn(muted && "text-muted-foreground")}>{label}</span>
      <span className={cn("tabular-nums", muted && "text-muted-foreground")}>
        {value}
      </span>
    </div>
  );
}
