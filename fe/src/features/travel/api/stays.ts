import axios from "axios";

import { client } from "@/shared/api";

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

/**
 * 숙소 1건. **기준 도시와 무관하다** — 닛코 당일치기 날의 기준 도시는 닛코지만 자는 곳은
 * 도쿄일 수 있다.
 *
 * <p>어느 날짜에 붙는지는 저장하지 않고 기간에서 파생한다. `checkIn <= 날짜 < checkOut`이
 * 그날 밤 자는 곳이고, `checkOut === 날짜`가 그날 체크아웃하는 곳이다.
 */
export interface Stay {
  stayId: number;
  name: string;
  /** 좌표·도시 판정에 쓴다. 붙이지 않은 숙소는 null. */
  placeId: number | null;
  checkInDate: string;
  checkOutDate: string;
  /** 벽시계 시각(`15:00`). 없으면 null. */
  checkInTime: string | null;
  checkOutTime: string | null;
  bookingUrl: string | null;
  memo: string | null;
  /** 묵는 밤 수. 반열린 구간이라 날짜 차이 그대로다. */
  nights: number;
}

export interface StayWriteRequest {
  name: string;
  placeId?: number | null;
  /**
   * 검색 결과를 그대로 담을 때. 서버가 장소를 upsert해 `placeId`로 연결한다 —
   * 프론트가 장소를 먼저 만들 필요가 없다. `placeId`와 함께 보내지 않는다.
   */
  googlePlaceId?: string | null;
  /**
   * 이 숙소가 **어느 도시에 있는지**. 새로 담기는 장소에 그 도시 식별자가 함께 저장돼
   * 숙소 이동의 도시 경계 판정이 성립한다(§3.4).
   *
   * <p>기준 도시에서 가져오지 않고 **사용자가 고른다** — 닛코 당일치기 날의 기준 도시는
   * 닛코지만 자는 곳은 도쿄일 수 있다.
   */
  cityPlaceId?: number | null;
  checkInDate: string;
  checkOutDate: string;
  checkInTime?: string | null;
  checkOutTime?: string | null;
  bookingUrl?: string | null;
  memo?: string | null;
}

/** 겹침 409가 함께 주는 **어느 숙소와 겹쳤는지**. 이 값이 있어야 안내가 안내가 된다. */
export interface StayOverlap {
  stayId: number;
  name: string;
  checkInDate: string;
  checkOutDate: string;
}

/** 기간 겹침. 서버가 이 코드와 함께 겹치는 숙소를 내려준다. */
const OVERLAP_CODE = "TRAVEL-ERR-017";

/**
 * 겹침 거절이면 **어느 숙소와 겹쳤는지**를 꺼낸다. 아니면 null.
 *
 * <p>"이미 숙소가 잡힌 기간입니다"만으로는 사용자가 무엇을 고쳐야 할지 모른다 — 겹친 숙소의
 * 이름과 기간을 그대로 보여줘야 "그 숙소를 먼저 줄이자"가 된다.
 */
export function stayOverlapOf(error: unknown): StayOverlap | null {
  if (!axios.isAxiosError(error)) return null;
  const body = error.response?.data as
    | { code?: string; data?: StayOverlap }
    | undefined;
  if (body?.code !== OVERLAP_CODE) return null;
  return body.data ?? null;
}

/** 여행의 숙소 전체. `checkInDate` 오름차순. */
export async function fetchStays(tripId: number): Promise<Stay[]> {
  const { data } = await client.get<ApiEnvelope<Stay[]>>(
    `/travel/trips/${tripId}/stays`,
  );
  return data.data;
}

export async function createStay(
  tripId: number,
  body: StayWriteRequest,
): Promise<Stay> {
  const { data } = await client.post<ApiEnvelope<Stay>>(
    `/travel/trips/${tripId}/stays`,
    body,
  );
  return data.data;
}

/** 수정은 **전체 교체**다 — 생략한 필드는 비워진다. */
export async function updateStay(
  stayId: number,
  body: StayWriteRequest,
): Promise<Stay> {
  const { data } = await client.put<ApiEnvelope<Stay>>(
    `/travel/stays/${stayId}`,
    body,
  );
  return data.data;
}

export async function deleteStay(stayId: number): Promise<void> {
  await client.delete(`/travel/stays/${stayId}`);
}
