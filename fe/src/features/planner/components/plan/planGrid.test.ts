import { describe, expect, it } from "vitest";

import {
  createBlocks,
  type EditableBlock,
  isReversed,
  layoutDay,
  minutesToTime,
  nextKey,
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

  it("isReversed는 종료<=시작이면 true", () => {
    expect(isReversed("10:00", "09:00")).toBe(true);
    expect(isReversed("10:00", "10:00")).toBe(true);
    expect(isReversed("09:00", "10:00")).toBe(false);
  });

  it("createBlocks는 선택한 여러 요일 각각에 동일 블록을 만든다", () => {
    const blocks = createBlocks([1, 3, 5], "09:00", "10:30", "공부", "sky");
    expect(blocks).toHaveLength(3);
    expect(blocks.map((b) => b.dayOfWeek)).toEqual([1, 3, 5]);
    blocks.forEach((b) => {
      expect(b).toMatchObject({
        startTime: "09:00",
        endTime: "10:30",
        label: "공부",
        color: "sky",
        id: null,
      });
    });
    // 각 블록은 고유 key
    expect(new Set(blocks.map((b) => b.key)).size).toBe(3);
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
