import type { City } from "@/features/travel/api/places";
import type { CityLeg } from "@/features/travel/api/travel";

/**
 * 편집 중인 구간 하나. **아직 저장되지 않은 상태**라 도시를 가리키는 방법이 세 가지다.
 *
 * | 출처 | 갖고 있는 것 |
 * |---|---|
 * | 검색으로 고름 | `cityGooglePlaceId` — 서버가 담고 도시로 승격한다 |
 * | 수정 화면 초기값 | `cityPlaceId` — 이미 저장된 도시 |
 * | 직접 입력 | 이름·타임존·통화만 — 저장 직전에 도시로 만든다 |
 *
 * `key`는 화면용 식별자다. 같은 도시를 두 구간에 넣을 수 있으므로(도쿄 → 닛코 → 도쿄)
 * 도시 id를 React key로 쓸 수 없다.
 */
export interface LegDraft {
  key: string;
  cityName: string;
  cityGooglePlaceId: string | null;
  cityPlaceId: number | null;
  timezone: string;
  currency: string;
  lat: number | null;
  lng: number | null;
  days: number;
}

let sequence = 0;

/** 화면용 키. 순서를 바꿔도 행이 유지돼야 입력 포커스가 튀지 않는다. */
export function nextLegKey(): string {
  sequence += 1;
  return `leg-${sequence}`;
}

/** 검색으로 고른 도시로 구간을 만든다. 타임존·통화는 서버가 확정해 준 값이다. */
export function legFromCity(city: City, days = 1): LegDraft {
  return {
    key: nextLegKey(),
    cityName: city.name,
    cityGooglePlaceId: city.googlePlaceId,
    cityPlaceId: null,
    timezone: city.timezone,
    currency: city.currency,
    lat: city.lat,
    lng: city.lng,
    days,
  };
}

/** 검색이 막혔을 때 직접 입력한 도시. 저장 직전에 도시 장소로 만든다. */
export function legFromManualCity(
  cityName: string,
  timezone: string,
  currency: string,
  days = 1,
): LegDraft {
  return {
    key: nextLegKey(),
    cityName,
    cityGooglePlaceId: null,
    cityPlaceId: null,
    timezone,
    currency,
    lat: null,
    lng: null,
    days,
  };
}

/** 수정 화면 초기값 — 서버가 날짜에서 파생한 구간을 그대로 편집 상태로 옮긴다. */
export function legFromSaved(leg: CityLeg): LegDraft {
  return {
    key: nextLegKey(),
    cityName: leg.cityName ?? "",
    cityGooglePlaceId: null,
    cityPlaceId: leg.cityPlaceId,
    timezone: leg.timezone ?? "",
    currency: "",
    lat: leg.lat,
    lng: leg.lng,
    days: leg.days,
  };
}

/** 여행에 등장하는 서로 다른 타임존. 안내 문구가 "타임존이 N개예요"를 판단하는 값이다. */
export function distinctTimezones(legs: LegDraft[]): string[] {
  return [...new Set(legs.map((leg) => leg.timezone).filter(Boolean))];
}
