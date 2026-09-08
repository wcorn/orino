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

/**
 * 꼬리표는 세 값을 순서대로 본다 — 광역 행정구역 → 장소가 아는 도시 → 담을 때 누른 칩.
 *
 * <p>순서가 이렇게 된 사연이 이 파일의 내용이다. 처음엔 칩이 먼저였는데(표기를 통일하려고),
 * 그러면 교토에서 담은 나라 장소가 「교토시」가 됐다(#1373). 장소가 아는 이름으로 바꿨더니
 * 같은 나라 장소들이 `나라시`·`Nara`·`야마토코리야마시`로 갈렸다(#1375). 현으로 묶어야
 * 하나가 된다.
 */
describe("도시 표시명", () => {
  it("광역 행정구역이 있으면 그것을 쓴다 — 같은 도시가 여러 글자로 보이지 않게", () => {
    // 구글이 주는 locality는 장소마다 갈린다. 현은 그 셋을 하나로 묶는다.
    const labels = [
      { cityName: "나라시", adminArea: "나라현", cityPlaceRef: "ChIJ_kyoto" },
      { cityName: "Nara", adminArea: "나라현", cityPlaceRef: "ChIJ_kyoto" },
      {
        cityName: "야마토코리야마시",
        adminArea: "나라현",
        cityPlaceRef: "ChIJ_kyoto",
      },
    ].map((place) => cityLabelOf(place, TRIP_CITIES));

    expect(labels).toEqual(["나라현", "나라현", "나라현"]);
  });

  it("현을 아직 못 받았으면 장소가 아는 도시로 떨어진다", () => {
    // 이 값이 생기기 전에 담긴 장소다. 「도시 이름 새로고침」 전까지는 옛 규칙으로 그린다.
    const label = cityLabelOf(
      { cityName: "나라시", adminArea: null, cityPlaceRef: "ChIJ_kyoto" },
      TRIP_CITIES,
    );

    expect(label).toBe("나라시");
  });

  it("담을 때 누른 칩은 맨 마지막이다 — 그건 장소의 도시가 아니다", () => {
    // 교토에 묵으며 나라 당일치기를 짜면 나라 장소에 교토 칩이 찍힌다(#1373).
    // 장소가 자기 도시를 아무것도 모를 때만 이 값으로 떨어진다.
    expect(
      cityLabelOf(
        { cityName: null, adminArea: null, cityPlaceRef: "ChIJ_kyoto" },
        TRIP_CITIES,
      ),
    ).toBe("교토시");
  });

  it("여행에 없는 도시면 장소가 준 이름을 쓴다 — 그게 가진 전부다", () => {
    const label = cityLabelOf(
      { cityName: "Nagoya", adminArea: null, cityPlaceRef: "ChIJ_nagoya" },
      TRIP_CITIES,
    );

    expect(label).toBe("Nagoya");
  });

  it("아무것도 없으면 null — 화면이 그 자리를 비운다", () => {
    expect(
      cityLabelOf(
        { cityName: null, adminArea: null, cityPlaceRef: null },
        TRIP_CITIES,
      ),
    ).toBeNull();
  });

  it("장소가 없으면 null", () => {
    expect(cityLabelOf(null, TRIP_CITIES)).toBeNull();
  });
});
