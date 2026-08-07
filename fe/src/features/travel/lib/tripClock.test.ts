import { describe, expect, it } from "vitest";

import { toTimeInputValue, toWallClockTime } from "./tripClock";

describe("toWallClockTime", () => {
  it("HH:mm을 그대로 둔다", () => {
    expect(toWallClockTime("09:00")).toBe("09:00");
    expect(toWallClockTime("23:59")).toBe("23:59");
    expect(toWallClockTime("00:00")).toBe("00:00");
  });

  it("초가 붙어 와도 HH:mm으로 자른다", () => {
    // input type=\"time\"이 step에 따라 초를 붙여 준다.
    expect(toWallClockTime("09:00:00")).toBe("09:00");
  });

  it("비어 있으면 null — 시각 없는 일정은 정상이다", () => {
    expect(toWallClockTime("")).toBeNull();
    expect(toWallClockTime(null)).toBeNull();
    expect(toWallClockTime(undefined)).toBeNull();
    expect(toWallClockTime("   ")).toBeNull();
  });

  it("형식이 아니면 null", () => {
    expect(toWallClockTime("9:00")).toBeNull();
    expect(toWallClockTime("24:00")).toBeNull();
    expect(toWallClockTime("09:60")).toBeNull();
    expect(toWallClockTime("오전 9시")).toBeNull();
  });

  it("기기 타임존에 흔들리지 않는다 — 문자열 그대로다", () => {
    // Date로 파싱했다면 서울에서 도쿄 일정을 열 때 값이 달라졌을 것이다.
    const original = "09:00";
    expect(toWallClockTime(original)).toBe(original);
    expect(toWallClockTime(toWallClockTime(original))).toBe(original);
  });
});

describe("toTimeInputValue", () => {
  it("없으면 빈 문자열을 준다(컨트롤드 입력 유지)", () => {
    expect(toTimeInputValue(null)).toBe("");
    expect(toTimeInputValue(undefined)).toBe("");
  });

  it("있으면 HH:mm", () => {
    expect(toTimeInputValue("09:00:00")).toBe("09:00");
  });
});
