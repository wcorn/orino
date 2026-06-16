import { describe, expect, it } from "vitest";

import type { PlannerEvent } from "./api/feed";
import { layoutDayEvents, weekDays } from "./weekLayout";

function timed(id: string, start: string, end: string | null): PlannerEvent {
  return {
    id,
    title: id,
    allDay: false,
    start,
    end,
    location: null,
    recurring: false,
    source: "google",
  };
}

describe("weekDays", () => {
  it("cursor가 속한 일요일 시작 주의 7일을 반환한다", () => {
    // 2026-06-10은 수요일
    const days = weekDays(new Date(2026, 5, 10));
    expect(days).toHaveLength(7);
    expect(days[0].getDay()).toBe(0); // 일요일
    expect(days[0].getDate()).toBe(7); // 6/7(일)
    expect(days[6].getDate()).toBe(13); // 6/13(토)
  });
});

describe("layoutDayEvents", () => {
  it("종일/날짜 일정은 제외한다", () => {
    const allDay: PlannerEvent = {
      ...timed("a", "2026-06-10", null),
      allDay: true,
    };
    expect(layoutDayEvents([allDay])).toHaveLength(0);
  });

  it("겹치지 않는 일정은 전체 너비를 차지한다", () => {
    const result = layoutDayEvents([
      timed("a", "2026-06-10T09:00:00", "2026-06-10T10:00:00"),
      timed("b", "2026-06-10T11:00:00", "2026-06-10T12:00:00"),
    ]);
    expect(result).toHaveLength(2);
    for (const p of result) {
      expect(p.left).toBe(0);
      expect(p.width).toBe(1);
    }
    const a = result.find((p) => p.event.id === "a")!;
    expect(a.top).toBeCloseTo(9 / 24);
    expect(a.height).toBeCloseTo(1 / 24);
  });

  it("겹치는 두 일정은 절반씩 나눠 나란히 배치한다", () => {
    const result = layoutDayEvents([
      timed("a", "2026-06-10T09:00:00", "2026-06-10T10:30:00"),
      timed("b", "2026-06-10T10:00:00", "2026-06-10T11:00:00"),
    ]);
    expect(result).toHaveLength(2);
    const a = result.find((p) => p.event.id === "a")!;
    const b = result.find((p) => p.event.id === "b")!;
    expect(a.width).toBeCloseTo(0.5);
    expect(b.width).toBeCloseTo(0.5);
    expect(new Set([a.left, b.left])).toEqual(new Set([0, 0.5]));
  });

  it("끝나는 일정의 컬럼을 재사용한다(3개: A겹침B, A끝난뒤 C)", () => {
    const result = layoutDayEvents([
      timed("a", "2026-06-10T09:00:00", "2026-06-10T10:00:00"),
      timed("b", "2026-06-10T09:30:00", "2026-06-10T11:00:00"),
      timed("c", "2026-06-10T10:00:00", "2026-06-10T10:45:00"),
    ]);
    // A,B 겹침 → 2컬럼. C는 A 끝난 뒤 시작이라 A 컬럼 재사용(같은 클러스터, 2컬럼 유지)
    const c = result.find((p) => p.event.id === "c")!;
    expect(c.width).toBeCloseTo(0.5);
    expect(c.left).toBe(0);
  });

  it("end가 없으면 1시간으로, 너무 짧으면 최소 30분으로 본다", () => {
    const result = layoutDayEvents([
      timed("a", "2026-06-10T09:00:00", null),
      timed("b", "2026-06-10T13:00:00", "2026-06-10T13:05:00"),
    ]);
    const a = result.find((p) => p.event.id === "a")!;
    const b = result.find((p) => p.event.id === "b")!;
    expect(a.height).toBeCloseTo(1 / 24); // 1시간
    expect(b.height).toBeCloseTo(0.5 / 24); // 최소 30분
  });
});
