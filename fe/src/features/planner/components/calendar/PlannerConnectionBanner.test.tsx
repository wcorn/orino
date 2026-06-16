import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { renderWithRouter } from "@/test/render";

import type { PlannerCalendarFeed } from "../../api/feed";
import { PlannerConnectionBanner } from "./PlannerConnectionBanner";

function feed(overrides: Partial<PlannerCalendarFeed>): PlannerCalendarFeed {
  return {
    from: "2026-06-01",
    to: "2026-06-30",
    googleConnected: true,
    partial: false,
    errors: [],
    events: [],
    tasks: [],
    reviews: [],
    ...overrides,
  };
}

describe("PlannerConnectionBanner", () => {
  it("미연동: 연결 CTA를 보여준다", () => {
    renderWithRouter(
      <PlannerConnectionBanner feed={feed({ googleConnected: false })} />,
    );

    expect(
      screen.getByText("Google 캘린더가 연결되지 않았습니다."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Google 연결" }),
    ).toBeInTheDocument();
  });

  it("재연동 필요(google 에러): 다시 연결 CTA를 보여준다", () => {
    renderWithRouter(
      <PlannerConnectionBanner
        feed={feed({
          googleConnected: false,
          partial: true,
          errors: [{ source: "google-events", message: "만료" }],
        })}
      />,
    );

    expect(
      screen.getByText("Google 연동이 만료되었습니다."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "다시 연결" }),
    ).toBeInTheDocument();
  });

  it("부분 실패: 경고만 보이고 CTA는 없다", () => {
    renderWithRouter(
      <PlannerConnectionBanner
        feed={feed({
          googleConnected: true,
          partial: true,
          errors: [{ source: "google-tasks", message: "x" }],
        })}
      />,
    );

    expect(
      screen.getByText("일부 일정을 불러오지 못했습니다."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("정상 연동이면 아무것도 렌더하지 않는다", () => {
    renderWithRouter(
      <PlannerConnectionBanner feed={feed({ googleConnected: true })} />,
    );
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("로딩(feed 없음)이면 렌더하지 않는다", () => {
    renderWithRouter(<PlannerConnectionBanner feed={undefined} />);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
