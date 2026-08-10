import type { BoardDay } from "@/features/travel/api/activities";

/**
 * 구간 하나 — 연속된 같은 기준 도시 날짜의 묶음.
 *
 * @param legIndex 1부터. 같은 도시를 다시 방문하면 다른 번호다
 * @param days 머무는 일수(당일 포함)
 */
export interface Leg {
  legIndex: number;
  cityPlaceId: number | null;
  cityName: string | null;
  days: number;
  startDate: string;
  endDate: string;
}

/**
 * 날짜에서 구간을 **파생**한다. 서버도 같은 규칙을 갖고 있고(`LegDeriver`), 여기서 다시
 * 계산하는 이유는 **보드 응답 하나로 화면이 성립해야** 하기 때문이다 — 구간을 알기 위해
 * 따로 조회하면 오프라인에서 탭 표기가 무너진다.
 *
 * **구간을 저장하지 않는 이유**(D-21): 저장하면 날짜와 구간이 어긋날 수 있는 상태가 두 개
 * 생긴다. 하루의 기준 도시를 바꿨는데 구간이 그대로면 화면 두 곳이 서로 다른 답을 보여준다.
 *
 * 하루만 바꿔서 구간이 셋으로 쪼개지는 것(도쿄/닛코/도쿄)은 **정상**이다. 같은 도시라도
 * 사이에 다른 도시가 끼면 다른 구간이다 — "언제 어디에 있었나"가 구간의 뜻이기 때문이다.
 *
 * @param days **날짜 오름차순**으로 정렬된 여행 날짜. 순서가 어긋나면 구간도 어긋난다
 */
export function deriveLegs(days: BoardDay[]): Leg[] {
  const legs: Leg[] = [];

  for (const day of days) {
    const cityPlaceId = day.baseCity?.placeId ?? null;
    const last = legs[legs.length - 1];

    if (last && last.cityPlaceId === cityPlaceId) {
      last.endDate = day.date;
      last.days += 1;
      continue;
    }
    legs.push({
      legIndex: legs.length + 1,
      cityPlaceId,
      cityName: day.baseCity?.name ?? null,
      days: 1,
      startDate: day.date,
      endDate: day.date,
    });
  }
  return legs;
}
