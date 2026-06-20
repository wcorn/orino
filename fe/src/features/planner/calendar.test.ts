import { describe, expect, it } from "vitest";

import type { PlannerEvent } from "./api/feed";
import { eventTimeParts, sortDayEvents } from "./calendar";

function event(partial: Partial<PlannerEvent>): PlannerEvent {
  return {
    id: "e",
    title: "일정",
    allDay: false,
    start: "2026-06-10T09:00:00",
    end: "2026-06-10T10:00:00",
    location: null,
    recurring: false,
    source: "google",
    ...partial,
  };
}

describe("eventTimeParts", () => {
  it("시간 일정은 시작/종료를 분리한다", () => {
    expect(
      eventTimeParts(
        event({ start: "2026-06-10T14:00:00", end: "2026-06-10T15:30:00" }),
      ),
    ).toEqual({ start: "14:00", end: "15:30" });
  });

  it("종료가 없으면 end는 null", () => {
    expect(eventTimeParts(event({ end: null }))).toEqual({
      start: "09:00",
      end: null,
    });
  });

  it("종일은 '종일' + end null", () => {
    expect(
      eventTimeParts(event({ allDay: true, start: "2026-06-10", end: null })),
    ).toEqual({ start: "종일", end: null });
  });
});

describe("sortDayEvents", () => {
  it("종일을 먼저, 시간 일정은 시작 시각 오름차순으로 정렬한다", () => {
    const events = [
      event({ id: "pm", start: "2026-06-10T14:00:00" }),
      event({ id: "allday", allDay: true, start: "2026-06-10", end: null }),
      event({ id: "am", start: "2026-06-10T09:00:00" }),
    ];

    expect(sortDayEvents(events).map((e) => e.id)).toEqual([
      "allday",
      "am",
      "pm",
    ]);
  });

  it("원본 배열을 변경하지 않는다", () => {
    const events = [
      event({ id: "b", start: "2026-06-10T14:00:00" }),
      event({ id: "a", start: "2026-06-10T09:00:00" }),
    ];
    sortDayEvents(events);
    expect(events.map((e) => e.id)).toEqual(["b", "a"]);
  });
});
