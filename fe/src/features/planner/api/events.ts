import { client } from "@/shared/api";

import type { PlannerEvent } from "./feed";

export interface EventWriteRequest {
  title: string;
  allDay: boolean;
  /** 종일이면 "2026-06-10", 시간 일정이면 로컬 "2026-06-10T14:00:00" */
  start: string;
  end: string;
  location: string | null;
  description: string | null;
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

export async function createEvent(
  request: EventWriteRequest,
): Promise<PlannerEvent> {
  const { data } = await client.post<ApiEnvelope<PlannerEvent>>(
    "/planner/calendar/events",
    request,
  );
  return data.data;
}

export async function updateEvent(
  eventId: string,
  request: EventWriteRequest,
): Promise<PlannerEvent> {
  const { data } = await client.patch<ApiEnvelope<PlannerEvent>>(
    `/planner/calendar/events/${eventId}`,
    request,
  );
  return data.data;
}

export async function deleteEvent(eventId: string): Promise<void> {
  await client.delete(`/planner/calendar/events/${eventId}`);
}
