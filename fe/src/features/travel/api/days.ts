import { client } from "@/shared/api";

import type { BaseCity } from "./activities";

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

/**
 * 날짜 하나. 보드 응답의 `days[]`와 같은 내용이되 일정·날씨가 없다 — 날짜만 필요한 화면과
 * 기준 도시 변경 응답이 쓴다.
 */
export interface TripDay {
  dayId: number;
  dayIndex: number;
  date: string;
  weekday: string;
  legIndex: number;
  cityChanged: boolean;
  cityMemo: string | null;
  baseCity: BaseCity | null;
}

/**
 * 기준 도시 변경 · 도시 메모. <b>보낸 필드만 바뀐다.</b>
 *
 * <p>도시는 구간 입력과 같은 두 방식이다 — 이미 담아 둔 도시(`baseCityPlaceId`)거나 검색 결과
 * 그대로(`baseCityGooglePlaceId`)다. 뒤쪽을 쓰면 서버가 담으면서 도시 식별자를 붙여 주므로,
 * 바꾼 도시에서도 도시 이탈 표시(`outOfBaseCity`)가 계속 성립한다.
 */
export interface DayUpdateRequest {
  baseCityPlaceId?: number;
  baseCityGooglePlaceId?: string;
  /** 빈 문자열이면 메모를 지운다. 생략은 "안 바꿈"이다. */
  cityMemo?: string;
}

/** 응답은 바꾼 날짜 하나가 아니라 기간 전체다 — 하루를 바꾸면 구간이 다시 나뉜다. */
export async function updateDay(
  dayId: number,
  body: DayUpdateRequest,
): Promise<TripDay[]> {
  const { data } = await client.put<ApiEnvelope<TripDay[]>>(
    `/travel/days/${dayId}`,
    body,
  );
  return data.data;
}
