import { describe, expect, it } from "vitest";

import { todayOpeningHours } from "./openingHours";

const RAW = JSON.stringify({
  weekdayDescriptions: [
    "월요일: 09:00~18:00",
    "화요일: 09:00~18:00",
    "수요일: 휴무",
    "목요일: 09:00~18:00",
    "금요일: 09:00~21:00",
    "토요일: 10:00~21:00",
    "일요일: 10:00~17:00",
  ],
});

describe("영업시간 — 오늘 줄만", () => {
  it("Google은 월요일부터 주고 Date는 일요일이 0이다 — 이 어긋남이 요일을 밀리게 한다", () => {
    // 2026-08-10은 월요일.
    expect(todayOpeningHours(RAW, new Date(2026, 7, 10))).toBe(
      "월요일: 09:00~18:00",
    );
    // 2026-08-16은 일요일 — 배열의 마지막이다.
    expect(todayOpeningHours(RAW, new Date(2026, 7, 16))).toBe(
      "일요일: 10:00~17:00",
    );
  });

  it("휴무일도 그대로 보여준다 — 가서 알게 되면 늦다", () => {
    // 2026-08-12는 수요일.
    expect(todayOpeningHours(RAW, new Date(2026, 7, 12))).toBe("수요일: 휴무");
  });

  it("영업시간이 없으면 비운다 — 상시 개방인 곳은 구글이 안 준다", () => {
    expect(todayOpeningHours(null)).toBeNull();
  });

  it("형태가 달라지면 조용히 비운다 — 원본을 그대로 캐시하므로 언제든 바뀔 수 있다", () => {
    expect(todayOpeningHours("not json")).toBeNull();
    expect(todayOpeningHours(JSON.stringify({ other: 1 }))).toBeNull();
    // 일곱 줄이 아니면 요일 매칭을 믿을 수 없다.
    expect(
      todayOpeningHours(JSON.stringify({ weekdayDescriptions: ["월: 종일"] })),
    ).toBeNull();
  });
});
