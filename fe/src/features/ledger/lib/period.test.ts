import { describe, expect, it } from "vitest";

import { periodLabel, periodOf } from "./period";

/**
 * 월 시작일 — 급여일 기준으로 살면 「8월」이 8월 1일에 시작하지 않는다.
 *
 * <p>서버(`GET /summary`)도 같은 계산을 한다. 두 곳이 갈리면 합계 바와 목록이
 * 서로 다른 달을 말하므로, 경계값을 여기서 못 박는다.
 */
describe("월 구간", () => {
  it("월 시작일이 1이면 달력 달 그대로다", () => {
    expect(periodOf(new Date(2026, 7, 15), 1)).toEqual({
      start: "2026-08-01",
      end: "2026-08-31",
    });
  });

  it("시작일이 25일이면 8월 20일은 아직 7월 25일 구간이다", () => {
    expect(periodOf(new Date(2026, 7, 20), 25)).toEqual({
      start: "2026-07-25",
      end: "2026-08-24",
    });
  });

  it("시작일 당일이면 그날부터 새 구간이다", () => {
    expect(periodOf(new Date(2026, 7, 25), 25)).toEqual({
      start: "2026-08-25",
      end: "2026-09-24",
    });
  });

  it("말일(99)은 달마다 길이가 다른 구간이 된다", () => {
    // 2월은 28일이 말일이다 — 「30일 시작」을 허용하지 않는 이유이기도 하다.
    expect(periodOf(new Date(2026, 1, 10), 99)).toEqual({
      start: "2026-01-31",
      end: "2026-02-27",
    });
  });

  it("앞뒤로 구간을 옮긴다", () => {
    expect(periodOf(new Date(2026, 7, 15), 1, -1).start).toBe("2026-07-01");
    expect(periodOf(new Date(2026, 7, 15), 1, 1).start).toBe("2026-09-01");
  });

  it("구간 이름은 시작한 달을 쓴다 — 사람이 그 기간을 부르는 이름이다", () => {
    expect(periodLabel({ start: "2026-07-25", end: "2026-08-24" })).toBe(
      "2026년 7월",
    );
  });
});
