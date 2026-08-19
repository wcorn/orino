import { describe, expect, it } from "vitest";

import type { BoardDay } from "@/features/travel/api/activities";

import { stayBadges } from "./stayBadge";

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

describe("숙소 배지", () => {
  it("숙소를 옮기는 날엔 둘 다 위에 선다 — 일정이 두 숙소 사이에 끼지 않게", () => {
    const items = stayBadges(
      day({ stayCheckout: DOTONBORI, stayTonight: KYOTO }),
    );

    expect(items).toEqual([
      { stayId: 76, name: "도톤보리 호텔", note: "오늘 체크아웃 11:00" },
      { stayId: 77, name: "교토 게스트하우스", note: "오늘 체크인 15:00" },
    ]);
  });

  it("체크아웃이 먼저다 — 아침에 급한 정보는 몇 시에 나가야 하나다", () => {
    const items = stayBadges(
      day({ stayCheckout: DOTONBORI, stayTonight: KYOTO }),
    );

    expect(items[0].stayId).toBe(76);
  });

  it("이어서 묵는 날은 한 줄이다 — 같은 숙소를 두 번 쓰면 소음이다", () => {
    const sameStay = day({
      stayCheckout: {
        stayId: 77,
        name: "교토 게스트하우스",
        checkOutTime: null,
      },
      stayTonight: { ...KYOTO, isCheckInDay: false },
    });

    expect(stayBadges(sameStay)).toHaveLength(1);
  });

  it("체크인하는 날은 시각을 몰라도 체크인이라고 말한다 — 체크아웃과 같은 규칙", () => {
    const items = stayBadges(
      day({ stayTonight: { ...KYOTO, checkInTime: null } }),
    );

    expect(items[0].note).toBe("오늘 체크인");
  });

  it("체크아웃 시각을 몰라도 체크아웃이라고 말한다 — 시각을 지어내지 않는다", () => {
    const items = stayBadges(
      day({ stayCheckout: { ...DOTONBORI, checkOutTime: null } }),
    );

    expect(items[0].note).toBe("오늘 체크아웃");
  });

  it("이어서 묵는 날에는 꼬리표를 붙이지 않는다 — 체크인은 지난 일이다", () => {
    const items = stayBadges(
      day({ stayTonight: { ...KYOTO, isCheckInDay: false } }),
    );

    expect(items[0].note).toBeNull();
  });

  it("체크아웃만 하는 마지막 날은 한 줄이다", () => {
    const items = stayBadges(day({ stayCheckout: DOTONBORI }));

    expect(items).toHaveLength(1);
    expect(items[0].note).toBe("오늘 체크아웃 11:00");
  });

  it("둘 다 없으면 비어 있다 — 화면은 그 자리에 `숙소 추가`를 세운다", () => {
    expect(stayBadges(day())).toEqual([]);
    expect(stayBadges(null)).toEqual([]);
  });
});
