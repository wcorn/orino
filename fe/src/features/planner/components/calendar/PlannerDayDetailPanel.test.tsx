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

describe("PlannerDayDetailPanel 일정 아젠다", () => {
  it("시작·종료 시각을 함께 보여주고 종일 먼저·시간순으로 정렬한다", () => {
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

    // 시작·종료 동시 표기
    expect(screen.getByText("14:00")).toBeInTheDocument();
    expect(screen.getByText("15:00")).toBeInTheDocument();
    expect(screen.getByText("종일")).toBeInTheDocument();

    // 정렬: 종일(여행) → 09:00(회의) → 14:00(치과 예약)
    const titles = screen
      .getAllByRole("button")
      .map((b) => b.textContent)
      .filter((t) => t && /여행|회의|치과 예약/.test(t));
    expect(titles).toEqual(["여행", "회의", "치과 예약"]);
  });
});
