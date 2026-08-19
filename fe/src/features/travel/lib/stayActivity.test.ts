import { describe, expect, it } from "vitest";

import type { Stay } from "@/features/travel/api/stays";

import { stayActivityTime, stayActivityTitle } from "./stayActivity";

const STAY: Stay = {
  stayId: 77,
  name: "교토 게스트하우스",
  placeId: 5,
  checkInDate: "2026-10-25",
  checkOutDate: "2026-10-28",
  checkInTime: "15:00",
  checkOutTime: "11:00",
  bookingUrl: null,
  memo: null,
  nights: 3,
};

describe("숙소를 일정으로 담을 때", () => {
  it("체크인하는 날이면 체크인이라고 적는다", () => {
    expect(stayActivityTitle(STAY, "2026-10-25")).toBe(
      "교토 게스트하우스 체크인",
    );
    expect(stayActivityTime(STAY, "2026-10-25")).toBe("15:00");
  });

  it("나가는 날이면 체크아웃이라고 적는다", () => {
    expect(stayActivityTitle(STAY, "2026-10-28")).toBe(
      "교토 게스트하우스 체크아웃",
    );
    expect(stayActivityTime(STAY, "2026-10-28")).toBe("11:00");
  });

  it("묵는 날에는 숙소 이름만 — 지난 체크인을 오늘 할 일처럼 적지 않는다", () => {
    expect(stayActivityTitle(STAY, "2026-10-26")).toBe("교토 게스트하우스");
    expect(stayActivityTime(STAY, "2026-10-26")).toBeNull();
  });

  it("시각을 모르는 숙소는 시각 없이 담는다 — 지어내면 순서가 틀어진다", () => {
    const noTime = { ...STAY, checkInTime: null, checkOutTime: null };

    expect(stayActivityTime(noTime, "2026-10-25")).toBeNull();
    expect(stayActivityTime(noTime, "2026-10-28")).toBeNull();
  });
});
