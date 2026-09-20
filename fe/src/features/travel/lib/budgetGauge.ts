export interface GaugeWidths {
  /** 이미 쓴 돈. 게이지의 진한 부분. */
  spent: number;
  /** 아직 안 썼지만 나갈 게 확정된 돈. 연한 부분. */
  scheduled: number;
}

/**
 * 예산 게이지 두 겹의 너비(%). 가계부의 월 예산 게이지에서 이 함수 하나만 옮겨 왔다.
 *
 * <p>확정분만 칠하면 「아직 절반 남았네」 하다가 숙소 잔금이 빠지고 놀란다 —
 * 그래서 예정분이 <b>확정분 위에 이어 붙는다</b>(경비 독립 §3).
 *
 * <p>합이 100%를 넘지 않게 자른다. 예산을 넘긴 여행에서도 막대가 칸 밖으로 삐져나가면
 * 「얼마나 넘었나」는 숫자로 읽어야 하고 막대는 아무 말도 못 하게 된다.
 */
export function gaugeWidths(
  spent: number,
  scheduled: number,
  total: number,
): GaugeWidths {
  if (total <= 0) {
    return { spent: 0, scheduled: 0 };
  }
  const spentPct = Math.min((spent / total) * 100, 100);
  const scheduledPct = Math.min((scheduled / total) * 100, 100 - spentPct);
  return { spent: spentPct, scheduled: Math.max(scheduledPct, 0) };
}
