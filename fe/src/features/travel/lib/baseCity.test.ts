import { describe, expect, it } from "vitest";

import type { BoardDay } from "@/features/travel/api/activities";

import { cityCount, cityOn, firstDayZone, zoneByDate } from "./baseCity";
import { daysUntil, deriveStatus, todayOfTrip } from "./tripStatus";

/**
 * 날짜 → 기준 도시 조회. 파생 계산에 그대로 물려 쓰는 것이 목적이라, 조회 자체보다
 * **물렸을 때 결과가 맞는지**를 함께 본다.
 */

function city(placeId: number, name: string, timezone: string) {
  return {
    placeId,
    name,
    timezone,
    currency: "JPY",
    countryCode: "JP",
    cityPlaceRef: null,
    lat: null,
    lng: null,
  };
}

const TOKYO = city(21, "도쿄", "Asia/Tokyo");
const HONOLULU = city(22, "호놀룰루", "Pacific/Honolulu");

function days(...cities: (ReturnType<typeof city> | null)[]): BoardDay[] {
  return cities.map((baseCity, i) => ({
    dayId: 500 + i,
    dayIndex: i + 1,
    date: `2026-10-${24 + i}`,
    weekday: "토",
    activityCount: 0,
    baseCity,
    cityChanged: false,
    legIndex: 1,
    cityMemo: null,
    weather: null,
    stayTonight: null,
    stayCheckout: null,
  }));
}

describe("cityOn", () => {
  it("그 날짜의 기준 도시를 준다", () => {
    expect(cityOn(days(TOKYO, HONOLULU), "2026-10-25")?.name).toBe("호놀룰루");
  });

  it("기간 밖 날짜는 null이다", () => {
    expect(cityOn(days(TOKYO), "2026-11-01")).toBeNull();
  });
});

describe("cityCount", () => {
  it("같은 도시를 다시 방문해도 하나로 센다", () => {
    expect(cityCount(days(TOKYO, HONOLULU, TOKYO))).toBe(2);
    expect(cityCount(days(TOKYO, TOKYO))).toBe(1);
    expect(cityCount([])).toBe(0);
  });
});

describe("zoneByDate", () => {
  it("날짜마다 그 도시의 타임존을 준다", () => {
    const zones = zoneByDate(days(TOKYO, HONOLULU));

    expect(zones("2026-10-24")).toBe("Asia/Tokyo");
    expect(zones("2026-10-25")).toBe("Pacific/Honolulu");
  });

  it("모르는 날짜는 첫날 타임존으로 답한다 — 비면 판정 자체를 못 한다", () => {
    const zones = zoneByDate(days(TOKYO, HONOLULU));

    expect(zones("2026-12-31")).toBe("Asia/Tokyo");
  });

  it("파생 계산에 물리면 날짜별로 갈린다", () => {
    // 10/25만 호놀룰루(UTC-10). 10/25 05:00 UTC —
    // 도쿄로 10/24는 이미 지났고(10/25 14:00), 호놀룰루로 10/25는 아직이다(10/24 19:00).
    const zones = zoneByDate(days(TOKYO, HONOLULU, TOKYO, TOKYO));
    const now = new Date("2026-10-25T05:00:00Z");

    expect(todayOfTrip("2026-10-24", "2026-10-27", zones, now)).toBe(
      "2026-10-25",
    );
    expect(deriveStatus("2026-10-24", "2026-10-27", zones, now)).toBe(
      "ONGOING",
    );
  });
});

describe("firstDayZone", () => {
  it("D-day는 첫날 도시로 센다 — 뒷날 도시가 달라도 흔들리지 않는다", () => {
    // 호놀룰루(UTC-10)에서 출발하는 여행. 10/24 05:00 UTC면 현지는 아직 10/23이다.
    const zones = firstDayZone("Pacific/Honolulu");

    expect(
      daysUntil("2026-10-24", zones, new Date("2026-10-24T05:00:00Z")),
    ).toBe(1);
  });
});
