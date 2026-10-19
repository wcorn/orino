import { describe, expect, it } from "vitest";

import { formatMomentTime } from "./datetime";

/**
 * 테스트 기기의 타임존은 고정할 수 없다. 그래서 로컬 시각으로 만든 Date를 넣는다 —
 * 함수도 로컬 시각으로 읽으므로 결과가 기기와 무관하다.
 */
describe("formatMomentTime", () => {
  it("오후를 오전/오후 없이 24시간제로 쓴다(#1430)", () => {
    const at = new Date(2026, 6, 20, 14, 30).toISOString();
    expect(formatMomentTime(at)).toBe("7월 20일 14:30");
  });

  it("자정은 00시다", () => {
    const at = new Date(2026, 6, 20, 0, 5).toISOString();
    expect(formatMomentTime(at)).toBe("7월 20일 00:05");
  });

  it("잘못된 값이면 빈 문자열이다", () => {
    expect(formatMomentTime("nope")).toBe("");
  });
});
