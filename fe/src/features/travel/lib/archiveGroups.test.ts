import { describe, expect, it } from "vitest";

import type {
  Activity,
  BaseCity,
  BoardDay,
} from "@/features/travel/api/activities";

import { daysForPlace, groupArchiveByCity } from "./archiveGroups";

function city(
  placeId: number,
  name: string,
  cityPlaceRef: string | null,
): BaseCity {
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

const OSAKA = city(21, "오사카", "ChIJ_osaka");
const KYOTO = city(22, "교토", "ChIJ_kyoto");

function day(dayIndex: number, date: string, baseCity: BaseCity): BoardDay {
  return {
    dayId: 500 + dayIndex,
    dayIndex,
    date,
    weekday: "토",
    activityCount: 0,
    baseCity,
    cityChanged: false,
    legIndex: 1,
    cityMemo: null,
    weather: null,
    stayTonight: null,
    stayCheckout: null,
  };
}

const DAYS = [
  day(1, "2026-10-24", OSAKA),
  day(2, "2026-10-25", OSAKA),
  day(3, "2026-10-26", KYOTO),
];

function activity(
  id: number,
  title: string,
  place: { cityName: string | null; cityPlaceRef: string | null } | null,
): Activity {
  return {
    id,
    tripId: 1,
    title,
    activityDate: null,
    startTime: null,
    place: place && {
      id: 100 + id,
      name: title,
      address: null,
      lat: null,
      lng: null,
      cityName: place.cityName,
      cityPlaceRef: place.cityPlaceRef,
    },
    memo: null,
    url: null,
    notifyEnabled: false,
    notifyMinutes: null,
    departureNotifyEnabled: false,
    outOfBaseCity: false,
    canDepartureNotify: true,
    sortOrder: id,
    log: null,
    hasLog: false,
  };
}

describe("groupArchiveByCity", () => {
  it("도시별로 묶고 구간 순서대로 배치한다", () => {
    const groups = groupArchiveByCity(
      [
        activity(1, "니시키 시장", {
          cityName: "교토",
          cityPlaceRef: "ChIJ_kyoto",
        }),
        activity(2, "구로몬 시장", {
          cityName: "오사카",
          cityPlaceRef: "ChIJ_osaka",
        }),
      ],
      DAYS,
    );

    // 목록에는 교토가 먼저 있었지만, 그룹 순서는 방문 순서(오사카 → 교토)다.
    expect(groups.map((g) => g.label)).toEqual(["오사카", "교토"]);
    expect(groups[0].activities.map((a) => a.title)).toEqual(["구로몬 시장"]);
  });

  it("여행의 어느 도시도 아니면 `기타`, 도시를 모르면 `도시 없음` — 둘 다 맨 뒤", () => {
    const groups = groupArchiveByCity(
      [
        activity(1, "이름만 아는 곳", null),
        activity(2, "나라 공원", {
          cityName: "나라",
          cityPlaceRef: "ChIJ_nara",
        }),
        activity(3, "구로몬 시장", {
          cityName: "오사카",
          cityPlaceRef: "ChIJ_osaka",
        }),
      ],
      DAYS,
    );

    expect(groups.map((g) => g.label)).toEqual(["오사카", "기타", "도시 없음"]);
  });

  it("도시 이름이 같아도 식별자가 없으면 묶지 않는다 — 추측하지 않는다", () => {
    const groups = groupArchiveByCity(
      [
        activity(1, "구로몬 시장", {
          cityName: "오사카",
          cityPlaceRef: "ChIJ_osaka",
        }),
        activity(2, "이름만 오사카", {
          cityName: "오사카",
          cityPlaceRef: null,
        }),
      ],
      DAYS,
    );

    expect(groups.map((g) => g.label)).toEqual(["오사카", "도시 없음"]);
  });

  it("빈 보관함은 그룹도 없다 — 빈 헤더만 늘어선 목록은 읽을 것이 없다", () => {
    expect(groupArchiveByCity([], DAYS)).toEqual([]);
  });
});

describe("daysForPlace", () => {
  it("그 장소의 도시가 기준 도시인 날짜를 위로 올린다", () => {
    const sorted = daysForPlace(DAYS, "ChIJ_kyoto");

    expect(sorted.map((d) => d.dayIndex)).toEqual([3, 1, 2]);
  });

  it("올린 것 말고는 원래 순서를 지킨다", () => {
    const sorted = daysForPlace(DAYS, "ChIJ_osaka");

    expect(sorted.map((d) => d.dayIndex)).toEqual([1, 2, 3]);
  });

  it("도시를 모르면 아무것도 올리지 않는다", () => {
    expect(daysForPlace(DAYS, null).map((d) => d.dayIndex)).toEqual([1, 2, 3]);
  });

  it("여행에 없는 도시면 그대로 둔다", () => {
    expect(daysForPlace(DAYS, "ChIJ_nara").map((d) => d.dayIndex)).toEqual([
      1, 2, 3,
    ]);
  });
});
