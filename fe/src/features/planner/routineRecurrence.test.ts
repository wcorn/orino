import { describe, expect, it } from "vitest";

import { recurrencePreview, recurrenceText } from "./routineRecurrence";

describe("recurrenceText", () => {
  it("매일 / N일마다", () => {
    expect(recurrenceText({ freq: "DAILY" })).toBe("매일");
    expect(recurrenceText({ freq: "DAILY", interval: 3 })).toBe("3일마다");
  });

  it("매주 요일 / N주마다", () => {
    expect(recurrenceText({ freq: "WEEKLY", byDay: ["MO", "WE", "FR"] })).toBe(
      "매주 월·수·금",
    );
    expect(recurrenceText({ freq: "WEEKLY", interval: 2, byDay: ["TU"] })).toBe(
      "2주마다 화",
    );
  });

  it("매월 일자 / N개월마다", () => {
    expect(recurrenceText({ freq: "MONTHLY", byMonthDay: [1, 15] })).toBe(
      "매월 1·15일",
    );
    expect(
      recurrenceText({ freq: "MONTHLY", interval: 3, byMonthDay: [1] }),
    ).toBe("3개월마다 1일");
  });
});

describe("recurrencePreview", () => {
  it("시작일과 종료일을 덧붙인다", () => {
    expect(
      recurrencePreview(
        { freq: "WEEKLY", byDay: ["MO", "WE", "FR"] },
        "2026-06-20",
      ),
    ).toBe("매주 월·수·금 · 2026-06-20부터");

    expect(
      recurrencePreview({ freq: "DAILY", until: "2026-12-31" }, "2026-06-20"),
    ).toBe("매일 · 2026-06-20부터 ~ 2026-12-31까지");
  });
});
