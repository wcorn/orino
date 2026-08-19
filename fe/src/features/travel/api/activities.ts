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
  /** 그날의 기준 도시. 도시가 바뀌는 날이면 **도착한 쪽**이다. */
  baseCity: BaseCity | null;
  /** 직전 날짜와 도시가 다르다 → 탭 왼쪽에 구분선. */
  cityChanged: boolean;
  /**
   * 그날 **떠나온 도시**. 도시가 바뀌는 날에만 있다(D-25) — 그 하루는 두 도시에 속한다.
   *
   * <p>오프라인 캐시(Workbox)에 이 필드가 생기기 전 응답이 남아 있을 수 있어 선택이다.
   * 없으면 이동일 표시가 빠질 뿐, 화면은 그대로 산다.
   */
  arrivingFrom?: BaseCity | null;
  /** 이 날짜가 속한 구간 번호(1부터). 저장값이 아니라 파생이다. */
  legIndex: number;
  cityMemo: string | null;
  /** 예보 범위(16일) 밖이면 null이다 — 오류가 아니라 "아직 모름"이다. */
  weather: DailyWeather | null;
  /** 떠나온 도시의 그날 날씨. 이동일에만 있다 — 오전에 뭘 입을지는 그 도시가 정한다. */
  arrivingFromWeather?: DailyWeather | null;
  /** 오늘 밤 자는 곳(`checkIn <= date < checkOut`). 없으면 null. */
  stayTonight: StayTonight | null;
  /** 그날 체크아웃하는 곳. 없으면 null. */
  stayCheckout: StayCheckout | null;
}

/**
 * 오늘 밤 자는 곳.
 *
 * <p>`sameCity`는 그날 기준 도시와 같은 도시인가다 — 닛코 당일치기 날 도쿄에서 자면 false.
 * 식별자가 한쪽이라도 없으면 판정하지 않고 같은 도시로 본다(D-23).
 */
export interface StayTonight {
  stayId: number;
  name: string;
  sameCity: boolean;
  /** 벽시계 시각(`15:00`). 없으면 null. */
  checkInTime: string | null;
  /** 그날 체크인하는 날인가 — 배지에 시각을 함께 보여줄지 가른다. */
  isCheckInDay: boolean;
}

