import { client } from "@/shared/api";

/** 루틴 인스턴스 주석. 루틴이 아니면 null. habit done은 routine_check 조인 결과. */
export interface RoutineMeta {
  type: "habit" | "schedule";
  recurringEventId: string;
  done: boolean;
}

export interface PlannerEvent {
  id: string;
  title: string | null;
  allDay: boolean;
  /** 종일이면 "2026-06-10", 시간 일정이면 사용자 TZ 로컬 "2026-06-10T14:00:00" */
  start: string;
  end: string | null;
  location: string | null;
  recurring: boolean;
  source: "google";
  routine?: RoutineMeta | null;
}

export interface PlannerTask {
  id: string;
  title: string;
  due: string | null;
  completed: boolean;
  source: "google";
}

export type PlannerReviewStatus = "PENDING" | "COMPLETED";

export interface PlannerReview {
  id: number;
  /** ISO datetime. 캘린더 그룹/분류는 날짜 부분(slice 0..10)으로 한다. */
  scheduledAt: string;
  status: PlannerReviewStatus;
  materialTitle: string;
  front: string;
  readOnly: boolean;
  source: "review";
}

export interface FeedError {
  source: string;
  message: string;
}

export interface PlannerCalendarFeed {
  from: string;
  to: string;
  googleConnected: boolean;
  partial: boolean;
  errors: FeedError[];
  events: PlannerEvent[];
  tasks: PlannerTask[];
  reviews: PlannerReview[];
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

export async function fetchPlannerCalendar(
  from: string,
  to: string,
): Promise<PlannerCalendarFeed> {
  const { data } = await client.get<ApiEnvelope<PlannerCalendarFeed>>(
    "/planner/calendar",
    { params: { from, to } },
  );
  return data.data;
}
