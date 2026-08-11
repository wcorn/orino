import type { CityLeg } from "@/features/travel/api/travel";

/** 지도에 찍히는 도시 하나. */
export interface CityMarker {
  cityPlaceId: number;
  /** **첫 방문** 구간 번호. 같은 도시를 다시 들러도 처음 번호를 그대로 쓴다. */
  legIndex: number;
  cityName: string;
  lat: number;
  lng: number;
}

interface LatLng {
  lat: number;
  lng: number;
}

/**
 * 구간에서 도시 마커를 만든다 — **도시당 하나**.
 *
 * <p>도쿄 → 닛코 → 도쿄처럼 같은 도시를 다시 방문하면 구간은 셋이지만 지도 위의 점은 둘이다.
 * 번호는 <b>첫 방문 구간</b> 것을 쓴다 — 같은 점에 2와 3을 겹쳐 쓸 수 없고, 둘 중 나중 것을
 * 고르면 "도쿄가 3번째 도시"로 읽혀 여행의 시작이 어디였는지 흐려진다.
 *
 * <p>좌표가 없는 도시(직접 입력)는 뺀다. 지도에 찍을 자리가 없다.
 */
export function cityMarkers(legs: CityLeg[]): CityMarker[] {
  const byCity = new Map<number, CityMarker>();
  for (const leg of legs) {
    if (leg.lat === null || leg.lng === null) continue;
    if (byCity.has(leg.cityPlaceId)) continue;
    byCity.set(leg.cityPlaceId, {
      cityPlaceId: leg.cityPlaceId,
      legIndex: leg.legIndex,
      cityName: leg.cityName ?? "도시 없음",
      lat: leg.lat,
      lng: leg.lng,
    });
  }
  return [...byCity.values()];
}

/**
 * 연결선은 <b>구간 순서 그대로</b>다 — 마커처럼 접지 않는다.
 *
 * <p>도쿄 → 닛코 → 도쿄는 점 둘, 선은 갔다 오는 두 획이다. 마커를 따라 선을 그으면 닛코에
 * 갔다 온 사실이 사라진다.
 */
export function legPath(legs: CityLeg[]): LatLng[] {
  return legs
    .filter((leg) => leg.lat !== null && leg.lng !== null)
    .map((leg) => ({ lat: leg.lat as number, lng: leg.lng as number }));
}
