import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { PlannerEvent } from "../../api/feed";
import { PlannerCalendarCell } from "./PlannerCalendarCell";

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

const baseProps = {
  date: new Date(2026, 5, 10),
  isoDate: "2026-06-10",
  inMonth: true,
  isToday: false,
  isSelected: false,
  today: new Date(2026, 5, 10),
  onSelect: () => {},
  tasks: [],
  reviews: [],
};

describe("PlannerCalendarCell 일정 라인", () => {
  it("시간 일정은 시작 시각 + 제목, 종일은 제목만 보여준다", () => {
    render(
      <PlannerCalendarCell
        {...baseProps}
        events={[
          event({ id: "t", title: "회의", start: "2026-06-10T14:00:00" }),
          event({ id: "a", title: "여행", allDay: true, start: "2026-06-10" }),
        ]}
      />,
    );

    expect(screen.getByText("14:00")).toBeInTheDocument();
    expect(screen.getByText("회의")).toBeInTheDocument();
    expect(screen.getByText("여행")).toBeInTheDocument();
    // 종일은 시각 표기 없음(14:00만 존재)
    expect(screen.queryByText("종일")).not.toBeInTheDocument();
  });

  it("3개를 넘으면 +N개로 접는다", () => {
    render(
      <PlannerCalendarCell
        {...baseProps}
        events={Array.from({ length: 5 }, (_, i) =>
          event({
            id: `e${i}`,
            title: `일정${i}`,
            start: `2026-06-10T0${i}:00:00`,
          }),
        )}
      />,
    );

    expect(screen.getByText("+2개")).toBeInTheDocument();
  });

  it("공휴일이면 이름을 표시하고 aria-label에 포함한다", () => {
    render(
      <PlannerCalendarCell {...baseProps} events={[]} holidayName="현충일" />,
    );

    expect(screen.getByText("현충일")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /현충일/ })).toBeInTheDocument();
  });
});
