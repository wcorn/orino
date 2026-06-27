import { describe, expect, it } from "vitest";

import {
  blockAtHour,
  type EditableBlock,
  isReversed,
  layoutDay,
  minutesToTime,
  nextKey,
  snap,
  timeToMinutes,
} from "./planGrid";

function block(start: string, end: string): EditableBlock {
  return {
    key: nextKey(),
    id: null,
    dayOfWeek: 1,
    startTime: start,
    endTime: end,
    label: "x",
    color: "violet",
  };
}

describe("planGrid", () => {
  it("timeToMinutes / minutesToTime 왕복", () => {
    expect(timeToMinutes("08:30")).toBe(510);
    expect(minutesToTime(510)).toBe("08:30");
    expect(minutesToTime(0)).toBe("00:00");
    expect(minutesToTime(1500)).toBe("23:59"); // 클램프
  });

  it("snap은 30분 격자로 반올림", () => {
    expect(snap(40)).toBe(30);
    expect(snap(46)).toBe(60);
    expect(snap(74)).toBe(60);
    expect(snap(75)).toBe(90); // 정확히 중간은 올림
  });

  it("isReversed는 종료<=시작이면 true", () => {
    expect(isReversed("10:00", "09:00")).toBe(true);
    expect(isReversed("10:00", "10:00")).toBe(true);
    expect(isReversed("09:00", "10:00")).toBe(false);
  });

  it("blockAtHour는 1시간 기본 블록, 23시는 23:59까지", () => {
    expect(blockAtHour(2, 9)).toMatchObject({
      dayOfWeek: 2,
      startTime: "09:00",
      endTime: "10:00",
    });
    expect(blockAtHour(2, 23)).toMatchObject({
      startTime: "23:00",
      endTime: "23:59",
    });
  });

  it("겹치지 않는 블록은 전체 폭(width=1)", () => {
    const out = layoutDay([block("08:00", "09:00"), block("10:00", "12:00")]);
    expect(out).toHaveLength(2);
    out.forEach((b) => {
      expect(b.width).toBe(1);
      expect(b.left).toBe(0);
    });
  });

  it("맞닿은 블록(끝=다음 시작)은 겹침 아님 → 전체 폭", () => {
    const out = layoutDay([block("10:00", "12:00"), block("12:00", "13:00")]);
    out.forEach((b) => expect(b.width).toBe(1));
  });

  it("겹치는 두 블록은 반폭으로 나란히", () => {
    const out = layoutDay([block("10:00", "12:00"), block("11:00", "13:00")]);
    expect(out).toHaveLength(2);
    out.forEach((b) => expect(b.width).toBeCloseTo(0.5));
    expect(out.map((b) => b.left).sort()).toEqual([0, 0.5]);
  });
});
