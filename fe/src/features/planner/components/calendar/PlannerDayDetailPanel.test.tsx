import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { renderWithRouter } from "@/test/render";

import type { PlannerEvent } from "../../api/feed";
import { PlannerDayDetailPanel } from "./PlannerDayDetailPanel";

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

describe("PlannerDayDetailPanel 일정 타임라인", () => {
  it("종일은 상단 줄, 시간 일정은 시작·종료가 붙은 블럭으로 보여준다", () => {
    const events = [
      event({
        id: "pm",
        title: "치과 예약",
        start: "2026-06-10T14:00:00",
        end: "2026-06-10T15:00:00",
      }),
      event({
        id: "allday",
        title: "여행",
        allDay: true,
        start: "2026-06-10",
        end: null,
      }),
      event({
        id: "am",
        title: "회의",
        start: "2026-06-10T09:00:00",
        end: "2026-06-10T10:00:00",
      }),
    ];

    renderWithRouter(
      <PlannerDayDetailPanel
        isoDate="2026-06-10"
        events={events}
        tasks={[]}
        reviews={[]}
      />,
    );

    // 종일 일정은 상단 줄
    expect(screen.getByText("여행")).toBeInTheDocument();
    expect(screen.getByText("종일")).toBeInTheDocument();

    // 시간 일정은 시작–종료가 붙은 블럭
    expect(screen.getByText("회의")).toBeInTheDocument();
    expect(screen.getByText(/09:00.10:00/)).toBeInTheDocument();
    expect(screen.getByText("치과 예약")).toBeInTheDocument();
    expect(screen.getByText(/14:00.15:00/)).toBeInTheDocument();

    // 24시간 그리드(시간 라벨)
    expect(screen.getByText("0시")).toBeInTheDocument();
    expect(screen.getByText("23시")).toBeInTheDocument();
  });
});
