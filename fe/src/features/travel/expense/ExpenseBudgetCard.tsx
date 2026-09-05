import { Button } from "@/components/ui/button";
import { gaugeWidths } from "@/features/ledger/lib/balance";
import { formatAmount, formatCompactAmount } from "@/features/ledger/lib/money";

import type { TripExpenses } from "../api/expenses";

interface ExpenseBudgetCardProps {
  data: TripExpenses;
  onEditBudget: () => void;
  /** 오프라인이면 쓰기 입구를 전부 잠근다 — 헤더뿐 아니라 여기도(§6.2). */
  offline: boolean;
}

/**
 * 예산 카드(화면 §10.2). 이 카드가 답하는 것은 <b>「얼마 쓰기로 했고 지금 얼마 썼나」</b>다.
 *
 * <p>상태가 셋이고 각각 다른 것을 보여준다.
 *
 * <ul>
 *   <li><b>예산 없음</b> — 게이지도 하루 사용액도 각주도 없다. 0으로 꾸미지 않는다(§5.3)
 *   <li><b>여행 중·출발 전</b> — 두 겹 게이지 + 「남은 N일 · 하루 쓸 수 있는 돈」
 *   <li><b>다녀온 뒤</b> — 같은 자리가 「총 82.3만 / 예산 80만 · 2.3만 초과」 + 하루 평균
 * </ul>
 */
export function ExpenseBudgetCard({
  data,
  onEditBudget,
  offline,
}: ExpenseBudgetCardProps) {
  const { budget, totals } = data;
  const completed = data.status === "COMPLETED";

  // 예산을 안 정했으면 한 줄로 끝낸다. 게이지를 0으로 그리면 「아직 안 정했다」와
  // 「0원으로 정했다」가 같아 보인다.
  if (!budget) {
    return (
      <section className="bg-card ring-foreground/10 flex flex-wrap items-center gap-3 rounded-xl p-5 ring-1">
        <p className="text-sm">
          아직 예산을 정하지 않았어요. 이번 여행에{" "}
          <b className="tabular-nums">{formatCompactAmount(totals.spent)}</b>{" "}
          썼습니다.
        </p>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="ml-auto"
          disabled={offline}
          onClick={onEditBudget}
        >
          예산 정하기
        </Button>
      </section>
    );
  }

  // 같은 계산을 두 곳에 적으면 두 화면이 다른 폭을 그린다 — 가계부의 것을 그대로 쓴다.
  const widths = gaugeWidths(budget.spent, budget.scheduled, budget.amount);
  const spentPercent = Math.round((budget.spent / budget.amount) * 100);
  const scheduledPercent = Math.round((budget.scheduled / budget.amount) * 100);
  const over = budget.remaining < 0;

  return (
    <section className="bg-card ring-foreground/10 flex flex-col gap-3 rounded-xl p-5 ring-1">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <p className="text-[22px]/[1.15] font-semibold tabular-nums">
          {completed ? (
            <>
              총 {formatCompactAmount(totals.spent)}
              <span className="text-muted-foreground text-sm font-normal">
                {" / 예산 "}
                {formatCompactAmount(budget.amount)}
                {over && ` · ${formatCompactAmount(-budget.remaining)} 초과`}
              </span>
            </>
          ) : (
            <>
              {formatCompactAmount(budget.amount)} 중{" "}
              {formatCompactAmount(budget.spent)}
            </>
          )}
        </p>
        {!completed && (
          <p className="text-muted-foreground text-[13px] tabular-nums">
            쓴 돈 {spentPercent}% · 예정 {scheduledPercent}%
          </p>
        )}
      </div>

      <div
        className="bg-muted flex h-2.5 overflow-hidden rounded-full"
        role="img"
        aria-label={`예산 ${formatAmount(budget.amount)}원 중 쓴 돈 ${formatAmount(
          budget.spent,
        )}원, 예정 ${formatAmount(budget.scheduled)}원`}
      >
        <span
          className="bg-primary block"
          style={{ width: `${widths.spent}%` }}
        />
        {/* 2층은 확정분 위에 이어 붙는다 — 25일에 고정비가 빠지고 놀라지 않도록. */}
        <span
          className="block"
          style={{
            width: `${widths.scheduled}%`,
            background: "color-mix(in oklab, var(--primary) 32%, var(--muted))",
          }}
        />
      </div>

      <div className="flex items-baseline justify-between border-t pt-3">
        {completed ? (
          <>
            <span className="text-[13px]">총 {totals.days}일 · 하루 평균</span>
            <span className="font-semibold tabular-nums">
              {totals.dailyAverage === null
                ? "—"
                : formatCompactAmount(totals.dailyAverage)}
            </span>
          </>
        ) : (
          <>
            <span className="text-[13px]">
              남은 {budget.daysLeft}일 · 하루 쓸 수 있는 돈
            </span>
            <span className="font-semibold tabular-nums">
              {budget.dailyAllowance === null
                ? "—"
                : formatCompactAmount(budget.dailyAllowance)}
            </span>
          </>
        )}
      </div>

      {/*
        각주가 카드에서 가장 중요한 한 줄일 수 있다 — 「카드값 200만이 또 나갔다」는
        이중 계산을 사람이 머릿속에서 하지 않도록 여기서 미리 답한다(§4.2).
      */}
      {!completed && (
        <p className="text-muted-foreground text-[13px]">
          남은 {formatCompactAmount(budget.remaining)} ÷ 남은 {budget.daysLeft}
          일. <b>카드 대금 납부는 여기 들어가지 않아요.</b>
        </p>
      )}
    </section>
  );
}
