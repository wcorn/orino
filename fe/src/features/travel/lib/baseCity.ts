import type { BaseCity, BoardDay } from "@/features/travel/api/activities";

import type { ZoneByDate } from "./tripStatus";

/**
 * 그 날짜의 기준 도시. 없는 날짜를 물으면 `null`이다 — v2.1에서 기간 내 모든 날짜에는
 * 기준 도시가 있으므로, `null`이 나오면 기간 밖을 물었거나 데이터가 깨진 것이다.
 */
export function cityOn(days: BoardDay[], date: string): BaseCity | null {
  return days.find((day) => day.date === date)?.baseCity ?? null;
}

/**
 * 날짜 → 타임존 조회 함수. 파생 계산(`deriveStatus`·`daysUntil`·`todayOfTrip`)에 그대로 넘긴다.
 *
 * 모르는 날짜를 물으면 **첫날의 타임존**으로 답한다. 기간 밖 날짜를 묻는 쪽은 상태 판정처럼
 * "오늘이 기간 밖일 수 있는" 계산인데, 거기서 타임존이 비면 판정 자체를 못 한다.
 */
export function zoneByDate(days: BoardDay[]): ZoneByDate {
  const fallback = days[0]?.baseCity?.timezone;
  return (date) => cityOn(days, date)?.timezone ?? fallback ?? "UTC";
}

/**
 * D-day 전용 — **첫날** 기준 도시의 타임존. "언제 출발하나"는 출발하는 곳의 시계로 세는
 * 값이라, 날짜별 조회가 필요 없다.
 *
 * 여행 상세·목록 응답의 `timezone`이 곧 첫날 기준 도시의 타임존이다(서버가 그렇게 채운다).
 */
export function firstDayZone(timezone: string): ZoneByDate {
  return () => timezone;
}

/** 기간에 등장하는 **서로 다른** 도시 수. 같은 도시를 다시 방문해도 1이다. */
export function cityCount(days: BoardDay[]): number {
  return new Set(
    days.map((day) => day.baseCity?.placeId).filter((id) => id != null),
  ).size;
}

/**
 * 이 여행에 등장하는 도시들. **구간 순서**로, 같은 도시를 다시 방문해도 한 번만 나온다.
 *
 * <p>기준 도시를 바꾸는 자리(날짜 탭 시트·검색 기준 칩)에서 가장 자주 고르는 후보라 그대로
 * 올린다 — 도쿄↔닛코를 오가는 변경에 매번 검색을 시킬 이유가 없다.
 */
export function tripCities(days: BoardDay[]): BaseCity[] {
  return [
    ...new Map(
      days
        .flatMap((day) => (day.baseCity ? [day.baseCity] : []))
        .map((city) => [city.placeId, city]),
    ).values(),
  ];
}
