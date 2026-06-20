import {
  parseIsoDate,
  type ReviewBucket,
  startOfDay,
} from "@/features/review/calendar";

import type { PlannerEvent, PlannerReview, PlannerTask } from "./api/feed";

/** ISO datetime/date에서 로컬 날짜 키(YYYY-MM-DD)만 추출. */
export function dateKey(iso: string): string {
  return iso.slice(0, 10);
}

function groupByDateKey<T>(
  items: T[],
  keyOf: (item: T) => string | null,
): Map<string, T[]> {
  const map = new Map<string, T[]>();
  for (const item of items) {
    const key = keyOf(item);
    if (!key) {
      continue;
    }
    const list = map.get(key);
    if (list) {
      list.push(item);
    } else {
      map.set(key, [item]);
    }
  }
  return map;
}

export function eventsByDate(
  events: PlannerEvent[],
): Map<string, PlannerEvent[]> {
  return groupByDateKey(events, (e) => dateKey(e.start));
}

export function tasksByDate(tasks: PlannerTask[]): Map<string, PlannerTask[]> {
  return groupByDateKey(tasks, (t) => (t.due ? dateKey(t.due) : null));
}

export function reviewsByDate(
  reviews: PlannerReview[],
): Map<string, PlannerReview[]> {
  return groupByDateKey(reviews, (r) => dateKey(r.scheduledAt));
}

/** 복습 분류(밀림/오늘/예정/완료) — 기존 bucketStyles와 동일 기준. */
export function classifyFeedReview(
  review: PlannerReview,
  today: Date,
): ReviewBucket {
  if (review.status === "COMPLETED") {
    return "completed";
  }
  const scheduled = parseIsoDate(dateKey(review.scheduledAt)).getTime();
  const todayMid = startOfDay(today).getTime();
  if (scheduled < todayMid) {
    return "overdue";
  }
  if (scheduled === todayMid) {
    return "today";
  }
  return "upcoming";
}

/** 시간 일정 "2026-06-10T14:00:00" → "14:00", 종일이면 "종일". */
export function eventTimeLabel(event: PlannerEvent): string {
  if (event.allDay) {
    return "종일";
  }
  const time = event.start.slice(11, 16);
  return time || "종일";
}

/**
 * 일 상세 아젠다용 시작/종료 시각. 종일이면 {start:"종일", end:null},
 * 시간 일정이면 {start:"14:00", end:"15:00"|null}.
 */
export function eventTimeParts(event: PlannerEvent): {
  start: string;
  end: string | null;
} {
  if (event.allDay) {
    return { start: "종일", end: null };
  }
  return {
    start: event.start.slice(11, 16),
    end: event.end ? event.end.slice(11, 16) : null,
  };
}

/** 일 상세 일정 정렬: 종일을 먼저, 그다음 시작 시각 오름차순. 원본은 변경하지 않는다. */
export function sortDayEvents(events: PlannerEvent[]): PlannerEvent[] {
  return [...events].sort((a, b) => {
    if (a.allDay !== b.allDay) {
      return a.allDay ? -1 : 1;
    }
    return a.start.localeCompare(b.start);
  });
}
