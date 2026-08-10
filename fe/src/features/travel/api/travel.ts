import { client } from "@/shared/api";

import type { TripStatus } from "../lib/tripStatus";

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

/** 진행 중 여행 — 탭하면 곧바로 보드로 간다. */
export interface OngoingTripSummary {
  id: number;
  title: string;
  /** 서버가 조립해 주는 보드 경로. 프론트가 경로를 다시 만들지 않는다. */
  boardPath: string;
}

/** 다음 예정 여행 — D-day 카운트다운 카드. */
export interface NextTripSummary {
  id: number;
  title: string;
  destinationName: string;
  startDate: string;
  endDate: string;
  /** 시작일까지 남은 일수. 여행 타임존 기준이라 프론트에서 다시 계산하지 않는다. */
  dDay: number;
  activityCount: number;
}

/** 가장 최근에 끝난 여행. */
export interface CompletedTripSummary {
  id: number;
  title: string;
  endDate: string;
  activityCount: number;
}

/**
 * `/select` 카드와 여행 홈(S-01)이 함께 쓰는 요약.
 * 셋 다 null이면 여행을 한 번도 만들지 않은 상태다.
 */
export interface TravelSummary {
  ongoing: OngoingTripSummary | null;
  next: NextTripSummary | null;
  recentCompleted: CompletedTripSummary | null;
}

export async function fetchTravelSummary(): Promise<TravelSummary> {
  const { data } =
    await client.get<ApiEnvelope<TravelSummary>>("/travel/summary");
  return data.data;
}

/** 여행 목록 카드 하나. `status`·`dDay`는 서버가 여행 타임존으로 파생해 준 값이다. */
export interface TripSummary {
  id: number;
  title: string;
  destinationName: string;
  startDate: string;
  endDate: string;
  status: TripStatus;
  dDay: number;
  activityCount: number;
}

export interface TripCounts {
  upcoming: number;
  ongoing: number;
  completed: number;
}

export interface TripListResponse {
  /** 탭 라벨 뒤 건수. 필터와 무관하게 항상 전체 기준이다. */
  counts: TripCounts;
  trips: TripSummary[];
}

/** 여행 상세. 생성·수정 화면(#1036)이 폼을 채울 때도 쓴다. */
export interface TripDetail extends TripSummary {
  destinationPlaceId: number | null;
  timezone: string;
  currency: string;
  lat: number | null;
  lng: number | null;
  defaultNotifyMinutes: number;
  morningSummaryEnabled: boolean;
  totalDays: number;
}

export async function fetchTrips(
  status?: TripStatus,
): Promise<TripListResponse> {
  const { data } = await client.get<ApiEnvelope<TripListResponse>>(
    "/travel/trips",
    { params: status ? { status } : undefined },
  );
  return data.data;
}

export async function fetchTrip(tripId: number): Promise<TripDetail> {
  const { data } = await client.get<ApiEnvelope<TripDetail>>(
    `/travel/trips/${tripId}`,
  );
  return data.data;
}

/**
 * 구간 하나 — 도시 + 머무는 일수. 목록 순서가 곧 방문 순서다.
 *
 * 도시는 담아 둔 장소(`cityPlaceId`)나 검색 결과(`cityGooglePlaceId`) 중 하나로 지정한다.
 */
export interface TripLegRequest {
  cityPlaceId?: number;
  cityGooglePlaceId?: string;
  days: number;
}

/**
 * 여행 생성·수정 요청. 전체 수정이라 두 경로가 같은 형태를 쓴다.
 *
 * 타임존·통화·좌표는 보내지 않는다 — 도시가 그 값들의 주인이라(v2.1), 여행이 따로 들고
 * 있으면 도시를 옮겨 다닐 때 서로 어긋난다.
 */
export interface TripWriteRequest {
  /** v2.1부터 필수 — 목적지가 여행에 없으니 자동으로 채울 이름도 없다. */
  title: string;
  startDate: string;
  endDate: string;
  /** 생성에는 필수. 수정에서 생략하면 날짜별 기준 도시를 건드리지 않는다. */
  legs?: TripLegRequest[];
  defaultNotifyMinutes?: number;
  morningSummaryEnabled?: boolean;
  /** 기간 단축으로 잘리는 일정을 보관함으로 옮겨도 좋다는 확인. */
  confirmArchive?: boolean;
}

export interface ShrinkPreview {
  movedActivityCount: number;
  /** 체크아웃일이 당겨질 숙소 수. */
  shrunkStayCount: number;
  /** 묵는 밤이 없어져 지워질 숙소 수. */
  removedStayCount: number;
}

export async function createTrip(body: TripWriteRequest): Promise<TripDetail> {
  const { data } = await client.post<ApiEnvelope<TripDetail>>(
    "/travel/trips",
    body,
  );
  return data.data;
}

export async function updateTrip(
  tripId: number,
  body: TripWriteRequest,
): Promise<TripDetail> {
  const { data } = await client.put<ApiEnvelope<TripDetail>>(
    `/travel/trips/${tripId}`,
    body,
  );
  return data.data;
}

/** 이 기간으로 바꾸면 보관함으로 갈 일정 수. 확인 모달의 문구에 그대로 들어간다. */
export async function fetchShrinkPreview(
  tripId: number,
  startDate: string,
  endDate: string,
): Promise<ShrinkPreview> {
  const { data } = await client.get<ApiEnvelope<ShrinkPreview>>(
    `/travel/trips/${tripId}/shrink-preview`,
    { params: { startDate, endDate } },
  );
  return data.data;
}

export async function deleteTrip(tripId: number): Promise<void> {
  await client.delete(`/travel/trips/${tripId}`);
}
