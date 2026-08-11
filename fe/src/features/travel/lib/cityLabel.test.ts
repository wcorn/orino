import { describe, expect, it } from "vitest";

import type { BaseCity } from "@/features/travel/api/activities";

import { cityLabelOf } from "./cityLabel";

function city(placeId: number, name: string, cityPlaceRef: string): BaseCity {
  return {
    placeId,
    name,
    timezone: "Asia/Tokyo",
    currency: "JPY",
    countryCode: "JP",
    cityPlaceRef,
    lat: null,
    lng: null,
  };
}

const TRIP_CITIES = [
  city(1, "오사카시", "ChIJ_osaka"),
  city(2, "교토시", "ChIJ_kyoto"),
];

describe("도시 표시명", () => {
  it("여행 도시와 맞으면 그 도시 이름을 쓴다 — 날짜 탭과 같은 글자여야 한다", () => {
    // 장소는 `Osaka`라고 들고 왔지만 탭은 `오사카시`라고 부른다.
    const label = cityLabelOf(
      { cityName: "Osaka", cityPlaceRef: "ChIJ_osaka" },
      TRIP_CITIES,
    );

    expect(label).toBe("오사카시");
  });

  it("구 단위로 온 이름도 도시 이름으로 바로잡는다", () => {
    // 신주쿠 호텔의 주소 구성요소는 `Shinjuku City`로 온다.
    const label = cityLabelOf(
      { cityName: "Shinjuku City", cityPlaceRef: "ChIJ_kyoto" },
      TRIP_CITIES,
    );

    expect(label).toBe("교토시");
  });

  it("여행에 없는 도시면 장소가 준 이름을 쓴다 — 그게 가진 전부다", () => {
    const label = cityLabelOf(
      { cityName: "Nagoya", cityPlaceRef: "ChIJ_nagoya" },
      TRIP_CITIES,
    );

    expect(label).toBe("Nagoya");
  });

  it("식별자를 모르면 이름으로 맞추지 않는다 — 표기 흔들림에 깨진다(D-23)", () => {
    const label = cityLabelOf(
      { cityName: "오사카시", cityPlaceRef: null },
      TRIP_CITIES,
    );

    // 이름이 같아도 식별자로 맞추지 않았으므로 장소가 준 값 그대로다(결과는 같지만
    // 경로가 다르다 — 여기서 이름 비교를 하면 `Osaka`는 못 맞춘다).
    expect(label).toBe("오사카시");
  });

  it("이름조차 없으면 null — 화면이 그 자리를 비운다", () => {
    expect(
      cityLabelOf({ cityName: null, cityPlaceRef: null }, TRIP_CITIES),
    ).toBeNull();
  });

  it("장소가 없으면 null", () => {
    expect(cityLabelOf(null, TRIP_CITIES)).toBeNull();
  });
});
