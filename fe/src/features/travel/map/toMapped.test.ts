import { describe, expect, it } from "vitest";

import type { Activity } from "@/features/travel/api/activities";

import { toMapped } from "./toMapped";

function activity(id: number, place: Activity["place"] = null): Activity {
  return {
    id,
    tripId: 1,
    title: `일정 ${id}`,
    activityDate: "2026-10-24",
    startTime: null,
    place,
    memo: null,
    url: null,
    notifyEnabled: false,
    notifyMinutes: null,
    departureNotifyEnabled: false,
    sortOrder: id,
    log: null,
    hasLog: false,
    outOfBaseCity: false,
    canDepartureNotify: true,
  };
}

const SENSOJI = {
  id: 10,
  name: "센소지",
  address: "다이토구",
  lat: 35.7147651,
  lng: 139.7966553,
  cityName: "도쿄",
  cityPlaceRef: "ChIJ_tokyo",
};
const SKYTREE = {
  id: 11,
  name: "스카이트리",
  address: "스미다구",
  lat: 35.7100627,
  lng: 139.8107004,
  cityName: "도쿄",
  cityPlaceRef: "ChIJ_tokyo",
};

describe("지도에 올릴 일정 고르기", () => {
  it("번호를 지도 표시 순서로 다시 매긴다 — 리스트 순번을 쓰면 1·3번만 뜬다", () => {
    const mapped = toMapped([
      activity(1, SENSOJI),
      activity(2), // 장소 없음 — 지도에 없다
      activity(3, SKYTREE),
    ]);

    expect(mapped.map((m) => m.order)).toEqual([1, 2]);
    expect(mapped.map((m) => m.activity.id)).toEqual([1, 3]);
  });

  it("좌표 없는 장소(직접 입력)는 뺀다 — 찍을 곳이 없다", () => {
    const manual = {
      id: 12,
      name: "골목 카페",
      address: null,
      lat: null,
      lng: null,
      cityName: null,
      cityPlaceRef: null,
    };

    expect(toMapped([activity(1, manual)])).toHaveLength(0);
  });

  it("리스트 순서를 그대로 지킨다 — 동선은 순서가 전부다", () => {
    const mapped = toMapped([activity(5, SKYTREE), activity(6, SENSOJI)]);

    expect(mapped.map((m) => m.activity.id)).toEqual([5, 6]);
  });

  it("장소가 하나도 없으면 빈 배열", () => {
    expect(toMapped([activity(1), activity(2)])).toHaveLength(0);
  });
});
