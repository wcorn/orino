import type { TripStatus } from "./lib/tripStatus";

export const travelKeys = {
  all: ["travel"] as const,
  summary: ["travel", "summary"] as const,
  trips: (status?: TripStatus) => ["travel", "trips", status ?? "all"] as const,
  trip: (tripId: number) => ["travel", "trip", tripId] as const,
  /** 보드는 보는 날짜마다 따로 캐시한다. `day`는 날짜 문자열 또는 "archive". */
  board: (tripId: number, day: string | undefined) =>
    ["travel", "board", tripId, day ?? "default"] as const,
  boards: (tripId: number) => ["travel", "board", tripId] as const,
  activity: (activityId: number) => ["travel", "activity", activityId] as const,
  /** 여행의 숙소 전체. 날짜별로 나누지 않는다 — 어느 날짜에 붙는지는 기간에서 파생한다. */
  stays: (tripId: number) => ["travel", "stays", tripId] as const,
  /**
   * 준비 목록 전체. 분류별로 나누지 않는다 — 진행률과 기한 지남 개수는 전체를 봐야 나오고,
   * 분류를 따로 부르면 목록과 진행률이 서로 다른 순간의 값을 보게 된다.
   */
  prep: (tripId: number) => ["travel", "prep", tripId] as const,
  /** 경비. 여행 하나를 통째로 읽는다 — 날짜 그룹도 합계도 한 응답에서 나온다. */
  expenses: (tripId: number) => ["travel", "expenses", tripId] as const,
  /** 파생 구간. 저장값이 아니라 날짜에서 매번 계산되므로 날짜를 바꾸면 함께 무효화한다. */
  cityLegs: (tripId: number) => ["travel", "cityLegs", tripId] as const,
  /**
   * 장소 검색. 서버가 이미 캐시하지만, 뒤로 갔다 오면 결과가 그대로 있어야 한다.
   *
   * <p>기준 도시가 키에 들어간다 — 같은 검색어라도 편향 도시가 다르면 다른 결과다.
   */
  placeSearch: (q: string, tripId?: number, cityPlaceId?: number | null) =>
    [
      "travel",
      "places",
      "search",
      tripId ?? "none",
      cityPlaceId ?? "none",
      q,
    ] as const,
  citySearch: (q: string) => ["travel", "places", "cities", q] as const,
  place: (placeId: number) => ["travel", "place", placeId] as const,
  weather: (tripId: number) => ["travel", "weather", tripId] as const,
  fx: (base: string, quote: string) => ["travel", "fx", base, quote] as const,
};
