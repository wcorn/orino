import { describe, expect, it } from "vitest";

import { planLegDates } from "./legPlan";

/**
 * 구간 → 날짜 전개. **BE `LegExpanderTest`와 같은 케이스**를 쓴다 — 화면이 보여주는 예고와
 * 서버가 실제로 저장하는 결과가 다르면, 사용자는 저장하고 나서야 어긋난 걸 알게 된다.
 */

describe("planLegDates", () => {
  it("[3][1][1] + 10.24~10.28 → 순서대로 날짜를 나눠 갖는다", () => {
    const plan = planLegDates("2026-10-24", "2026-10-28", [3, 1, 1]);

    expect(plan.dates).toEqual([
      { startDate: "2026-10-24", endDate: "2026-10-26" },
      { startDate: "2026-10-27", endDate: "2026-10-27" },
      { startDate: "2026-10-28", endDate: "2026-10-28" },
    ]);
    expect(plan.verdict).toBe("exact");
    expect(plan.diff).toBe(0);
  });

  it("합계가 모자라면 마지막 구간이 남은 날짜를 이어 쓴다", () => {
    // 합계 5일 / 기간 10일
    const plan = planLegDates("2026-10-24", "2026-11-02", [3, 1, 1]);

    expect(plan.verdict).toBe("short");
    expect(plan.diff).toBe(5);
    expect(plan.dates[2]).toEqual({
      startDate: "2026-10-28",
      endDate: "2026-11-02",
    });
  });

  it("합계가 넘치면 뒤 구간이 잘린다 — 잘린 구간은 날짜가 없다", () => {
    // 합계 12일 / 기간 4일
    const plan = planLegDates("2026-10-24", "2026-10-27", [3, 4, 5]);

    expect(plan.verdict).toBe("over");
    expect(plan.diff).toBe(8);
    expect(plan.dates[0]).toEqual({
      startDate: "2026-10-24",
      endDate: "2026-10-26",
    });
    expect(plan.dates[1]).toEqual({
      startDate: "2026-10-27",
      endDate: "2026-10-27",
    });
    expect(plan.dates[2]).toBeNull();
  });

  it("구간이 하나면 전 기간을 차지한다", () => {
    const plan = planLegDates("2026-10-24", "2026-10-27", [1]);

    expect(plan.dates[0]).toEqual({
      startDate: "2026-10-24",
      endDate: "2026-10-27",
    });
    expect(plan.verdict).toBe("short");
  });

  it("기간을 아직 안 정했으면 날짜를 계산하지 않는다 — 빈 값으로 추측하지 않는다", () => {
    const plan = planLegDates("", "", [3]);

    expect(plan.dates).toEqual([null]);
    expect(plan.period).toBe(0);
  });

  it("종료일이 시작일보다 빠르면 기간이 없다", () => {
    expect(planLegDates("2026-10-27", "2026-10-24", [3]).period).toBe(0);
  });

  it("구간이 없으면 날짜도 없다", () => {
    const plan = planLegDates("2026-10-24", "2026-10-27", []);

    expect(plan.dates).toEqual([]);
    expect(plan.sum).toBe(0);
    expect(plan.verdict).toBe("short");
  });
});
