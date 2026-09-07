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
  /**
   * 이 파일의 핵심(#1373). `cityPlaceRef`는 「이 장소가 어느 도시에 있는가」가 아니라
   * <b>「어느 도시 칩으로 담았는가」</b>다 — 둘이 갈리는 순간 칩으로 이름을 지으면 거짓말이 된다.
   */
  it("담을 때 누른 칩이 아니라 장소가 아는 도시를 말한다", () => {
    // 교토에 묵으며 나라 당일치기를 짜면 나라 장소에 교토 칩이 찍힌다.
    const label = cityLabelOf(
      { cityName: "나라시", cityPlaceRef: "ChIJ_kyoto" },
      TRIP_CITIES,
    );

    expect(label).toBe("나라시");
  });

  it("장소가 준 이름을 그대로 쓴다 — 여행 도시와 표기가 달라도", () => {
    // 잃는 것을 여기 적어 둔다. 탭은 `오사카시`인데 장소 상세는 `Osaka`로 온다.
    // 표기가 갈리지만, 다른 도시 이름을 씌우는 것보다는 낫다.
    const label = cityLabelOf(
      { cityName: "Osaka", cityPlaceRef: "ChIJ_osaka" },
      TRIP_CITIES,
    );

    expect(label).toBe("Osaka");
  });

  it("구 단위로 와도 그대로 쓴다 — 도시로 올려 주지 않는다", () => {
    // 신주쿠 호텔의 주소 구성요소는 `Shinjuku City`로 온다. 예전에는 칩으로 도시 이름을
    // 씌웠지만, 그 방식이 나라 장소를 「교토시」로 만든 원인이었다.
    const label = cityLabelOf(
      { cityName: "Shinjuku City", cityPlaceRef: "ChIJ_kyoto" },
      TRIP_CITIES,
    );

    expect(label).toBe("Shinjuku City");
  });

  it("여행에 없는 도시면 장소가 준 이름을 쓴다 — 그게 가진 전부다", () => {
    const label = cityLabelOf(
      { cityName: "Nagoya", cityPlaceRef: "ChIJ_nagoya" },
      TRIP_CITIES,
    );

    expect(label).toBe("Nagoya");
  });

  it("식별자가 없어도 장소가 아는 이름이 있으면 그것을 쓴다", () => {
    const label = cityLabelOf(
      { cityName: "오사카시", cityPlaceRef: null },
      TRIP_CITIES,
    );

    expect(label).toBe("오사카시");
  });

  it("장소가 자기 도시를 모르면 담을 때의 칩으로 떨어진다 — 그게 가진 전부다", () => {
    const label = cityLabelOf(
      { cityName: null, cityPlaceRef: "ChIJ_kyoto" },
      TRIP_CITIES,
    );

    expect(label).toBe("교토시");
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
