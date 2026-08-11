import { describe, expect, it } from "vitest";

import type { CityLeg } from "@/features/travel/api/travel";

import { cityMarkers, legPath } from "./cityMarkers";

function leg(
  legIndex: number,
  cityPlaceId: number,
  cityName: string,
  overrides: Partial<CityLeg> = {},
): CityLeg {
  return {
    legIndex,
    cityPlaceId,
    cityName,
    days: 2,
    startDate: "2026-10-24",
    endDate: "2026-10-25",
    timezone: "Asia/Tokyo",
    lat: 35.68 + legIndex,
    lng: 139.76 + legIndex,
    ...overrides,
  };
}

/** 도쿄 → 닛코 → 도쿄. 구간은 셋, 도시는 둘이다. */
const NIKKO_TRIP = [leg(1, 21, "도쿄"), leg(2, 22, "닛코"), leg(3, 21, "도쿄")];

describe("도시 마커", () => {
  it("같은 도시를 다시 방문해도 마커는 하나다", () => {
    expect(cityMarkers(NIKKO_TRIP)).toHaveLength(2);
  });

  it("번호는 첫 방문 구간 것을 쓴다 — 도쿄는 3이 아니라 1이다", () => {
    const markers = cityMarkers(NIKKO_TRIP);

    expect(markers[0]).toMatchObject({ cityName: "도쿄", legIndex: 1 });
    expect(markers[1]).toMatchObject({ cityName: "닛코", legIndex: 2 });
  });

  it("좌표를 모르는 도시는 찍지 않는다 — 지도에 놓을 자리가 없다", () => {
    const markers = cityMarkers([
      leg(1, 21, "도쿄"),
      leg(2, 23, "하코네", { lat: null, lng: null }),
    ]);

    expect(markers.map((m) => m.cityName)).toEqual(["도쿄"]);
  });

  it("이름을 모르면 `도시 없음`으로 둔다 — 빈 칸을 남기지 않는다", () => {
    expect(
      cityMarkers([leg(1, 21, "도쿄", { cityName: null })])[0],
    ).toMatchObject({ cityName: "도시 없음" });
  });

  it("구간이 없으면 마커도 없다", () => {
    expect(cityMarkers([])).toEqual([]);
  });
});

describe("연결선", () => {
  it("구간 순서 그대로다 — 마커처럼 접지 않는다", () => {
    // 접어 버리면 닛코에 갔다 온 사실이 선에서 사라진다.
    expect(legPath(NIKKO_TRIP)).toHaveLength(3);
  });

  it("좌표를 모르는 구간은 건너뛴다", () => {
    const path = legPath([
      leg(1, 21, "도쿄"),
      leg(2, 23, "하코네", { lat: null, lng: null }),
      leg(3, 22, "닛코"),
    ]);

    expect(path).toHaveLength(2);
  });
});
