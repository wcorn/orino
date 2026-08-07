import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

const API_BASE = "https://api.orino.dev/api";

function mockTravelSummary(data: unknown) {
  server.use(
    http.get(`${API_BASE}/travel/summary`, () =>
      HttpResponse.json({ code: "OK", data }),
    ),
  );
}

function renderApp(initialEntries: string[]) {
  return renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries },
  );
}

describe("WorkspaceSelectPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  it("두 워크스페이스 카드를 보여준다", async () => {
    renderApp(["/select"]);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "어디로 갈까요" }),
      ).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: /여행/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /일상/ })).toBeInTheDocument();
  });

  it("사이드바가 없다 — 선택 화면은 앱 셸 밖이다", async () => {
    renderApp(["/select"]);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "어디로 갈까요" }),
      ).toBeInTheDocument();
    });
    expect(screen.queryByRole("navigation", { name: "주 메뉴" })).toBeNull();
  });

  it("여행이 없으면 배지도 메타도 그리지 않는다", async () => {
    renderApp(["/select"]);

    const travelCard = await screen.findByRole("button", { name: /여행/ });
    // 더미 텍스트 대신 빈 자리. 설명 줄만 남는다.
    expect(travelCard).toHaveTextContent("일정 보드, 지도, 알림, 환율·날씨");
    expect(travelCard).not.toHaveTextContent(/D-/);
  });

  it("다음 여행이 있으면 D-day 배지와 기간 메타를 보여준다", async () => {
    mockTravelSummary({
      ongoing: null,
      next: {
        id: 3,
        title: "도쿄 3박 4일",
        destinationName: "도쿄",
        startDate: "2026-10-24",
        endDate: "2026-10-27",
        dDay: 78,
        activityCount: 13,
      },
      recentCompleted: null,
    });

    renderApp(["/select"]);

    const travelCard = await screen.findByRole("button", { name: /여행/ });
    await waitFor(() => {
      expect(travelCard).toHaveTextContent("D-78");
    });
    expect(travelCard).toHaveTextContent("도쿄 3박 4일 · 10.24 – 10.27");
  });

  it("진행 중 여행이 있으면 '진행 중' 배지를 보여주고 눌렀을 때 보드로 간다", async () => {
    mockTravelSummary({
      ongoing: {
        id: 3,
        title: "도쿄 3박 4일",
        boardPath: "/travel/trips/3/board",
      },
      next: null,
      recentCompleted: null,
    });

    renderApp(["/select"]);

    const travelCard = await screen.findByRole("button", { name: /여행/ });
    await waitFor(() => {
      expect(travelCard).toHaveTextContent("진행 중");
    });

    await userEvent.click(travelCard);

    await waitFor(() => {
      expect(screen.getByRole("tab", { name: /1일차/ })).toBeInTheDocument();
    });
  });

  it("진행 중 여행이 없으면 여행 카드는 여행 홈으로 간다", async () => {
    renderApp(["/select"]);

    const travelCard = await screen.findByRole("button", { name: /여행/ });
    await userEvent.click(travelCard);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "여행" })).toBeInTheDocument();
    });
  });

  it("일상 카드는 미완료 복습 수를 배지로 보여준다", async () => {
    server.use(
      http.get(`${API_BASE}/planner/reviews/summary`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            today: "2026-05-18",
            counts: { now: 3, overdue: 0, upcoming: 0, doneToday: 0 },
            estimatedMinutes: 10,
            materials: [],
          },
        }),
      ),
    );

    renderApp(["/select"]);

    const dailyCard = await screen.findByRole("button", { name: /일상/ });
    await waitFor(() => {
      expect(dailyCard).toHaveTextContent("복습 3");
    });
  });

  it("오늘 루틴이 있으면 개수를 메타로 보여준다", async () => {
    server.use(
      http.get(`${API_BASE}/planner/calendar`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            from: "2026-05-18",
            to: "2026-05-18",
            googleConnected: true,
            partial: false,
            errors: [],
            events: [
              routineEvent("1"),
              routineEvent("2"),
              // 루틴이 아닌 일정은 세지 않는다.
              {
                id: "plain",
                title: "회의",
                allDay: false,
                start: "2026-05-18T10:00:00",
                end: null,
                location: null,
                recurring: false,
                source: "google",
                routine: null,
              },
            ],
            tasks: [],
            reviews: [],
          },
        }),
      ),
    );

    renderApp(["/select"]);

    const dailyCard = await screen.findByRole("button", { name: /일상/ });
    await waitFor(() => {
      expect(dailyCard).toHaveTextContent("오늘 루틴 2개");
    });
  });

  it("일상 카드를 누르면 홈으로 간다", async () => {
    server.use(
      http.get(`${API_BASE}/planner/reviews/today`, () =>
        HttpResponse.json({
          code: "OK",
          data: { today: "2026-05-18", reviews: [] },
        }),
      ),
    );

    renderApp(["/select"]);

    const dailyCard = await screen.findByRole("button", { name: /일상/ });
    await userEvent.click(dailyCard);

    await waitFor(() => {
      expect(screen.getByText("안녕하세요 👋")).toBeInTheDocument();
    });
  });
});

function routineEvent(id: string) {
  return {
    id,
    title: `루틴 ${id}`,
    allDay: false,
    start: "2026-05-18T08:00:00",
    end: null,
    location: null,
    recurring: true,
    source: "google",
    routine: { type: "habit", recurringEventId: id, done: false },
  };
}
