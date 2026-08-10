import type { TripCitySummary } from "@/features/travel/api/travel";

/** 이 개수를 넘으면 가운데를 줄인다. 카드 한 줄에 들어가는 한계이기도 하다. */
const MAX_SHOWN = 4;
const ELLIPSIS = "…";

/**
 * 여행이 거쳐 가는 도시를 한 줄로 — `오사카 → 교토 → … → 도쿄`.
 *
 * <p>줄이는 규칙이 <b>한 곳에만</b> 있어야 한다. `/select` 카드 · S-01 홈 · S-02 목록이 같은
 * 값을 조금씩 다르게 줄여 쓰는데, 화면마다 따로 만들면 규칙이 세 벌이 되고 그중 하나만 고치는
 * 일이 반드시 생긴다.
 *
 * <ul>
 *   <li><b>연속 중복은 접는다</b> — 서버가 이미 구간으로 접어 주지만, 여기서도 한 번 더 본다.
 *       "도쿄 → 도쿄"는 어떤 경로로 들어와도 읽을 것이 없는 표기다</li>
 *   <li><b>떨어져 있는 중복은 남긴다</b> — 도쿄 → 닛코 → 도쿄에서 마지막 도쿄는 돌아온
 *       것이라, 지우면 여행이 닛코에서 끝난 것처럼 보인다</li>
 *   <li>{@link MAX_SHOWN}개를 넘으면 <b>처음 둘과 마지막</b>만 두고 가운데를 줄인다 — 출발지와
 *       도착지가 여행의 모양을 가장 많이 말한다</li>
 * </ul>
 */
export function formatCityPath(names: string[]): string {
  const path = collapseRepeats(names);
  if (path.length === 0) return "";
  if (path.length <= MAX_SHOWN) return path.join(" → ");
  return [path[0], path[1], ELLIPSIS, path[path.length - 1]].join(" → ");
}

/** `6개 도시`. 도시가 하나뿐이면 셀 것이 없어 빈 문자열이다. */
export function formatCityCount(count: number): string {
  return count > 1 ? `${count}개 도시` : "";
}

/**
 * 카드 한 줄 — `오사카 → 교토 → … → 도쿄 (6개 도시)`.
 *
 * <p>도시가 하나면 이름만 남는다. <b>단일 도시 여행에서 나열은 소음이다</b> — v2.0과 같은
 * 모양으로 보여야 한다.
 */
export function formatCities(cities: TripCitySummary | undefined): string {
  if (!cities || cities.names.length === 0) return "";
  const path = formatCityPath(cities.names);
  const count = formatCityCount(cities.count);
  return count ? `${path} (${count})` : path;
}

/**
 * 오늘 어디에 있는지 — `교토`, 옮기는 날이면 `오사카 → 교토`.
 *
 * <p>진행 중이 아니면 빈 문자열이다. 예정 여행에 "오늘의 도시"를 쓰면 첫날 도시가 오늘인
 * 것처럼 보인다.
 */
export function formatTodayCity(cities: TripCitySummary | undefined): string {
  if (!cities?.today) return "";
  return cities.movedFrom
    ? `${cities.movedFrom} → ${cities.today}`
    : cities.today;
}

/** 붙어 있는 같은 이름을 하나로. 떨어져 있는 중복은 그대로 둔다. */
function collapseRepeats(names: string[]): string[] {
  return names.filter(
    (name, index) => index === 0 || name !== names[index - 1],
  );
}
