import type { BaseCity, BoardDay } from "@/features/travel/api/activities";

/** 도시 판정에 필요한 최소한. 일정의 장소든 숙소의 장소든 이 둘만 있으면 된다. */
export interface PlaceCity {
  cityName: string | null;
  cityPlaceRef: string | null;
}

/**
 * 장소가 속한 도시의 <b>표시명</b>.
 *
 * <p>여행 도시 중 식별자가 맞는 것이 있으면 <b>그 도시의 이름</b>을 쓴다. 날짜 탭·구간
 * 리스트·보관함 그룹이 쓰는 것과 같은 이름이라, 화면 안에서 같은 도시가 같은 글자로 보인다.
 *
 * <p>맞추지 않고 장소가 준 이름을 그대로 쓰면 표기가 갈린다 — 두 값이 서로 다른 Google
 * 필드에서 오기 때문이다. 도시 검색은 도시 장소의 표시명(`오사카시`)을, 장소 상세는 주소
 * 구성요소의 `locality`(`Osaka`, 때로는 구 단위인 `Shinjuku City`)를 준다.
 *
 * <p>못 맞추면 장소가 들고 온 이름으로 떨어진다 — 여행에 없는 도시일 수 있고, 그때는 그
 * 이름이 우리가 가진 전부다. 이름조차 없으면 null이라 화면이 그 자리를 비운다.
 *
 * <p>비교는 <b>식별자로만</b> 한다(D-23). 이름으로 맞추면 바로 그 표기 흔들림에 깨진다.
 */
export function cityLabelOf(
  place: PlaceCity | null | undefined,
  cities: BaseCity[],
): string | null {
  if (!place) return null;
  const known = place.cityPlaceRef
    ? cities.find((city) => city.cityPlaceRef === place.cityPlaceRef)
    : undefined;
  return known?.name ?? place.cityName;
}

/** 날짜 목록에서 바로 부르는 자리용. 도시 목록을 따로 만들지 않아도 된다. */
export function cityLabelOfDays(
  place: PlaceCity | null | undefined,
  days: BoardDay[],
): string | null {
  return cityLabelOf(
    place,
    days.flatMap((day) => (day.baseCity ? [day.baseCity] : [])),
  );
}
