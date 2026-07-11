/**
 * 복습 시각 라벨 포맷터. 서버는 `scheduledAt`/`completedAt`(사용자 시간대 로컬 ISO)만 주고,
 * "지금 / 10분 후 / 오늘 20:30 / 어제 22:10 / 내일 07/12" 같은 사람 친화 라벨은 FE가 만든다.
 */

function pad(n: number): string {
  return String(n).padStart(2, "0");
}

function hhmm(d: Date): string {
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function mmdd(d: Date): string {
  return `${pad(d.getMonth() + 1)}/${pad(d.getDate())}`;
}

/** 로컬 날짜 기준 일수 차(to - from). 같은 날 0, 내일 +1, 어제 -1. */
function calendarDayDiff(from: Date, to: Date): number {
  const a = new Date(from.getFullYear(), from.getMonth(), from.getDate());
  const b = new Date(to.getFullYear(), to.getMonth(), to.getDate());
  return Math.round((b.getTime() - a.getTime()) / 86_400_000);
}

/** 앞으로의 복습 예정 라벨: 지금 / N분 후 / 오늘 HH:MM / 내일 MM/DD / MM/DD. */
export function formatUpcomingLabel(
  iso: string,
  now: Date = new Date(),
): string {
  const at = new Date(iso);
  const diffMin = Math.round((at.getTime() - now.getTime()) / 60_000);
  if (diffMin <= 0) return "지금";
  if (diffMin < 60) return `${diffMin}분 후`;

  const days = calendarDayDiff(now, at);
  if (days <= 0) return `오늘 ${hhmm(at)}`;
  if (days === 1) return `내일 ${mmdd(at)}`;
  return mmdd(at);
}

/** 완료된 복습 시각 라벨: 오늘 HH:MM / 어제 HH:MM / MM/DD HH:MM. */
export function formatCompletedLabel(
  iso: string,
  now: Date = new Date(),
): string {
  const at = new Date(iso);
  const daysAgo = calendarDayDiff(at, now);
  if (daysAgo <= 0) return `오늘 ${hhmm(at)}`;
  if (daysAgo === 1) return `어제 ${hhmm(at)}`;
  return `${mmdd(at)} ${hhmm(at)}`;
}
