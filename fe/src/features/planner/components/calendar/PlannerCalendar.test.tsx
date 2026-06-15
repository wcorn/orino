import { screen } from "@testing-library/react";
import { delay, http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { startOfDay, toIsoDate } from "@/features/review/calendar";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { PlannerCalendar } from "./PlannerCalendar";

const API_BASE = "https://api.orino.dev/api";
const TODAY = toIsoDate(startOfDay(new Date()));

interface FeedOverrides {
  googleConnected?: boolean;
  partial?: boolean;
  events?: unknown[];
  reviews?: unknown[];
  tasks?: unknown[];
}

function mockFeed(
  overrides: FeedOverrides = {},
  options?: { delayMs?: number },
) {
  server.use(
    http.get(`${API_BASE}/planner/calendar`, async () => {
      if (options?.delayMs) {
        await delay(options.delayMs);
      }
      return HttpResponse.json({
        code: "OK",
        data: {
          from: TODAY,
          to: TODAY,
          googleConnected: overrides.googleConnected ?? true,
          partial: overrides.partial ?? false,
          errors: [],
          events: overrides.events ?? [],
          tasks: overrides.tasks ?? [],
          reviews: overrides.reviews ?? [],
        },
      });
    }),
  );
}

function mockGoogleConnected(connected: boolean) {
  server.use(
    http.get(`${API_BASE}/planner/google/status`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          connected,
          googleEmail: connected ? "me@gmail.com" : null,
          scopes: connected ? ["s"] : null,
          connectedAt: connected ? "2026-06-15T10:00:00" : null,
        },
      }),
    ),
  );
}

describe("PlannerCalendar", () => {
  it("로딩 중 스켈레톤을 보이고, 이후 일정과 복습을 그날 상세에 렌더한다", async () => {
    mockGoogleConnected(true);
    mockFeed(
      {
        events: [
          {
            id: "e1",
            title: "회의",
            allDay: false,
            start: `${TODAY}T14:00:00`,
            end: `${TODAY}T15:00:00`,
            location: "3층",
            recurring: false,
            source: "google",
          },
        ],
        reviews: [
          {
            id: 1,
            scheduledAt: `${TODAY}T04:00:00`,
            status: "PENDING",
            materialTitle: "이펙티브 자바",
            front: "Q1",
            readOnly: true,
            source: "review",
          },
        ],
      },
      { delayMs: 50 },
    );

    renderWithRouter(<PlannerCalendar />);

    expect(screen.getByLabelText("불러오는 중")).toBeInTheDocument();

    expect(await screen.findByText("회의")).toBeInTheDocument();
    expect(screen.getByText("이펙티브 자바")).toBeInTheDocument();
    expect(screen.getByText("Q1")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "오늘 복습 하러가기" }),
    ).toBeInTheDocument();
  });

  it("미연동이면 연결 배너를 보여준다", async () => {
    mockGoogleConnected(false);
    mockFeed({ googleConnected: false });

    renderWithRouter(<PlannerCalendar />);

    expect(
      await screen.findByText(/Google 캘린더가 연결되지 않았습니다/),
    ).toBeInTheDocument();
  });

  it("부분 실패면 경고 배너를 보여준다", async () => {
    mockGoogleConnected(true);
    mockFeed({ partial: true });

    renderWithRouter(<PlannerCalendar />);

    expect(
      await screen.findByText("일부 일정을 불러오지 못했습니다."),
    ).toBeInTheDocument();
  });
});
