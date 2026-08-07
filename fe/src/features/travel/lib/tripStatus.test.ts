import { describe, expect, it } from "vitest";

import {
  dayChips,
  daysUntil,
  deriveStatus,
  formatPeriod,
  formatShortDate,
  todayIn,
  totalDays,
} from "./tripStatus";

/** 2026-10-23 16:00 UTC — 도쿄는 이미 10/24 01:00, 호놀룰루는 아직 10/23 06:00. */
const CROSSING = new Date("2026-10-23T16:00:00Z");

describe("todayIn", () => {
  it("여행 타임존의 오늘을 준다", () => {
    expect(todayIn("Asia/Tokyo", CROSSING)).toBe("2026-10-24");
    expect(todayIn("Pacific/Honolulu", CROSSING)).toBe("2026-10-23");
    expect(todayIn("UTC", CROSSING)).toBe("2026-10-23");
  });

  it("알 수 없는 타임존이면 기기 기준으로 떨어진다(화면을 죽이지 않는다)", () => {
    expect(todayIn("Not/AZone", CROSSING)).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});

describe("deriveStatus", () => {
  const period = ["2026-10-24", "2026-10-27"] as const;

  it("시작 전이면 예정, 기간 안이면 진행 중, 끝나면 완료", () => {
    expect(
      deriveStatus(...period, "Asia/Tokyo", new Date("2026-10-20T03:00:00Z")),
    ).toBe("UPCOMING");
    expect(
      deriveStatus(...period, "Asia/Tokyo", new Date("2026-10-25T03:00:00Z")),
    ).toBe("ONGOING");
    expect(
      deriveStatus(...period, "Asia/Tokyo", new Date("2026-10-28T03:00:00Z")),
    ).toBe("COMPLETED");
  });

  it("시작일·종료일 당일도 진행 중이다(경계 포함)", () => {
    expect(
      deriveStatus(...period, "Asia/Tokyo", new Date("2026-10-24T03:00:00Z")),
    ).toBe("ONGOING");
    expect(
      deriveStatus(...period, "Asia/Tokyo", new Date("2026-10-27T03:00:00Z")),
    ).toBe("ONGOING");
  });

  it("기기 시간대가 아니라 여행 타임존으로 판정한다", () => {
    // 같은 순간·같은 기간인데 목적지에 따라 갈린다.
    expect(deriveStatus(...period, "Asia/Tokyo", CROSSING)).toBe("ONGOING");
    expect(deriveStatus(...period, "Pacific/Honolulu", CROSSING)).toBe(
      "UPCOMING",
    );
  });
});

describe("daysUntil", () => {
  it("남은 일수를 준다 — 당일 0, 시작 후 음수", () => {
    expect(daysUntil("2026-10-24", "Asia/Tokyo", CROSSING)).toBe(0);
    expect(
      daysUntil("2026-10-24", "Asia/Tokyo", new Date("2026-10-20T03:00:00Z")),
    ).toBe(4);
    expect(
      daysUntil("2026-10-24", "Asia/Tokyo", new Date("2026-10-26T03:00:00Z")),
    ).toBe(-2);
  });

  it("호놀룰루는 같은 순간에도 하루가 더 남는다", () => {
    expect(daysUntil("2026-10-24", "Pacific/Honolulu", CROSSING)).toBe(1);
  });

  it("서머타임 경계를 넘어도 일수가 어긋나지 않는다", () => {
    // 미국 서머타임 종료(2026-11-01) 전후. 로컬 자정으로 계산하면 하루가 25시간이라 밀린다.
    expect(
      daysUntil(
        "2026-11-05",
        "America/New_York",
        new Date("2026-10-29T16:00:00Z"),
      ),
    ).toBe(7);
  });
});

describe("totalDays · dayChips", () => {
  it("총 일수는 당일을 포함한다", () => {
    expect(totalDays("2026-10-24", "2026-10-27")).toBe(4);
    expect(totalDays("2026-10-24", "2026-10-24")).toBe(1);
  });

  it("일자 칩은 1일차부터 요일과 함께 만든다", () => {
    expect(dayChips("2026-10-24", "2026-10-27")).toEqual([
      { dayIndex: 1, date: "2026-10-24", weekday: "토" },
      { dayIndex: 2, date: "2026-10-25", weekday: "일" },
      { dayIndex: 3, date: "2026-10-26", weekday: "월" },
      { dayIndex: 4, date: "2026-10-27", weekday: "화" },
    ]);
  });

  it("달을 넘기는 기간도 이어서 만든다", () => {
    const chips = dayChips("2026-10-30", "2026-11-02");
    expect(chips.map((c) => c.date)).toEqual([
      "2026-10-30",
      "2026-10-31",
      "2026-11-01",
      "2026-11-02",
    ]);
  });
});

describe("포맷", () => {
  it("기간은 한 줄로, 하루짜리는 한 번만 쓴다", () => {
    expect(formatPeriod("2026-10-24", "2026-10-27")).toBe(
      "10월 24일 – 10월 27일",
    );
    expect(formatPeriod("2026-10-24", "2026-10-24")).toBe("10월 24일");
  });

  it("짧은 날짜는 월의 0을 떼고 일은 두 자리로 둔다", () => {
    expect(formatShortDate("2026-10-24")).toBe("10.24");
    expect(formatShortDate("2026-05-03")).toBe("5.03");
  });
});
