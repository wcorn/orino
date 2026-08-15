import { ArrowRight } from "lucide-react";

import { formatShortDate } from "@/features/travel/lib/tripStatus";

interface LegMoveRowProps {
  /** 도시가 바뀌는 날 — 다음 구간이 시작하는 날짜다. */
  date: string;
  fromCity: string;
  toCity: string;
}

/**
 * 구간 사이의 이동 줄 — `10.28 오사카 → 교토`.
 *
 * <p>구간을 일 단위로 배타적으로 나누면 <b>이동일이 어느 구간에도 안 보인다.</b> 오사카가
 * 10.27에 끝나고 교토가 10.28에 시작하는데, 카드만 보면 10.28 오전에 아직 오사카에 있다는
 * 사실이 어디에도 없다. 그 하루가 두 도시에 속한다는 것을 여기서 말한다(D-25).
 *
 * <p>날짜는 <b>다음 구간의 첫날</b>이다. 기준 도시는 도착한 쪽이라 그날이 곧 이동일이다.
 */
export function LegMoveRow({ date, fromCity, toCity }: LegMoveRowProps) {
  return (
    <li
      className="text-muted-foreground flex items-center gap-1.5 py-0.5 pl-[34px] text-xs"
      aria-label={`${formatShortDate(date)} ${fromCity}에서 ${toCity}로 이동`}
    >
      <span className="tabular-nums">{formatShortDate(date)}</span>
      <span className="truncate">{fromCity}</span>
      <ArrowRight className="size-3 shrink-0" aria-hidden="true" />
      <span className="truncate">{toCity}</span>
    </li>
  );
}
