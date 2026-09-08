import type { BaseCity, BoardDay } from "@/features/travel/api/activities";

/** 도시 판정에 필요한 최소한. 일정의 장소든 숙소의 장소든 이것만 있으면 된다. */
export interface PlaceCity {
  cityName: string | null;
  /** 광역 행정구역(현·부·도). 있으면 이것이 이름이 된다 — 아래 참고. */
  adminArea?: string | null;
  cityPlaceRef: string | null;
}

/**
 * 장소가 속한 도시의 <b>표시명</b>. 세 값을 이 순서로 본다(#1375).
 *
 * <p><b>1. 광역 행정구역</b>(현·부·도). 구글이 주는 `locality`는 같은 도시인데도 갈린다 —
 * 한국어가 있는 것과 없는 것(`나라시` / `Nara`)이 섞이고, 경계 근처 장소는 인접 시로
 * 잡힌다(나라마치 → `야마토코리야마시`). 사용자에게는 전부 「나라」인데 화면이 세 가지로
 * 말하던 것을 현으로 묶는다. 대신 단위가 여행의 기준 도시(`오사카시`)와 어긋날 수 있다.
 *
 * <p><b>2. 장소가 아는 도시 이름</b>(`locality`). 이 칼럼이 생기기 전에 담긴 장소는 현이
 * 비어 있다 — 「도시 이름 새로고침」을 누르기 전까지는 옛 규칙으로 그린다.
 *
 * <p><b>3. 담을 때 누른 도시 칩.</b> 마지막이다. {@code cityPlaceRef}는 「이 장소가 어느
 * 도시에 있는가」가 아니라 <b>「어느 도시 칩으로 담았는가」</b>라서(§2.7), 교토에서 담은
 * 나라 장소를 「교토시」라고 부르게 된다(#1373). 장소가 자기 도시를 아무것도 모를 때만 쓴다.
 *
 * <p><b>도시 이탈 판정은 그대로 식별자로 한다</b>(D-23). 이름으로 판정하면 위의 표기
 * 흔들림에 깨져 멀쩡한 일정에 경고가 붙는다 — 이름은 보여 주는 값이고, 식별자는 가르는 값이다.
 */
export function cityLabelOf(
  place: PlaceCity | null | undefined,
  cities: BaseCity[],
): string | null {
  if (!place) return null;
  if (place.adminArea) return place.adminArea;
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
