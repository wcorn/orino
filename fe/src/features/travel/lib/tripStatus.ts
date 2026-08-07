export type TripStatus = "UPCOMING" | "ONGOING" | "COMPLETED";

/**
 * 여행 타임존 기준 오늘 날짜("YYYY-MM-DD").
 *
 * <p>`en-CA` 로케일이 ISO 형태로 포맷해 준다. 알 수 없는 타임존이면 기기 기준으로 떨어진다 —
 * 화면이 죽는 것보다 하루 어긋날 가능성이 낫다.
 */
export function todayIn(timezone: string, now: Date = new Date()): string {
  try {
    return new Intl.DateTimeFormat("en-CA", { timeZone: timezone }).format(now);
  } catch {
    return new Intl.DateTimeFormat("en-CA").format(now);
  }
}

/**
 * 상태를 파생한다. BE와 같은 규칙이며 기준은 <b>여행 타임존의 오늘</b>이다.
 *
 * <p>서버도 같은 값을 내려주는데 여기서 다시 계산하는 이유는 <b>오프라인 캐시</b>다(4단계).
 * 어제 받아둔 응답을 그대로 보여주면 D-day와 상태가 하루 어긋난 채 화면에 남는다.
 */
export function deriveStatus(
  startDate: string,
  endDate: string,
  timezone: string,
  now?: Date,
): TripStatus {
  // "YYYY-MM-DD"는 사전순 비교가 곧 날짜 비교다.
  const today = todayIn(timezone, now);
  if (today < startDate) return "UPCOMING";
  if (today > endDate) return "COMPLETED";
  return "ONGOING";
}

/** 시작일까지 남은 일수. 시작 당일 0, 이미 시작했으면 음수. */
export function daysUntil(
  startDate: string,
  timezone: string,
  now?: Date,
): number {
  return diffDays(todayIn(timezone, now), startDate);
}

/** 여행 총 일수(당일 포함). 하루짜리면 1. */
export function totalDays(startDate: string, endDate: string): number {
  return diffDays(startDate, endDate) + 1;
}

export interface DayChip {
  /** 1부터 시작하는 일차. */
  dayIndex: number;
  /** "2026-10-24" */
  date: string;
  /** 한국어 요일 한 글자("금"). */
  weekday: string;
}

/** 기간에서 일자 칩을 만든다. 일정이 없는 날짜도 칩은 나와야 한다. */
export function dayChips(startDate: string, endDate: string): DayChip[] {
  const chips: DayChip[] = [];
  const count = totalDays(startDate, endDate);
  for (let i = 0; i < count; i++) {
    const date = addDays(startDate, i);
    chips.push({ dayIndex: i + 1, date, weekday: weekdayOf(date) });
  }
  return chips;
}

/** "2026-10-24" → "10월 24일" */
export function formatMonthDay(date: string): string {
  const [, month, day] = date.split("-");
  return `${Number(month)}월 ${Number(day)}일`;
}

/** "2026-10-24" → "10.24" (목록 메타처럼 좁은 자리에 쓴다) */
export function formatShortDate(date: string): string {
  const [, month, day] = date.split("-");
  return `${Number(month)}.${day}`;
}

/**
 * 기간 한 줄. "10월 24일 – 10월 27일", 하루짜리면 한 번만 쓴다.
 */
export function formatPeriod(startDate: string, endDate: string): string {
  if (startDate === endDate) return formatMonthDay(startDate);
  return `${formatMonthDay(startDate)} – ${formatMonthDay(endDate)}`;
}

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

function weekdayOf(date: string): string {
  return WEEKDAYS[toUtcDate(date).getUTCDay()];
}

function addDays(date: string, days: number): string {
  const d = toUtcDate(date);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

function diffDays(from: string, to: string): number {
  const ms = toUtcDate(to).getTime() - toUtcDate(from).getTime();
  return Math.round(ms / 86_400_000);
}

/**
 * "YYYY-MM-DD"를 UTC 자정으로 읽는다. 날짜 계산에만 쓰는 값이라 타임존을 태우지 않는다 —
 * 로컬 자정으로 만들면 서머타임 경계에서 하루가 23·25시간이 돼 일수가 어긋난다.
 */
function toUtcDate(date: string): Date {
  return new Date(`${date}T00:00:00Z`);
}
