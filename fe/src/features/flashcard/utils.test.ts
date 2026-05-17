import { describe, expect, it } from "vitest";

import { formatNextReview } from "./utils";

describe("formatNextReview", () => {
  const today = new Date(2026, 4, 18);

  it("같은 날이면 오늘", () => {
    expect(
      formatNextReview(
        {
          id: 1,
          sequence: 1,
          scheduledDate: "2026-05-18",
          intervalDays: 1,
          easeFactor: 2.5,
        },
        today,
      ),
    ).toBe("5/18 (오늘) · 1회차");
  });

  it("미래 날짜는 N일 후", () => {
    expect(
      formatNextReview(
        {
          id: 1,
          sequence: 2,
          scheduledDate: "2026-05-24",
          intervalDays: 6,
          easeFactor: 2.5,
        },
        today,
      ),
    ).toBe("5/24 (6일 후) · 2회차");
  });

  it("지난 날짜는 N일 지남", () => {
    expect(
      formatNextReview(
        {
          id: 1,
          sequence: 3,
          scheduledDate: "2026-05-15",
          intervalDays: 1,
          easeFactor: 2.5,
        },
        today,
      ),
    ).toBe("5/15 (3일 지남) · 3회차");
  });
});
