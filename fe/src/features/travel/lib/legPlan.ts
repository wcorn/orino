import { totalDays } from "./tripStatus";

/** 구간이 실제로 차지하는 날짜. 기간을 넘겨 잘린 구간은 `null`이다. */
export interface LegDates {
  startDate: string;
  endDate: string;
}

export type LegPlanVerdict = "exact" | "short" | "over";

export interface LegPlan {
  /** 구간별 날짜 범위. 잘린 구간은 `null` — 화면이 "잘려요"를 그 자리에 보여준다. */
  dates: (LegDates | null)[];
  /** 구간 일수 합계. */
  sum: number;
  /** 기간 일수(당일 포함). */
  period: number;
  verdict: LegPlanVerdict;
  /** 남거나 넘치는 일수. `exact`면 0. */
  diff: number;
}

/**
 * 구간(일수 목록)을 기간에 펴서 **각 구간이 어느 날짜를 차지하는지** 계산한다.
 * 서버가 저장할 때 하는 일(`LegExpander`)과 같은 규칙이라, 화면이 미리 같은 답을 보여준다.
 *
 * **합계와 기간이 달라도 막지 않는다.** 여행을 짜는 중간 상태가 대부분 불일치라, 막으면
 * 도시를 하나 추가할 때마다 기간을 먼저 늘려야 한다. 대신 무슨 일이 일어날지 미리 말해 준다.
 *
 * - 합계가 **모자라면** 남은 날짜가 마지막 구간 도시를 이어 쓴다 → 마지막 구간의 `endDate`가 늘어난다
 * - 합계가 **넘치면** 기간을 채운 시점에서 뒤 구간이 잘린다 → 그 구간들의 날짜가 `null`이다
 */
export function planLegDates(
  startDate: string,
  endDate: string,
  dayCounts: number[],
): LegPlan {
  const sum = dayCounts.reduce((acc, days) => acc + days, 0);
  const period =
    startDate && endDate && endDate >= startDate
      ? totalDays(startDate, endDate)
      : 0;

  const dates: (LegDates | null)[] = dayCounts.map(() => null);
  if (period > 0 && dayCounts.length > 0) {
    let legIndex = 0;
    let usedInLeg = 0;

    for (let offset = 0; offset < period; offset++) {
      // 현재 구간의 일수를 다 쓰면 다음 구간으로. 마지막 구간에서는 더 갈 곳이 없어
      // 그대로 머무는데, 그게 "남은 날짜가 마지막 도시를 상속한다"는 규칙이다.
      while (
        usedInLeg >= dayCounts[legIndex] &&
        legIndex < dayCounts.length - 1
      ) {
        legIndex++;
        usedInLeg = 0;
      }
      const date = addDays(startDate, offset);
      const current = dates[legIndex];
      dates[legIndex] = {
        startDate: current?.startDate ?? date,
        endDate: date,
      };
      usedInLeg++;
    }
  }

  return {
    dates,
    sum,
    period,
    verdict: sum === period ? "exact" : sum > period ? "over" : "short",
    diff: Math.abs(sum - period),
  };
}

function addDays(date: string, days: number): string {
  const d = new Date(`${date}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}
