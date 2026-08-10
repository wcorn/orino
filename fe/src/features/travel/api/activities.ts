import { client } from "@/shared/api";

import type { TripStatus } from "../lib/tripStatus";
import type { ActivityPhoto } from "./photos";
import type { DailyWeather } from "./tools";

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
  /** 이 장소가 속한 도시 표시명. 도시를 벗어난 일정의 `· 오사카` 꼬리표. */
  cityName: string | null;
  /** 도시 식별자. 도시 일치 판정은 이 값으로만 한다(좌표 거리로 추측하지 않는다). */
  cityPlaceRef: string | null;
}

/**
 * 일정의 사후 기록(§S-07 기록 영역).
 *
 * <p>사진은 별도 테이블·별도 요청이다 — 업로드가 실패해도 평점·메모는 남는다. 조회에서만
 * 함께 실려 온다.
 */
export interface ActivityLog {
  /** 1~5. null이면 아직 매기지 않았거나 해제한 것이다. */
  rating: number | null;
  memo: string | null;
  photos: ActivityPhoto[];
  updatedAt: string;
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
  /** 그날 기준 도시와 다른 도시의 장소다 → 도시명을 경고색으로 덧붙인다. */
  outOfBaseCity: boolean;
  /** 도시를 넘는 이동은 계산 대상이 아니라 출발 알림을 켤 수 없다(3단계에서 판정이 붙는다). */
  canDepartureNotify: boolean;
  sortOrder: number;
  /** 아직 기록이 없으면 null. */
  log: ActivityLog | null;
  hasLog: boolean;
}

/**
 * 그 날짜의 기준 도시. **타임존·통화·날씨 좌표가 전부 여기서 나온다** — v2.1에서 여행은
 * 타임존을 갖지 않는다.
 */
export interface BaseCity {
  placeId: number;
  name: string;
  timezone: string;
  currency: string;
  countryCode: string | null;
  /** 도시 식별자. 장소의 같은 값과 맞춰 도시 일치를 판정한다(이름으로 묶지 않는다). */
  cityPlaceRef: string | null;
  lat: number | null;
  lng: number | null;
}

export interface BoardDay {
  dayId: number;
  dayIndex: number;
  date: string;
  weekday: string;
  activityCount: number;
  baseCity: BaseCity | null;
  /** 직전 날짜와 도시가 다르다 → 탭 왼쪽에 구분선. */
  cityChanged: boolean;
  /** 이 날짜가 속한 구간 번호(1부터). 저장값이 아니라 파생이다. */
  legIndex: number;
  cityMemo: string | null;
  /** 예보 범위(16일) 밖이면 null이다 — 오류가 아니라 "아직 모름"이다. */
  weather: DailyWeather | null;
  /** 숙소는 3단계 — 형태만 확정돼 있고 값은 아직 없다. */
  stayTonight: null;
  stayCheckout: null;
}

export interface BoardTrip {
  id: number;
  title: string;
  startDate: string;
  endDate: string;
  status: TripStatus;
  /** 완료된 여행이면 true — 계획 편집 대신 기록을 보여준다(4단계). */
  recordMode: boolean;
  /** 기간에 등장하는 서로 다른 도시 수. 같은 도시를 다시 방문해도 1이다. */
  cityCount: number;
  countryCount: number;
  /** 전 기간이 한 도시 — 날짜 탭을 `N일차`로 그린다. */
  singleCity: boolean;
}

/** 앱이 계산하는 이동수단. 대중교통은 계산하지 않는다 — 구글 지도 딥링크가 맡는다. */
export type TravelMode = "WALK" | "DRIVE";

export interface TravelTime {
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
  travelTimes: TravelTime[];
  /** 마지막 일정 → 숙소 이동. 숙소는 3단계라 아직 null이다. */
  stayMove: null;
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

export interface ActivityLogRequest {
  rating: number | null;
  memo: string | null;
}

/**
 * 기록(평점·메모) 저장. 사진과 분리된 요청이라 사진 업로드가 실패해도 이건 남는다.
 *
 * <p>둘 다 비우면 서버가 기록을 지우고 null을 돌려준다 — "다 지웠다"와 "빈 기록이 있다"는
 * 다른 상태다.
 */
export async function saveActivityLog(
  activityId: number,
  body: ActivityLogRequest,
): Promise<ActivityLog | null> {
  const { data } = await client.put<ApiEnvelope<ActivityLog | null>>(
    `/travel/activities/${activityId}/log`,
    body,
  );
  return data.data ?? null;
}

/**
 * 이동수단 시트가 여는 단건 조회. 자동 판정되지 않은 수단은 **고른 순간에만** 부른다 —
 * 미리 둘 다 받아두면 아무도 안 열어 볼 값까지 사게 된다(호출당 과금).
 */
export async function fetchTravelTime(
  tripId: number,
  from: number,
  to: number,
  mode: TravelMode,
): Promise<TravelTime> {
  const { data } = await client.get<ApiEnvelope<TravelTime>>(
    `/travel/trips/${tripId}/travel-time`,
    { params: { from, to, mode } },
  );
  return data.data;
}

/** 날짜 하나의 전체 순서. 부분 갱신은 보내지 않는다(순서가 비결정적이 된다). */
export interface ReorderMove {
  /** null이면 미배정 보관함. */
  date: string | null;
  activityIds: number[];
}

/**
 * 드래그 결과를 한 번에 반영한다. 응답에 **재계산된 `travelTimes`가 담겨** 온다 —
 * 드래그는 손을 뗀 순간 결과가 보여야 해서, 이동시간 때문에 한 번 더 왕복하지 않는다.
 *
 * <p>다른 날짜로 옮기는 것은 이 엔드포인트가 아니라 {@link updateActivity}로 한다 —
 * 서버가 대상 날짜의 맨 뒤에 붙이고 떠나온 날짜를 재인덱싱해 주기 때문에, 대상 날짜의
 * 기존 순서를 모르는 화면에서도 정확히 "끝에 추가"가 된다.
 */
export async function reorderActivities(
  tripId: number,
  moves: ReorderMove[],
): Promise<TravelTime[]> {
  const { data } = await client.put<ApiEnvelope<{ travelTimes: TravelTime[] }>>(
    `/travel/trips/${tripId}/activities/order`,
    { moves },
  );
  return data.data.travelTimes;
}
