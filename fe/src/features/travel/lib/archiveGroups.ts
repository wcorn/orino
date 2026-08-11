import type {
  Activity,
  BaseCity,
  BoardDay,
} from "@/features/travel/api/activities";
import { cityLabelOf } from "@/features/travel/lib/cityLabel";

export interface ArchiveGroup {
  /** 목록 key. 도시 식별자이거나 `other`·`none`이다. */
  key: string;
  label: string;
  activities: Activity[];
}

/** 여행의 어느 기준 도시도 아닌 장소. */
const OTHER = "other";
/** 장소가 없거나 도시 식별자가 없는 장소. */
const NONE = "none";

/**
 * 보관함을 <b>도시별로</b> 묶는다(§3.8).
 *
 * <p>다구간 여행에서 보관함이 평면 리스트면 "교토에서 갈 곳"과 "나고야에서 갈 곳"이 섞여,
 * 정작 교토 날짜를 짜는 동안 목록을 위아래로 훑어야 한다. 도시로 묶으면 그 도시를 짜는 동안
 * 볼 것이 한 덩어리가 된다.
 *
 * <p><b>묶는 기준은 도시 식별자(`cityPlaceRef`)뿐이다</b> — 좌표 거리로도, 도시 이름으로도
 * 추측하지 않는다(D-23). 이름으로 묶으면 "교토"라는 같은 글자를 쓰는 다른 장소가 한 덩어리가
 * 되고, 그렇게 묶인 목록은 틀렸다는 사실조차 드러나지 않는다.
 *
 * <p>그래서 <b>식별자가 없는 장소는 `도시 없음`으로 떨어진다.</b> 억지로 묶는 것보다 낫다 —
 * 검색으로 담은 장소에 식별자를 채우는 일은 3단계에서 온다.
 *
 * <p>그룹 순서는 <b>구간 순서</b>다(날짜 순서 = 방문 순서). `기타`·`도시 없음`은 맨 뒤다.
 */
export function groupArchiveByCity(
  activities: Activity[],
  days: BoardDay[],
): ArchiveGroup[] {
  const cities = cityOrder(days);
  const groups = new Map<string, ArchiveGroup>();

  // 도시 그룹은 활동이 없어도 만들지 않는다 — 빈 헤더만 늘어선 목록은 읽을 것이 없다.
  for (const activity of activities) {
    const ref = activity.place?.cityPlaceRef ?? null;
    const city = ref === null ? undefined : cities.get(ref);
    const key = city ? ref! : ref === null ? NONE : OTHER;
    // 그룹 이름도 같은 규칙을 탄다 — 여기만 맞고 일정 행이 어긋나면 그게 더 헷갈린다.
    const label = city
      ? (cityLabelOf(activity.place, [city]) ?? city.name)
      : ref === null
        ? "도시 없음"
        : "기타";
    const group = groups.get(key) ?? { key, label, activities: [] };
    group.activities.push(activity);
    groups.set(key, group);
  }

  const order = [...cities.keys()];
  return [...groups.values()].sort(
    (a, b) => rank(a.key, order) - rank(b.key, order),
  );
}

/** 여행에 등장하는 도시를 <b>처음 나오는 날짜 순서</b>로 — 그게 곧 구간 순서다. */
function cityOrder(days: BoardDay[]): Map<string, BaseCity> {
  const cities = new Map<string, BaseCity>();
  for (const day of days) {
    const ref = day.baseCity?.cityPlaceRef;
    if (day.baseCity && ref && !cities.has(ref)) {
      cities.set(ref, day.baseCity);
    }
  }
  return cities;
}

/** `기타`와 `도시 없음`은 도시 뒤에, 그 둘 사이에서는 `기타`가 앞이다. */
function rank(key: string, order: string[]): number {
  if (key === NONE) return order.length + 1;
  if (key === OTHER) return order.length;
  return order.indexOf(key);
}

/**
 * 담을 날짜를 고르는 목록을 정렬한다 — <b>그 장소의 도시가 기준 도시인 날짜를 위로</b>.
 *
 * <p>오사카 가게를 담을 때 오사카 날짜가 위에 있어야 한다. 나머지는 원래 순서(날짜 순)를
 * 그대로 지킨다 — 위로 올린 것 말고는 아무것도 흔들지 않는다.
 *
 * <p>장소의 도시를 모르면(식별자가 없으면) 아무것도 올리지 않는다. 모르는 것을 근거로 순서를
 * 바꾸면 사용자는 왜 그 날짜가 위에 있는지 알 수 없다.
 */
export function daysForPlace(
  days: BoardDay[],
  cityPlaceRef: string | null | undefined,
): BoardDay[] {
  if (!cityPlaceRef) return days;
  const matches = days.filter(
    (day) => day.baseCity?.cityPlaceRef === cityPlaceRef,
  );
  if (matches.length === 0) return days;
  return [
    ...matches,
    ...days.filter((day) => day.baseCity?.cityPlaceRef !== cityPlaceRef),
  ];
}
