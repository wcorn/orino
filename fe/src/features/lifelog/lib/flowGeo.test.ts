import { describe, expect, it } from "vitest";

import type { MomentCard } from "../api/types";
import { toGeoMoments } from "./flowGeo";

function moment(
  id: number,
  lat: number | null,
  lng: number | null,
): MomentCard {
  return {
    id,
    occurredAt: "2026-07-20T00:00:00Z",
    body: null,
    mood: null,
    lat,
    lng,
    placeName: null,
    tags: [],
    photos: [],
    flows: [],
    createdAt: "2026-07-20T00:00:00Z",
  };
}

describe("toGeoMoments", () => {
  it("좌표 있는 기록만 뽑고 시간순 번호를 매긴다", () => {
    const result = toGeoMoments([
      moment(1, 33.45, 126.94),
      moment(2, null, null),
      moment(3, 35.1, 129.0),
    ]);
    expect(result.map((g) => g.moment.id)).toEqual([1, 3]);
    expect(result.map((g) => g.order)).toEqual([1, 2]);
  });

  it("한쪽 좌표만 있으면 제외한다", () => {
    expect(toGeoMoments([moment(1, 33.45, null)])).toEqual([]);
  });
});
