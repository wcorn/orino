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

/** 앱이 계산하는 이동수단. 대중교통은 계산하지 않는다 — 구글 지도 딥링크가 맡는다. */
export type TravelMode = "WALK" | "DRIVE";

export interface Leg {
  fromActivityId: number;
  toActivityId: number;
  mode: TravelMode;
  /** `fallback`이면 null — 거리만 안다. */
  durationMinutes: number | null;
  /** 경로 거리. `fallback`이면 직선거리다. */
  distanceM: number;
  /** Routes를 못 얻어 직선거리로 대체했다. 화면은 `약 N.Nkm`로 보여준다. */
  fallback: boolean;
}

export interface Board {
  trip: BoardTrip;
  days: BoardDay[];
  /** 이번 응답의 activities가 속한 날짜. null이면 보관함을 보고 있다. */
  selectedDate: string | null;
  archiveCount: number;
  activities: Activity[];
  /** 연속한 두 일정 사이 이동. 장소 없는 일정은 건너뛴 결과다. */
  legs: Leg[];
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

export async function fetchActivity(activityId: number): Promise<Activity> {
  const { data } = await client.get<ApiEnvelope<Activity>>(
    `/travel/activities/${activityId}`,
  );
  return data.data;
}

export interface ActivityWriteRequest {
  title: string;
  /** null이면 보관함으로 넣는다. */
  activityDate?: string | null;
  startTime?: string | null;
  placeId?: number | null;
  /**
   * 검색 결과에서 곧바로 담을 때. 서버가 장소를 upsert해 일정에 연결하므로
   * 프론트가 장소를 먼저 만들 필요가 없다. `placeId`와 함께 보내지 않는다.
   */
  googlePlaceId?: string | null;
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

/** 날짜 하나의 전체 순서. 부분 갱신은 보내지 않는다(순서가 비결정적이 된다). */
export interface ReorderMove {
  /** null이면 미배정 보관함. */
  date: string | null;
  activityIds: number[];
}

/**
 * 드래그 결과를 한 번에 반영한다. 응답에 **재계산된 `legs`가 담겨** 온다 —
 * 드래그는 손을 뗀 순간 결과가 보여야 해서, 이동시간 때문에 한 번 더 왕복하지 않는다.
 *
 * <p>다른 날짜로 옮기는 것은 이 엔드포인트가 아니라 {@link updateActivity}로 한다 —
 * 서버가 대상 날짜의 맨 뒤에 붙이고 떠나온 날짜를 재인덱싱해 주기 때문에, 대상 날짜의
 * 기존 순서를 모르는 화면에서도 정확히 "끝에 추가"가 된다.
 */
export async function reorderActivities(
  tripId: number,
  moves: ReorderMove[],
): Promise<Leg[]> {
  const { data } = await client.put<ApiEnvelope<{ legs: Leg[] }>>(
    `/travel/trips/${tripId}/activities/order`,
    { moves },
  );
  return data.data.legs;
}
