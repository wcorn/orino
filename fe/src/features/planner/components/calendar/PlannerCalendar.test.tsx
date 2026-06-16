import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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
  errors?: { source: string; message: string }[];
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
          errors: overrides.errors ?? [],
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
          googleEvent({
            start: `${TODAY}T14:00:00`,
            end: `${TODAY}T15:00:00`,
            location: "3층",
          }),
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

  it("재연동 필요(invalid_grant)면 다시 연결 배너를 보여준다", async () => {
    mockFeed({
      googleConnected: false,
      partial: true,
      errors: [{ source: "google-events", message: "만료" }],
    });

    renderWithRouter(<PlannerCalendar />);

    expect(
      await screen.findByText("Google 연동이 만료되었습니다."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "다시 연결" }),
    ).toBeInTheDocument();
  });

  const taskOnToday = {
    id: "t1",
    title: "리포트",
    due: TODAY,
    completed: false,
    notes: null,
    source: "google",
  };

  function googleEvent(overrides: Record<string, unknown> = {}) {
    return {
      id: "e1",
      title: "회의",
      allDay: false,
      start: `${TODAY}T09:00:00`,
      end: `${TODAY}T10:00:00`,
      location: null,
      recurring: false,
      source: "google",
      ...overrides,
    };
  }

  it("할 일 완료 토글: 체크박스를 누르면 PATCH한다", async () => {
    mockGoogleConnected(true);
    mockFeed({ tasks: [taskOnToday] });
    let patched: unknown = null;
    server.use(
      http.patch(`${API_BASE}/planner/tasks/t1`, async ({ request }) => {
        patched = await request.json();
        return HttpResponse.json({
          code: "OK",
          data: { ...taskOnToday, completed: true },
        });
      }),
    );

    renderWithRouter(<PlannerCalendar />);

    await userEvent.click(await screen.findByLabelText("리포트 완료"));

    await waitFor(() => expect(patched).toMatchObject({ completed: true }));
  });

  it("할 일 삭제: 삭제 버튼을 누르면 DELETE한다", async () => {
    mockGoogleConnected(true);
    mockFeed({ tasks: [taskOnToday] });
    let deleted = false;
    server.use(
      http.delete(`${API_BASE}/planner/tasks/t1`, () => {
        deleted = true;
        return HttpResponse.json({ code: "OK", data: null });
      }),
    );

    renderWithRouter(<PlannerCalendar />);

    await userEvent.click(await screen.findByLabelText("리포트 삭제"));

    await waitFor(() => expect(deleted).toBe(true));
  });

  it("+할 일로 할 일을 생성하면 POST한다", async () => {
    mockGoogleConnected(true);
    mockFeed({ googleConnected: true });
    let posted: unknown = null;
    server.use(
      http.post(`${API_BASE}/planner/tasks`, async ({ request }) => {
        posted = await request.json();
        return HttpResponse.json({
          code: "OK",
          data: { ...taskOnToday, id: "new" },
        });
      }),
    );

    renderWithRouter(<PlannerCalendar />);

    await userEvent.click(await screen.findByRole("button", { name: "할 일" }));
    await userEvent.type(await screen.findByLabelText("제목"), "리포트");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(posted).toMatchObject({ title: "리포트" }));
  });

  it("+일정으로 일정을 생성하면 POST하고 다이얼로그를 닫는다", async () => {
    mockGoogleConnected(true);
    mockFeed({ googleConnected: true });
    let posted: unknown = null;
    server.use(
      http.post(`${API_BASE}/planner/calendar/events`, async ({ request }) => {
        posted = await request.json();
        return HttpResponse.json({
          code: "OK",
          data: googleEvent({ id: "new-1" }),
        });
      }),
    );

    renderWithRouter(<PlannerCalendar />);

    await userEvent.click(await screen.findByRole("button", { name: "일정" }));
    await userEvent.type(await screen.findByLabelText("제목"), "회의");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() =>
      expect(posted).toMatchObject({ title: "회의", allDay: false }),
    );
    await waitFor(() =>
      expect(screen.queryByLabelText("제목")).not.toBeInTheDocument(),
    );
  });

  it("주 뷰로 전환하면 시간축과 시간대 일정 블록을 보여준다", async () => {
    mockGoogleConnected(true);
    mockFeed({
      googleConnected: true,
      events: [googleEvent()],
    });

    renderWithRouter(<PlannerCalendar />);

    await userEvent.click(await screen.findByRole("button", { name: "주" }));

    expect(await screen.findByText("9시")).toBeInTheDocument();
    expect(await screen.findByText("회의")).toBeInTheDocument();
  });

  it("주 뷰 빈 시간대 클릭 시 그 시각으로 생성 다이얼로그가 열린다", async () => {
    mockGoogleConnected(true);
    mockFeed({ googleConnected: true });

    renderWithRouter(<PlannerCalendar />);

    await userEvent.click(await screen.findByRole("button", { name: "주" }));
    await userEvent.click(
      await screen.findByLabelText(`${TODAY} 10시 일정 추가`),
    );

    expect(await screen.findByLabelText("제목")).toBeInTheDocument();
    expect(screen.getByLabelText("시작 시간")).toHaveValue("10:00");
  });

  it("일 뷰로 전환하면 단일 날짜의 시간축과 일정을 보여준다", async () => {
    mockGoogleConnected(true);
    mockFeed({
      events: [googleEvent()],
    });

    renderWithRouter(<PlannerCalendar />);

    await userEvent.click(await screen.findByRole("button", { name: "일" }));

    expect(await screen.findByText("9시")).toBeInTheDocument();
    expect(await screen.findByText("회의")).toBeInTheDocument();
    expect(screen.getByLabelText(`${TODAY} 9시 일정 추가`)).toBeInTheDocument();
  });
});
