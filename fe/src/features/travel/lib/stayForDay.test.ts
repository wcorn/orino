import { describe, expect, it } from "vitest";

import {
  coversNight,
  isCheckOutOn,
  overlaps,
  stayCheckout,
  stayTonight,
} from "./stayForDay";

/**
 * 숙소 날짜 판정. **BE `TripStayRepositoryTest`와 같은 경계**를 쓴다 —
 * 체크아웃일 밤은 이미 다른 곳에서 잔다.
 */

const OSAKA_HOTEL = {
  name: "오사카 호텔",
  checkInDate: "2026-10-24",
  checkOutDate: "2026-10-27",
};
const KYOTO_RYOKAN = {
  name: "교토 료칸",
  checkInDate: "2026-10-27",
  checkOutDate: "2026-10-29",
};

describe("coversNight · isCheckOutOn", () => {
  it("체크인 이상 체크아웃 미만인 날짜에만 잔다", () => {
    expect(coversNight(OSAKA_HOTEL, "2026-10-23")).toBe(false);
    expect(coversNight(OSAKA_HOTEL, "2026-10-24")).toBe(true);
    expect(coversNight(OSAKA_HOTEL, "2026-10-26")).toBe(true);
    // 체크아웃일 밤은 다른 곳에서 잔다.
    expect(coversNight(OSAKA_HOTEL, "2026-10-27")).toBe(false);
  });

  it("체크아웃일은 따로 판정한다", () => {
    expect(isCheckOutOn(OSAKA_HOTEL, "2026-10-27")).toBe(true);
    expect(isCheckOutOn(OSAKA_HOTEL, "2026-10-26")).toBe(false);
  });
});

describe("overlaps", () => {
  it("이동일(앞 체크아웃 = 뒤 체크인)은 겹침이 아니다", () => {
    expect(overlaps(OSAKA_HOTEL, KYOTO_RYOKAN)).toBe(false);
  });

  it("하루라도 밤이 겹치면 겹침이다", () => {
    expect(
      overlaps(OSAKA_HOTEL, {
        checkInDate: "2026-10-26",
        checkOutDate: "2026-10-29",
      }),
    ).toBe(true);
    expect(
      overlaps(OSAKA_HOTEL, {
        checkInDate: "2026-10-20",
        checkOutDate: "2026-10-25",
      }),
    ).toBe(true);
  });
});

describe("stayTonight · stayCheckout", () => {
  const stays = [OSAKA_HOTEL, KYOTO_RYOKAN];

  it("이동일에는 자는 곳과 체크아웃하는 곳이 다르다", () => {
    // 10/27 — 오사카에서 체크아웃하고 교토에서 잔다.
    expect(stayCheckout(stays, "2026-10-27")?.name).toBe("오사카 호텔");
    expect(stayTonight(stays, "2026-10-27")?.name).toBe("교토 료칸");
  });

  it("해당 없는 날짜는 null이다", () => {
    expect(stayTonight(stays, "2026-10-29")).toBeNull();
    expect(stayCheckout(stays, "2026-10-28")).toBeNull();
    expect(stayTonight([], "2026-10-24")).toBeNull();
  });
});
