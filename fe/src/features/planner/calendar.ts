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
