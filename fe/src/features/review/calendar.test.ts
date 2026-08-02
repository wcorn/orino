import { describe, expect, it } from "vitest";

import type { CalendarReview } from "./api/calendar";
import {
  addMonths,
  classifyReview,
  countBuckets,
  groupByDate,
  monthGridDays,
  toIsoDate,
} from "./calendar";

function review(date: string, status: "PENDING" | "COMPLETED"): CalendarReview {
  return {
    id: Math.floor(Math.random() * 1e6),
    scheduledAt: `${date}T04:00:00`,
    status,
    rating: status === "COMPLETED" ? "GOOD" : null,
    sequence: 1,
    flashcard: {
      id: 1,
      front: "Q",
      material: { id: 1, title: "M", type: "BOOK" },
    },
  };
}

describe("monthGridDays", () => {
  it("2026-05 (목요일 시작 달)은 6주 42칸, 일요일부터 시작한다", () => {
    const days = monthGridDays(2026, 4); // 0-based: 4 = May
    expect(days).toHaveLength(42);
    expect(days[0].getDay()).toBe(0); // 일요일
    // 5/1은 금요일 → 그리드 첫날은 4/26(일)
    expect(toIsoDate(days[0])).toBe("2026-04-26");
    expect(toIsoDate(days[41])).toBe("2026-06-06");
  });
});

describe("addMonths", () => {
  it("월 이동은 해당 월 1일을 반환한다", () => {
    expect(toIsoDate(addMonths(new Date(2026, 4, 18), 1))).toBe("2026-06-01");
    expect(toIsoDate(addMonths(new Date(2026, 0, 15), -1))).toBe("2025-12-01");
  });
});

describe("classifyReview", () => {
  const now = new Date(2026, 4, 18, 11, 0); // 학습일 5/18 한낮

  it("COMPLETED → completed", () => {
    expect(classifyReview(review("2026-05-10", "COMPLETED"), now)).toBe(
      "completed",
    );
  });

  it("PENDING + 과거 → overdue", () => {
    expect(classifyReview(review("2026-05-15", "PENDING"), now)).toBe(
      "overdue",
    );
  });

  it("PENDING + 오늘 → today", () => {
    expect(classifyReview(review("2026-05-18", "PENDING"), now)).toBe("today");
  });

  it("PENDING + 미래 → upcoming", () => {
    expect(classifyReview(review("2026-05-20", "PENDING"), now)).toBe(
      "upcoming",
    );
  });

  it("새벽 2시는 아직 전날 학습일이라 그날 04:00 복습은 upcoming (#1003)", () => {
    const lateNight = new Date(2026, 4, 18, 2, 0); // 학습일 5/17

    expect(classifyReview(review("2026-05-18", "PENDING"), lateNight)).toBe(
      "upcoming",
    );
    expect(classifyReview(review("2026-05-17", "PENDING"), lateNight)).toBe(
      "today",
    );
  });
});

describe("groupByDate / countBuckets", () => {
  it("날짜별로 그룹핑하고 버킷 카운트를 센다", () => {
    const now = new Date(2026, 4, 18, 11, 0); // 학습일 5/18 한낮
    const reviews = [
      review("2026-05-18", "PENDING"), // today
      review("2026-05-18", "COMPLETED"), // completed
      review("2026-05-15", "PENDING"), // overdue
    ];
    const grouped = groupByDate(reviews);
    expect(grouped.get("2026-05-18")).toHaveLength(2);
    expect(grouped.get("2026-05-15")).toHaveLength(1);

    const counts = countBuckets(grouped.get("2026-05-18")!, now);
    expect(counts.today).toBe(1);
    expect(counts.completed).toBe(1);
    expect(counts.overdue).toBe(0);
  });
});
