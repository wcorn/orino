import { describe, expect, it } from "vitest";

import type { BoardDay } from "@/features/travel/api/activities";

import { badgeAboveList, badgeBelowList } from "./stayBadge";

function day(overrides: Partial<BoardDay> = {}): BoardDay {
  return {
    dayId: 1,
    dayIndex: 1,
    date: "2026-10-27",
    weekday: "화",
    activityCount: 0,
    baseCity: null,
    cityChanged: false,
    legIndex: 1,
    cityMemo: null,
    weather: null,
    stayTonight: null,
    stayCheckout: null,
    ...overrides,
  };
}

const DOTONBORI = {
  stayId: 76,
  name: "도톤보리 호텔",
  checkOutTime: "11:00",
};

const KYOTO = {
  stayId: 77,
  name: "교토 게스트하우스",
  sameCity: true,
  checkInTime: "15:00",
  isCheckInDay: true,
};

describe("리스트 위 배지", () => {
  it("체크아웃이 먼저다 — 아침에 급한 정보는 몇 시에 나가야 하나다", () => {
    const item = badgeAboveList(
      day({ stayCheckout: DOTONBORI, stayTonight: KYOTO }),
    );

    expect(item).toEqual({
      stayId: 76,
      name: "도톤보리 호텔",
      note: "오늘 체크아웃 11:00",
    });
  });

  it("체크아웃 시각을 모르면 시각 없이 말한다 — 지어내지 않는다", () => {
    const item = badgeAboveList(
      day({ stayCheckout: { ...DOTONBORI, checkOutTime: null } }),
    );

    expect(item?.note).toBe("오늘 체크아웃");
  });

  it("체크아웃이 없으면 오늘 밤 숙소를 보여준다", () => {
    const item = badgeAboveList(day({ stayTonight: KYOTO }));

    expect(item).toEqual({
      stayId: 77,
      name: "교토 게스트하우스",
      note: "오늘 체크인 15:00",
    });
  });

  it("이어서 묵는 날에는 체크인 시각을 붙이지 않는다 — 지난 정보다", () => {
    const item = badgeAboveList(
      day({ stayTonight: { ...KYOTO, isCheckInDay: false } }),
    );

    expect(item?.note).toBeNull();
  });

  it("둘 다 없으면 null — 화면은 그 자리에 `숙소 추가`를 세운다", () => {
    expect(badgeAboveList(day())).toBeNull();
    expect(badgeAboveList(null)).toBeNull();
  });
});

describe("리스트 아래 배지", () => {
  it("숙소를 옮기는 날에만 나온다 — 위는 체크아웃, 아래는 오늘 밤", () => {
    const target = day({ stayCheckout: DOTONBORI, stayTonight: KYOTO });

    expect(badgeAboveList(target)?.stayId).toBe(76);
    expect(badgeBelowList(target)?.stayId).toBe(77);
  });

  it("위 배지와 같은 숙소면 그리지 않는다 — 같은 것을 두 번 쓰면 소음이다", () => {
    expect(badgeBelowList(day({ stayTonight: KYOTO }))).toBeNull();
  });

  it("오늘 밤 숙소가 없으면 없다 — 체크아웃만 하는 마지막 날", () => {
    expect(badgeBelowList(day({ stayCheckout: DOTONBORI }))).toBeNull();
  });
});
