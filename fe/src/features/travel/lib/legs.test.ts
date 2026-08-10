import { describe, expect, it } from "vitest";

import type { BoardDay } from "@/features/travel/api/activities";

import { deriveLegs } from "./legs";

/**
 * 구간 파생 규칙. **BE `LegDeriverTest`와 같은 케이스**를 쓴다 — 규칙이 두 벌 존재하는 것은
 * 오프라인 때문에 피할 수 없으니, 어긋나지 않게 같은 질문을 양쪽에 던져 둔다.
 */

const TOKYO = city(21, "도쿄");
const NIKKO = city(22, "닛코");
const OSAKA = city(23, "오사카");

function city(placeId: number, name: string) {
  return {
    placeId,
    name,
    timezone: "Asia/Tokyo",
    currency: "JPY",
    countryCode: "JP",
    lat: null,
    lng: null,
  };
}

/** 10.24부터 하루씩, 주어진 순서대로 기준 도시를 붙인 날짜들. */
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

describe("deriveLegs", () => {
  it("전 기간 같은 도시면 구간은 하나다", () => {
    const legs = deriveLegs(days(TOKYO, TOKYO, TOKYO, TOKYO));

    expect(legs).toHaveLength(1);
    expect(legs[0]).toMatchObject({
      legIndex: 1,
      cityPlaceId: 21,
      cityName: "도쿄",
      days: 4,
      startDate: "2026-10-24",
      endDate: "2026-10-27",
    });
  });

  it("[도쿄, 닛코, 도쿄] → 구간 3개. 같은 도시라도 사이가 끊기면 다른 구간이다", () => {
    const legs = deriveLegs(days(TOKYO, NIKKO, TOKYO));

    expect(legs.map((leg) => leg.cityName)).toEqual(["도쿄", "닛코", "도쿄"]);
    expect(legs.map((leg) => leg.legIndex)).toEqual([1, 2, 3]);
    expect(legs.map((leg) => leg.days)).toEqual([1, 1, 1]);
  });

  it("연속된 같은 도시는 한 구간으로 묶이고 일수가 합쳐진다", () => {
    const legs = deriveLegs(days(OSAKA, OSAKA, OSAKA, NIKKO, TOKYO, TOKYO));

    expect(legs.map((leg) => leg.days)).toEqual([3, 1, 2]);
    expect(legs[0].endDate).toBe("2026-10-26");
    expect(legs[1].startDate).toBe("2026-10-27");
    expect(legs[legs.length - 1].endDate).toBe("2026-10-29");
  });

  it("날짜가 하나면 구간도 하나, 하루짜리다", () => {
    expect(deriveLegs(days(TOKYO))).toEqual([
      {
        legIndex: 1,
        cityPlaceId: 21,
        cityName: "도쿄",
        days: 1,
        startDate: "2026-10-24",
        endDate: "2026-10-24",
      },
    ]);
  });

  it("날짜가 없으면 구간도 없다", () => {
    expect(deriveLegs([])).toEqual([]);
  });

  it("기준 도시를 모르는 날짜도 자리를 지킨다 — 빠지면 날짜가 건너뛴 것처럼 보인다", () => {
    const legs = deriveLegs(days(TOKYO, null, TOKYO));

    expect(legs).toHaveLength(3);
    expect(legs[1].cityPlaceId).toBeNull();
    expect(legs[1].startDate).toBe("2026-10-25");
  });
});
