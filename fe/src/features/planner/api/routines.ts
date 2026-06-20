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

/** 편집/삭제 적용 범위. following/instance는 instanceDate가 필요하다. */
export type RoutineScope = "all" | "following" | "instance";

export interface RoutineEditRequest {
  title: string;
  allDay: boolean;
  start: string;
  end: string;
  recurrence: RoutineRecurrence;
  memo: string | null;
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

export async function listRoutines(): Promise<RoutineSeriesSummary[]> {
  const { data } =
    await client.get<ApiEnvelope<{ routines: RoutineSeriesSummary[] }>>(
      "/planner/routines",
    );
  return data.data.routines;
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

interface ScopeParams {
  scope: RoutineScope;
  instanceDate?: string;
}

export async function updateRoutine(
  eventId: string,
  request: RoutineEditRequest,
  { scope, instanceDate }: ScopeParams,
): Promise<RoutineSeriesSummary> {
  const { data } = await client.patch<ApiEnvelope<RoutineSeriesSummary>>(
    `/planner/routines/${eventId}`,
    request,
    { params: { scope, instanceDate } },
  );
  return data.data;
}

export async function deleteRoutine(
  eventId: string,
  { scope, instanceDate }: ScopeParams,
): Promise<void> {
  await client.delete(`/planner/routines/${eventId}`, {
    params: { scope, instanceDate },
  });
}

export interface RoutineCheckResult {
  recurringEventId: string;
  date: string;
  done: boolean;
}

/** 습관 완료 체크 토글. done=true upsert / false delete. */
export async function checkRoutine(
  recurringEventId: string,
  date: string,
  done: boolean,
): Promise<RoutineCheckResult> {
  const { data } = await client.post<ApiEnvelope<RoutineCheckResult>>(
    `/planner/routines/${recurringEventId}/check`,
    { date, done },
  );
  return data.data;
}
