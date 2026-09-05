import { client } from "@/shared/api";

import type { TripStatus } from "../lib/tripStatus";
import type { PrepSummary } from "./prep";

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

/**
 * 여행이 거쳐 가는 도시. **줄이는 규칙은 화면이 갖고, 서버는 줄이기 전의 사실만 준다.**
 *
 * <p>목록·요약이 이미 읽어 둔 날짜별 기준 도시에서 나오므로 추가 조회가 없다 — 여행마다
 * `/city-legs`를 부르면 그때부터 N+1이다.
 */
export interface TripCitySummary {
  /** 구간 순서의 도시 이름. 연속으로 같은 도시는 이미 한 번으로 접혀 있다. */
  names: string[];
  /** 서로 다른 도시 수. 같은 도시를 다시 방문해도 하나로 센다. */
  count: number;
  /** 오늘의 기준 도시. 진행 중이 아니면 null. */
  today: string | null;
  /** 오늘 도시가 바뀌었다면 어제의 도시. 아니면 null. */
  movedFrom: string | null;
  todayDayIndex: number | null;
  todayTimezone: string | null;
  todayCurrency: string | null;
}

/** 진행 중 여행 — 탭하면 곧바로 보드로 간다. */
export interface OngoingTripSummary {
  id: number;
  title: string;
  /** 서버가 조립해 주는 보드 경로. 프론트가 경로를 다시 만들지 않는다. */
  boardPath: string;
  /** 준비 화면 경로. 보드와 같은 규칙으로 서버가 조립한다. */
  prepPath: string;
  startDate: string;
  endDate: string;
  activityCount: number;
  cities: TripCitySummary;
  /**
   * 준비 진행률·기한 지남 개수. <b>화면이 다시 세지 않는다</b> — 사이드바 배지와 준비 화면
   * 상단이 다른 값을 말하면, 무엇을 눌러야 배지가 사라지는지 알 수 없다.
   */
  prep: PrepSummary;
}

/** 다음 예정 여행 — D-day 카운트다운 카드. */
export interface NextTripSummary {
  id: number;
  title: string;
  destinationName: string;
  prepPath: string;
  startDate: string;
  endDate: string;
  /** 시작일까지 남은 일수. 여행 타임존 기준이라 프론트에서 다시 계산하지 않는다. */
  dDay: number;
  activityCount: number;
  cities: TripCitySummary;
  /**
   * 준비 요약이 <b>예정 여행에도 붙는다.</b> 준비는 출발 전에 값을 내는 기능이라, 여행이
   * 시작된 뒤에만 배지가 뜨면 정작 필요한 동안 아무 말도 하지 않는다(명세 v2.2 §13).
   */
  prep: PrepSummary;
}

/** 가장 최근에 끝난 여행. */
export interface CompletedTripSummary {
  id: number;
  title: string;
  endDate: string;
  activityCount: number;
}

/** 여행 하나의 경비 한 줄. 예산을 안 정했으면 `budget`이 null이다(0이 아니다). */
export interface TripExpenseSummary {
  budget: number | null;
  spent: number;
}

/**
 * 사이드바 여행 트리 한 줄(API §2.1). 폴백 화면도 <b>같은 배열</b>을 읽는다 — 따로 만들면
 * 사이드바에는 있는데 폴백에는 없는 여행이 생기고, 그 여행은 고를 수 없는 채로 남는다.
 *
 * <p>다녀온 여행은 여기 없다 — 개수만 `completedCount`로 온다(D-39).
 */
export interface SidebarTripSummary {
  id: number;
  title: string;
  status: TripStatus;
  startDate: string;
  endDate: string;
  /** 출발까지 남은 일수. <b>예정일 때만</b> 찬다. */
  dDay: number | null;
  /** 오늘이 며칠째인지(첫날이 1). <b>진행 중일 때만</b> 찬다. */
  dayNumber: number | null;
  /** 항목이 하나도 없어도 `{0,0,0}`이다 — 「모른다」가 아니라 0개다. */
  prep: PrepSummary;
  expense: TripExpenseSummary;
}

/**
 * `/select` 카드와 여행 홈(S-01)이 함께 쓰는 요약.
 * 앞의 셋이 다 null이면 여행을 한 번도 만들지 않은 상태다.
 */
export interface TravelSummary {
  ongoing: OngoingTripSummary | null;
  next: NextTripSummary | null;
  recentCompleted: CompletedTripSummary | null;
  /** 진행 중·예정 전부. 진행 중 → 예정, 각각 시작일 오름차순. */
  trips: SidebarTripSummary[];
  /** 다녀온 여행 수. 사이드바의 「다녀온 여행 N개」 한 줄이 쓴다. */
  completedCount: number;
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
  cities: TripCitySummary;
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

/**
 * 파생된 구간. **저장된 것이 아니라 날짜에서 매번 계산한다**(D-21) — 수정 화면이 이걸로
 * 초기값을 채우면 날짜와 어긋날 수가 없다.
 */
export interface CityLeg {
  legIndex: number;
  cityPlaceId: number;
  cityName: string | null;
  days: number;
  startDate: string;
  endDate: string;
  timezone: string | null;
  lat: number | null;
  lng: number | null;
}

export async function fetchCityLegs(tripId: number): Promise<CityLeg[]> {
  const { data } = await client.get<ApiEnvelope<CityLeg[]>>(
    `/travel/trips/${tripId}/city-legs`,
  );
  return data.data;
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

/**
 * 고른 지출을 이 여행에 붙인다(명세 v2.2 §18).
 *
 * <p>가계부가 아니라 <b>여행</b> API인 이유 — 「어느 여행의 지출인가」를 정하는 것은 여행이
 * 아는 일이다. 가계부에 두면 가계부가 여행의 존재와 소유권을 알아야 하고, 그때부터 의존이
 * 양방향이 된다(의존은 여행 → 가계부 한 방향).
 *
 * <p>지출을 <b>만드는</b> 곳은 여전히 가계부 API다. 여기서 하는 것은 연결뿐이다.
 */
export async function attachExpensesToTrip(
  tripId: number,
  transactionIds: number[],
): Promise<{ affected: number }> {
  const { data } = await client.post<ApiEnvelope<{ affected: number }>>(
    `/travel/trips/${tripId}/expenses/attach`,
    { transactionIds },
  );
  return data.data;
}

/** 이 여행에서 뗀다. 거래는 지우지 않고 연결만 끊는다. */
export async function detachExpensesFromTrip(
  tripId: number,
  transactionIds: number[],
): Promise<{ affected: number }> {
  const { data } = await client.post<ApiEnvelope<{ affected: number }>>(
    `/travel/trips/${tripId}/expenses/detach`,
    { transactionIds },
  );
  return data.data;
}
