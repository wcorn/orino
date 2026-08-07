import { client } from "@/shared/api";

import type { TripStatus } from "../lib/tripStatus";

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

export interface ActivityPlace {
  id: number;
  name: string;
  address: string | null;
  lat: number | null;
  lng: number | null;
}

export interface Activity {
  id: number;
  tripId: number;
  title: string;
  /** null이면 미배정 보관함에 있는 일정. */
  activityDate: string | null;
  /** 여행 타임존의 벽시계 시각("09:00"). null이면 시각 미정. */
  startTime: string | null;
  place: ActivityPlace | null;
  memo: string | null;
  url: string | null;
  notifyEnabled: boolean;
  notifyMinutes: number | null;
  departureNotifyEnabled: boolean;
  sortOrder: number;
  /** 사후 기록(평점·메모) 존재 여부. 기록은 4단계라 지금은 항상 false. */
  hasLog: boolean;
}

export interface BoardDay {
  dayIndex: number;
  date: string;
  weekday: string;
  activityCount: number;
  /** 날씨는 4단계. 지금은 항상 null. */
  weather: unknown | null;
}

export interface BoardTrip {
  id: number;
  title: string;
  timezone: string;
  currency: string;
  startDate: string;
  endDate: string;
  status: TripStatus;
  /** 완료된 여행이면 true — 계획 편집 대신 기록을 보여준다(4단계). */
  recordMode: boolean;
}

export interface Board {
  trip: BoardTrip;
  days: BoardDay[];
  /** 이번 응답의 activities가 속한 날짜. null이면 보관함을 보고 있다. */
  selectedDate: string | null;
  archiveCount: number;
  activities: Activity[];
  /** 이동시간은 2단계. 지금은 항상 빈 배열. */
  legs: unknown[];
}

/**
 * 보드 단일 조회. `date`와 `archive`는 배타적이며, 둘 다 없으면 서버가 고른다
 * (진행 중이면 여행 타임존의 오늘, 아니면 1일차).
 */
export async function fetchBoard(
  tripId: number,
  params: { date?: string; archive?: boolean },
): Promise<Board> {
  const { data } = await client.get<ApiEnvelope<Board>>(
    `/travel/trips/${tripId}/board`,
    { params: params.archive ? { archive: true } : { date: params.date } },
  );
  return data.data;
}

export interface ActivityWriteRequest {
  title: string;
  /** null이면 보관함으로 넣는다. */
  activityDate?: string | null;
  startTime?: string | null;
  placeId?: number | null;
  memo?: string | null;
  url?: string | null;
  notifyEnabled?: boolean;
  notifyMinutes?: number | null;
  departureNotifyEnabled?: boolean;
}

export async function createActivity(
  tripId: number,
  body: ActivityWriteRequest,
): Promise<Activity> {
  const { data } = await client.post<ApiEnvelope<Activity>>(
    `/travel/trips/${tripId}/activities`,
    body,
  );
  return data.data;
}

export async function updateActivity(
  activityId: number,
  body: ActivityWriteRequest,
): Promise<Activity> {
  const { data } = await client.put<ApiEnvelope<Activity>>(
    `/travel/activities/${activityId}`,
    body,
  );
  return data.data;
}

export async function deleteActivity(activityId: number): Promise<void> {
  await client.delete(`/travel/activities/${activityId}`);
}