export interface StayCheckout {
  stayId: number;
  name: string;
  checkOutTime: string | null;
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

/**
 * 이동수단의 **분류**. 아이콘과 묶음에만 쓴다 — 실제로 무엇을 타는지는 `name`에 적는다.
 *
 * <p>나라 고유명(신칸센·TGV)을 값으로 두지 않는다. 일본 밖에서 못 쓰게 되고, 다음엔 다른
 * 나라 열차를 넣어 달라는 요청이 이어진다. `TRAIN` + `노조미 21호`면 그 줄이 끊긴다.
 */
export type TravelMode =
  | "WALK"
  | "BIKE"
  | "BUS"
  | "CAR"
  | "SUBWAY"
  | "TRAIN"
  | "FLIGHT"
  | "FERRY"
  | "OTHER";

/**
 * 이동 한 건(#1208). **사용자가 직접 적는다** — 앱이 계산하지 않는다.
 *
 * <p>아직 아무것도 적지 않은 구간도 행으로 내려온다(`mode`가 null). 그 빈 행이 곧 입력
 * 지점이라, 응답에서 빼면 화면에 누를 곳이 없어진다.
 */
export interface Move {
  fromActivityId: number;
  /** 숙소로 가는 이동이면 null. */
  toActivityId: number | null;
  /** 일정 사이 이동이면 null. */
  toStayId: number | null;
  /** null이면 아직 적지 않은 구간이다. */
  mode: TravelMode | null;
  /** 실제로 타는 것 — `나리타 익스프레스 3호` · `피치 MM8`. */
  name: string | null;
  /** 수단만 먼저 정하고 시간은 나중에 확인할 수 있어 null을 허용한다. */
  durationMinutes: number | null;
  /** 예매·확인 링크. */
  url: string | null;
  /** 좌석·플랫폼·예약번호. */
  memo: string | null;
}

export interface Board {
  trip: BoardTrip;
  days: BoardDay[];
  /** 이번 응답의 activities가 속한 날짜. null이면 보관함을 보고 있다. */
  selectedDate: string | null;
  archiveCount: number;
  activities: Activity[];
  /**
   * 연속한 두 일정 사이 이동. 장소 없는 일정은 건너뛴 결과다.
   *
   * <p>아직 안 적은 구간도 빈 값으로 들어 있다 — 그 자리가 입력 지점이다.
   */
  moves: Move[];
  /**
   * 그날 마지막 일정 → 오늘 밤 숙소 이동. 숙소가 없거나, 마지막 일정이 이미 그 숙소거나,
   * 숙소에 장소가 안 붙어 있으면 null이다(도착지가 없으면 적을 수도 없다).
   */
  stayMove: Move | null;
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
  /**
   * (v2.1) 이 장소를 **어느 도시를 기준으로 찾았는지**(S-06 검색 기준 도시 칩).
   * `googlePlaceId`로 새로 담기는 장소에만 쓰이며, 그 도시의 식별자가 장소에 함께 저장돼
   * 보관함 도시 그룹·도시 이탈 표시가 성립한다. 없으면 장소는 도시 없이 저장된다.
   */
  cityPlaceId?: number | null;
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

export interface MoveWriteRequest {
  fromActivityId: number;
  /** `toStayId`와 정확히 하나만 보낸다. */
  toActivityId?: number | null;
  toStayId?: number | null;
  mode: TravelMode;
  name?: string | null;
  durationMinutes?: number | null;
  url?: string | null;
  memo?: string | null;
}

/**
 * 이동을 저장한다. 등록·수정이 같은 요청이고, 한 구간에 이동은 하나라 덮어쓴다.
 *
 * <p>양 끝을 **일정 id로** 보낸다 — 서버가 장소로 옮겨 저장한다. 화면이 장소 id를 들고
 * 다니면 저장 단위를 바꿀 때마다 화면이 함께 흔들린다.
 */
export async function saveMove(
  tripId: number,
  body: MoveWriteRequest,
): Promise<Move> {
  const { data } = await client.put<ApiEnvelope<Move>>(
    `/travel/trips/${tripId}/moves`,
    body,
  );
  return data.data;
}

export async function deleteMove(
  tripId: number,
  from: number,
  to: { activityId: number } | { stayId: number },
): Promise<void> {
  await client.delete(`/travel/trips/${tripId}/moves`, {
    params:
      "activityId" in to
        ? { from, to: to.activityId }
        : { from, toStay: to.stayId },
  });
}

/** 날짜 하나의 전체 순서. 부분 갱신은 보내지 않는다(순서가 비결정적이 된다). */
export interface ReorderMove {
  /** null이면 미배정 보관함. */
  date: string | null;
  activityIds: number[];
}

/**
 * 드래그 결과를 한 번에 반영한다. 응답에 **다시 이어진 구간의 이동이 담겨** 온다 —
 * 드래그는 손을 뗀 순간 결과가 보여야 해서 한 번 더 왕복하지 않는다.
 *
 * <p>순서가 바뀌면 저장된 이동이 사라지는 게 아니라 **어느 자리에 오는지**가 바뀐다.
 * 이동은 장소 쌍에 붙어 있다.
 *
 * <p>다른 날짜로 옮기는 것은 이 엔드포인트가 아니라 {@link updateActivity}로 한다 —
 * 서버가 대상 날짜의 맨 뒤에 붙이고 떠나온 날짜를 재인덱싱해 주기 때문에, 대상 날짜의
 * 기존 순서를 모르는 화면에서도 정확히 "끝에 추가"가 된다.
 */
export async function reorderActivities(
  tripId: number,
  moves: ReorderMove[],
): Promise<Move[]> {
  const { data } = await client.put<ApiEnvelope<{ moves: Move[] }>>(
    `/travel/trips/${tripId}/activities/order`,
    { moves },
  );
  return data.data.moves;
}
