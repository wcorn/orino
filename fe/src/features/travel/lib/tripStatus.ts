export type TripStatus = "UPCOMING" | "ONGOING" | "COMPLETED";

/**
 * 날짜 → 그 날짜의 기준 도시 타임존(IANA).
 *
 * v2.1에서 **여행은 타임존을 갖지 않는다.** 날짜마다 기준 도시가 있고 타임존은 거기서
 * 나오므로, 파생 계산은 "여행의 타임존" 대신 이 조회 함수를 받는다.
 */
export type ZoneByDate = (date: string) => string;

/** 전 기간이 한 도시인 여행. 다구간 데이터가 오기 전(#1124)까지 대부분 이 모양이다. */
export function constantZone(timezone: string): ZoneByDate {
  return () => timezone;
}

/**
 * 주어진 타임존 기준 오늘 날짜("YYYY-MM-DD").
 *
 * `en-CA` 로케일이 ISO 형태로 포맷해 준다. 알 수 없는 타임존이면 기기 기준으로 떨어진다 —
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
 * 화면이 열어야 할 "오늘" — **아직 지나지 않은 첫 날짜**. 전부 지나갔으면 마지막 날.
 *
 * 날짜마다 자기 시계로 "나는 지나갔나"를 묻는다. "그 날짜의 오늘이 그 날짜와 같은가"로
 * 물으면 날짜변경선을 넘나드는 여행에서 **어느 날짜도 답하지 못하는 순간**이 생긴다 —
 * 오사카는 벌써 25일인데 25일에 배정된 호놀룰루는 아직 24일인 식이다. BE와 같은 규칙이다.
 */
export function todayOfTrip(
  startDate: string,
  endDate: string,
  zones: ZoneByDate,
  now?: Date,
): string {
  const count = totalDays(startDate, endDate);
  for (let i = 0; i < count; i++) {
    const date = addDays(startDate, i);
    if (!hasPassed(date, zones, now)) return date;
  }
  return endDate;
}

/**
 * 상태를 파생한다. BE와 같은 규칙 — **첫날 도시가 시작일에 닿기 전이면 예정, 마지막 날
 * 도시가 종료일을 넘겼으면 완료**, 그 사이는 진행 중이다.
 *
 * 서버도 같은 값을 내려주는데 여기서 다시 계산하는 이유는 **오프라인 캐시**다(4단계).
 * 어제 받아둔 응답을 그대로 보여주면 D-day와 상태가 하루 어긋난 채 화면에 남는다.
 */
export function deriveStatus(
  startDate: string,
  endDate: string,
  zones: ZoneByDate,
  now?: Date,
): TripStatus {
  // "YYYY-MM-DD"는 사전순 비교가 곧 날짜 비교다.
  if (todayIn(zones(startDate), now) < startDate) return "UPCOMING";
  if (hasPassed(endDate, zones, now)) return "COMPLETED";
  return "ONGOING";
}

/**
 * 시작일까지 남은 일수. 시작 당일 0, 이미 시작했으면 음수.
 * 기준은 **첫날** 도시다 — "언제 출발하나"는 출발하는 곳의 시계로 세는 값이다.
 */
export function daysUntil(
  startDate: string,
  zones: ZoneByDate,
  now?: Date,
): number {
  return diffDays(todayIn(zones(startDate), now), startDate);
}

/** 그 날짜가 자기 도시의 시계로 이미 지나갔는가. */
function hasPassed(date: string, zones: ZoneByDate, now?: Date): boolean {
  return todayIn(zones(date), now) > date;
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
