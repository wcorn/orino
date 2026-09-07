import type { BaseCity, BoardDay } from "@/features/travel/api/activities";

/** 도시 판정에 필요한 최소한. 일정의 장소든 숙소의 장소든 이 둘만 있으면 된다. */
export interface PlaceCity {
  cityName: string | null;
  cityPlaceRef: string | null;
}

/**
 * 장소가 속한 도시의 <b>표시명</b>.
 *
 * <p><b>장소가 아는 이름이 먼저다</b>(#1373). {@code cityPlaceRef}는 「이 장소가 어느 도시에
 * 있는가」가 아니라 <b>「어느 도시 칩으로 담았는가」</b>다(§2.7) — 교토에 묵으면서 나라
 * 당일치기를 짜면 나라 장소에 교토가 찍힌다. 그 값으로 이름을 지으면 나라역이 「교토시」가
 * 된다. 꼬리표는 도시 이탈 배지에 붙는 이름이라, 틀리면 경고 자체를 못 믿게 된다.
 *
 * <p>대신 표기 통일을 잃는다 — 도시 검색은 도시 장소의 표시명(`오사카시`)을 주고 장소 상세는
 * 주소 구성요소의 {@code locality}(`Osaka`, 때로는 구 단위인 `Shinjuku City`)를 주므로,
 * 같은 도시가 화면마다 다른 글자로 보일 수 있다. <b>거짓 이름보다는 낫다</b>고 봤다.
 *
 * <p>장소가 자기 도시를 모르면 담을 때의 칩으로 떨어진다 — 그때는 그것이 우리가 가진
 * 전부다. 둘 다 없으면 null이라 화면이 그 자리를 비운다.
 *
 * <p><b>도시 이탈 판정은 그대로 식별자로 한다</b>(D-23). 이름으로 판정하면 위의 표기
 * 흔들림에 깨져 멀쩡한 일정에 경고가 붙는다 — 이름은 보여 주는 값이고, 식별자는 가르는 값이다.
 */
export function cityLabelOf(
  place: PlaceCity | null | undefined,
  cities: BaseCity[],
): string | null {
  if (!place) return null;
  if (place.cityName) return place.cityName;
  const chip = place.cityPlaceRef
    ? cities.find((city) => city.cityPlaceRef === place.cityPlaceRef)
    : undefined;
  return chip?.name ?? null;
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
