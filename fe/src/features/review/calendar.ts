import type { CalendarReview } from "./api/calendar";

export type ReviewBucket = "completed" | "overdue" | "today" | "upcoming";

/** scheduledAt(datetime)에서 로컬 날짜 부분(YYYY-MM-DD)만 추출. 캘린더는 날짜 단위로 그룹/분류한다. */
export function reviewDate(review: CalendarReview): string {
  return review.scheduledAt.slice(0, 10);
}

/** YYYY-MM-DD 로컬 포맷 (UTC 변환 없이) */
export function toIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function parseIsoDate(iso: string): Date {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d);
}

export function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

export function addDays(date: Date, days: number): Date {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

export function addMonths(date: Date, months: number): Date {
  return new Date(date.getFullYear(), date.getMonth() + months, 1);
}

/**
 * 해당 월을 포함하는 6주(42일) 그리드. 일요일 시작.
 * 첫째 날 = 해당 월 1일이 속한 주의 일요일.
 */
export function monthGridDays(year: number, month0: number): Date[] {
  const first = new Date(year, month0, 1);
  const start = addDays(first, -first.getDay());
  return Array.from({ length: 42 }, (_, i) => addDays(start, i));
}

export function classifyReview(
  review: CalendarReview,
  today: Date,
): ReviewBucket {
  if (review.status === "COMPLETED") {
    return "completed";
  }
  const scheduled = parseIsoDate(reviewDate(review)).getTime();
  const todayMid = startOfDay(today).getTime();
  if (scheduled < todayMid) {
    return "overdue";
  }
  if (scheduled === todayMid) {
    return "today";
  }
  return "upcoming";
}

export interface DayBucketCounts {
  completed: number;
  overdue: number;
  today: number;
  upcoming: number;
}

/** ISO 날짜 → 그날 복습 목록 */
export function groupByDate(
  reviews: CalendarReview[],
): Map<string, CalendarReview[]> {
  const map = new Map<string, CalendarReview[]>();
  for (const r of reviews) {
    const key = reviewDate(r);
    const list = map.get(key);
    if (list) {
      list.push(r);
    } else {
      map.set(key, [r]);
    }
  }
  return map;
}

export function countBuckets(
  reviews: CalendarReview[],
  today: Date,
): DayBucketCounts {
  const counts: DayBucketCounts = {
    completed: 0,
    overdue: 0,
    today: 0,
    upcoming: 0,
  };
  for (const r of reviews) {
    counts[classifyReview(r, today)] += 1;
  }
  return counts;
}
