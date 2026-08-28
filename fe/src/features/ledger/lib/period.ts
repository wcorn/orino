/**
 * 월 시작일이 반영된 「이번 달」 구간.
 *
 * <p>급여일 기준(25일)으로 두면 8월 20일은 아직 <b>7월 25일에 시작한 구간</b>이다.
 * 서버(`GET /summary`)도 같은 계산을 하지만, 내역 화면은 월을 앞뒤로 넘기며 보므로
 * 화면 쪽에도 같은 규칙이 필요하다 — 두 곳이 갈리면 합계 바와 목록이 서로 다른 달을 말한다.
 */

/** 말일을 뜻하는 특수값. 2월과 31일 달을 한 값으로 표현하려면 이 방법뿐이다. */
export const LAST_DAY_OF_MONTH = 99;

export interface Period {
  start: string;
  end: string;
}

function iso(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

function lastDayOf(year: number, month: number): number {
  return new Date(year, month + 1, 0).getDate();
}

/** 그 달에 실제로 존재하는 시작일. 말일(99)과 짧은 달을 함께 처리한다. */
function startDayIn(
  year: number,
  month: number,
  monthStartDay: number,
): number {
  if (monthStartDay === LAST_DAY_OF_MONTH) {
    return lastDayOf(year, month);
  }
  return Math.min(monthStartDay, lastDayOf(year, month));
}

/**
 * `anchor`가 속한 구간. `monthStartDay`가 1이면 달력 달 그대로다.
 *
 * @param offset 앞뒤로 몇 구간 이동할지. 월 네비게이터가 쓴다
 */
export function periodOf(
  anchor: Date,
  monthStartDay: number,
  offset = 0,
): Period {
  const year = anchor.getFullYear();
  const month = anchor.getMonth();
  const day = startDayIn(year, month, monthStartDay);

  // 시작일 전이면 아직 지난 구간에 있다.
  const base =
    anchor.getDate() < day
      ? new Date(year, month - 1, 1)
      : new Date(year, month, 1);
  const shifted = new Date(base.getFullYear(), base.getMonth() + offset, 1);

  const startYear = shifted.getFullYear();
  const startMonth = shifted.getMonth();
  const start = new Date(
    startYear,
    startMonth,
    startDayIn(startYear, startMonth, monthStartDay),
  );

  const nextMonth = new Date(startYear, startMonth + 1, 1);
  const next = new Date(
    nextMonth.getFullYear(),
    nextMonth.getMonth(),
    startDayIn(nextMonth.getFullYear(), nextMonth.getMonth(), monthStartDay),
  );
  const end = new Date(next.getTime() - 86_400_000);

  return { start: iso(start), end: iso(end) };
}

/**
 * 구간 라벨 — `2026년 8월`.
 *
 * <p>월 시작일이 1이 아니면 구간이 두 달에 걸친다. 그때는 <b>시작한 달</b>을 이름으로 쓴다 —
 * 「8월 급여로 사는 기간」이 사람이 그 구간을 부르는 이름이기 때문이다.
 */
export function periodLabel(period: Period): string {
  const start = new Date(`${period.start}T00:00:00`);
  return `${start.getFullYear()}년 ${start.getMonth() + 1}월`;
}

/** 오늘(기기 시간대). 서버가 준 `todayLine`이 있으면 그쪽을 쓴다. */
export function todayIso(): string {
  return iso(new Date());
}
