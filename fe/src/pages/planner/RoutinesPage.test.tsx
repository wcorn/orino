import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { RoutinesPage } from "./RoutinesPage";

const API_BASE = "https://api.orino.dev/api";

function connectedStatus() {
  server.use(
    http.get(`${API_BASE}/planner/google/status`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          connected: true,
          googleEmail: "me@gmail.com",
          scopes: "calendar",
          connectedAt: "2026-06-01T00:00:00Z",
        },
      }),
    ),
  );
}

function routinesResponse(routines: unknown[]) {
  server.use(
    http.get(`${API_BASE}/planner/routines`, () =>
      HttpResponse.json({ code: "OK", data: { routines } }),
    ),
  );
}

const HABIT = {
  recurringEventId: "r-habit-1",
  type: "habit",
  title: "운동하기",
  allDay: true,
  start: "2026-06-20",
  recurrence: { freq: "WEEKLY", byDay: ["MO", "WE", "FR"] },
  recurrenceText: "매주 월·수·금",
};
const SCHEDULE = {
  recurringEventId: "r-sched-1",
  type: "schedule",
  title: "스탠드업",
  allDay: false,
  start: "2026-06-20T09:00:00",
  end: "2026-06-20T09:15:00",
  recurrence: { freq: "DAILY" },
  recurrenceText: "매일",
};

describe("RoutinesPage", () => {
  it("종류별 그룹으로 시리즈를 렌더한다", async () => {
    connectedStatus();
    routinesResponse([HABIT, SCHEDULE]);

    renderWithRouter(<RoutinesPage />);

    expect(await screen.findByText("운동하기")).toBeInTheDocument();
    expect(screen.getByText("매주 월·수·금")).toBeInTheDocument();
    expect(screen.getByText("스탠드업")).toBeInTheDocument();
    expect(screen.getByText("습관")).toBeInTheDocument();
    expect(screen.getByText("고정 일정")).toBeInTheDocument();
  });

  it("루틴이 없으면 빈 상태를 보여준다", async () => {
    connectedStatus();
    routinesResponse([]);

    renderWithRouter(<RoutinesPage />);

    expect(
      await screen.findByText("아직 루틴이 없습니다."),
    ).toBeInTheDocument();
  });

  it("⋯ 메뉴에서 수정/삭제 진입점을 보여준다", async () => {
    connectedStatus();
    routinesResponse([HABIT]);

    renderWithRouter(<RoutinesPage />);

    await userEvent.click(
      await screen.findByRole("button", { name: "운동하기 메뉴" }),
    );

    expect(
      await screen.findByRole("menuitem", { name: "수정" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "삭제" })).toBeInTheDocument();
  });

  it("미연동이면 연결 CTA를 보여준다", async () => {
    // 기본 핸들러가 connected:false를 반환한다.
    renderWithRouter(<RoutinesPage />);

    expect(
      await screen.findByText("Google 연결이 필요합니다."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Google 연결" }),
    ).toBeInTheDocument();
  });
});
