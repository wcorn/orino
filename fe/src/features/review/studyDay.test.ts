import { describe, expect, it } from "vitest";

import { studyDayDiff, studyDayStart } from "./studyDay";

describe("studyDayStart", () => {
  it("자정~04:00은 전날 학습일에 속한다", () => {
    expect(studyDayStart(new Date(2026, 5, 10, 0, 0))).toEqual(
      new Date(2026, 5, 9, 4, 0),
    );
    expect(studyDayStart(new Date(2026, 5, 10, 3, 59))).toEqual(
      new Date(2026, 5, 9, 4, 0),
    );
  });

  it("04:00부터 새 학습일이다", () => {
    expect(studyDayStart(new Date(2026, 5, 10, 4, 0))).toEqual(
      new Date(2026, 5, 10, 4, 0),
    );
    expect(studyDayStart(new Date(2026, 5, 10, 23, 59))).toEqual(
      new Date(2026, 5, 10, 4, 0),
    );
  });
});

describe("studyDayDiff", () => {
  it("같은 학습일이면 0 — 밤 11시와 다음날 새벽 2시는 같은 날이다", () => {
    expect(
      studyDayDiff(new Date(2026, 5, 10, 23, 0), new Date(2026, 5, 11, 2, 0)),
    ).toBe(0);
  });

  it("04:00을 넘기면 다음 학습일이다", () => {
    expect(
      studyDayDiff(new Date(2026, 5, 11, 3, 0), new Date(2026, 5, 11, 5, 0)),
    ).toBe(1);
  });

  it("지난 학습일은 음수", () => {
    expect(
      studyDayDiff(new Date(2026, 5, 11, 12, 0), new Date(2026, 5, 10, 12, 0)),
    ).toBe(-1);
  });

  it("서머타임 없는 로컬 기준 날짜 차를 그대로 센다", () => {
    expect(
      studyDayDiff(new Date(2026, 5, 1, 12, 0), new Date(2026, 5, 8, 12, 0)),
    ).toBe(7);
  });
});
