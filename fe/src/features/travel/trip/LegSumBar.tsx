import { Check, Info } from "lucide-react";

import type { LegPlan } from "@/features/travel/lib/legPlan";
import { cn } from "@/lib/utils";

/**
 * 합계 바 — `합계 10일 / 기간 10일` + 무슨 일이 일어날지.
 *
 * <p><b>저장을 막지 않는다.</b> 여행을 짜는 중간 상태는 대부분 불일치다 — 막으면 도시를
 * 하나 추가할 때마다 기간을 먼저 늘려야 한다. 대신 어긋났을 때 서버가 무엇을 할지 미리
 * 말해 주는 것이 이 줄의 일이다.
 */
export function LegSumBar({ plan }: { plan: LegPlan }) {
  const warn = plan.verdict !== "exact";

  return (
    <div
      className={cn(
        "flex items-center gap-2 rounded-lg px-3 py-2 text-[13px]",
        warn ? "bg-warning/16" : "bg-muted",
      )}
    >
      {warn ? (
        <Info className="size-[15px] shrink-0" />
      ) : (
        <Check className="size-[15px] shrink-0" />
      )}
      <span className="tabular-nums">
        합계 {plan.sum}일 / 기간 {plan.period}일
      </span>
      <span className="text-muted-foreground">· {describe(plan)}</span>
    </div>
  );
}

function describe(plan: LegPlan): string {
  if (plan.verdict === "exact") return "딱 맞아요";
  if (plan.verdict === "over") return `${plan.diff}일 초과 · 뒤 구간이 잘려요`;
  return `${plan.diff}일 남음 · 마지막 구간 도시를 이어써요`;
}
