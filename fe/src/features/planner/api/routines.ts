import { client } from "@/shared/api";

export type RoutineType = "habit" | "schedule";
export type RoutineFreq = "DAILY" | "WEEKLY" | "MONTHLY";
export type Weekday = "MO" | "TU" | "WE" | "TH" | "FR" | "SA" | "SU";

export interface RoutineRecurrence {
  freq: RoutineFreq;
  interval?: number | null;
  byDay?: Weekday[] | null;
  byMonthDay?: number[] | null;
  /** 종료일(포함) "2026-12-31". 없으면 무한 반복 */
  until?: string | null;
}

export interface RoutineCreateRequest {
  type: RoutineType;
  title: string;
  allDay: boolean;
  /** 종일이면 "2026-06-20", 시간 루틴이면 로컬 "2026-06-20T07:00:00" */
  start: string;
  end: string;
  recurrence: RoutineRecurrence;
  memo: string | null;
  color: string | null;
}

export interface RoutineSeriesSummary {
  recurringEventId: string;
  type: RoutineType;
  title: string;
  allDay: boolean;
  start: string;
  end?: string | null;
  recurrence: RoutineRecurrence;
  recurrenceText: string;
  color?: string | null;
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

export async function createRoutine(
  request: RoutineCreateRequest,
): Promise<RoutineSeriesSummary> {
  const { data } = await client.post<ApiEnvelope<RoutineSeriesSummary>>(
    "/planner/routines",
    request,
  );
  return data.data;
}
