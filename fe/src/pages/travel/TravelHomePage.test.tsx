import { screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

const API_BASE = "https://api.orino.dev/api";

function mockSummary(data: unknown) {
  server.use(
    http.get(`${API_BASE}/travel/summary`, () =>
      HttpResponse.json({ code: "OK", data }),
    ),
  );
}

function mockTrip(trip: Record<string, unknown>) {
  server.use(
    http.get(`${API_BASE}/travel/trips/:tripId`, () =>
      HttpResponse.json({ code: "OK", data: trip }),
    ),
  );
}

const TOKYO = {
  id: 3,
  title: "도쿄 3박 4일",
  destinationName: "도쿄",
  destinationPlaceId: null,
  startDate: "2026-10-24",
  endDate: "2026-10-27",
  timezone: "Asia/Tokyo",
  currency: "JPY",
  lat: null,
  lng: null,
  defaultNotifyMinutes: 15,
  morningSummaryEnabled: false,
  status: "UPCOMING",
  dDay: 78,
  totalDays: 4,
  activityCount: 13,
};

function renderApp() {
  return renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: ["/travel"] },
  );
}

describe("TravelHomePage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  it("여행이 하나도 없으면 만들기 버튼만 보여준다", async () => {
    renderApp();

    await waitFor(() => {
      expect(screen.getByText("아직 만든 여행이 없어요.")).toBeInTheDocument();
    });
    expect(screen.getByRole("link", { name: /여행 만들기/ })).toHaveAttribute(
      "href",
      "/travel/trips/new",
    );
  });

  it("다음 여행을 예정 배지·기간·일정 수와 함께 보여준다", async () => {
    mockSummary({
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
    mockTrip(TOKYO);

    renderApp();

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "도쿄 3박 4일" }),
      ).toBeInTheDocument();
    });
    expect(screen.getByText("예정")).toBeInTheDocument();
    expect(
      screen.getByText("10월 24일 – 10월 27일 · 일정 13개"),
    ).toBeInTheDocument();
    expect(screen.getByText("Asia/Tokyo · JPY")).toBeInTheDocument();
  });

  it("일자 칩을 기간만큼 만든다(1단계는 일차·요일만)", async () => {
    mockSummary({
      ongoing: null,
      next: { ...TOKYO, dDay: 78 },
      recentCompleted: null,
    });
    mockTrip(TOKYO);

    renderApp();

    await waitFor(() => {
      expect(screen.getByText("1일차 · 토")).toBeInTheDocument();
    });
    expect(screen.getByText("4일차 · 화")).toBeInTheDocument();
    // 날씨는 4단계라 이 화면엔 온도가 없다.
    expect(screen.queryByText(/°/)).toBeNull();
  });

  it("진행 중 여행이 있으면 진행 중 배지와 그 제목을 헤더에 쓴다", async () => {
    mockSummary({
      ongoing: {
        id: 3,
        title: "도쿄 3박 4일",
        boardPath: "/travel/trips/3/board",
      },
      next: null,
      recentCompleted: null,
    });
    mockTrip({ ...TOKYO, status: "ONGOING" });

    renderApp();

    await waitFor(() => {
      expect(screen.getByText("진행 중")).toBeInTheDocument();
    });
    expect(screen.getByText("도쿄 3박 4일 여행 중이에요.")).toBeInTheDocument();
  });

  it("보드 열기 링크가 그 여행의 보드를 가리킨다", async () => {
    mockSummary({
      ongoing: null,
      next: { ...TOKYO, dDay: 78 },
      recentCompleted: null,
    });
    mockTrip(TOKYO);

    renderApp();

    await waitFor(() => {
      expect(
        screen.getByRole("link", { name: /일정 보드 열기/ }),
      ).toHaveAttribute("href", "/travel/trips/3/board");
    });
  });

  it("최근 완료 여행은 별도 행으로 보여주고 그 보드로 보낸다", async () => {
    mockSummary({
      ongoing: null,
      next: null,
      recentCompleted: {
        id: 2,
        title: "오사카 2박 3일",
        endDate: "2026-05-11",
        activityCount: 9,
      },
    });

    renderApp();

    await waitFor(() => {
      expect(screen.getByText("최근 완료")).toBeInTheDocument();
    });
    const link = screen.getByRole("link", { name: /오사카 2박 3일/ });
    expect(link).toHaveAttribute("href", "/travel/trips/2/board");
    expect(link).toHaveTextContent("5월 11일 종료 · 일정 9개");
  });

  it("완료 여행만 있으면 다음 여행 카드를 그리지 않는다", async () => {
    mockSummary({
      ongoing: null,
      next: null,
      recentCompleted: {
        id: 2,
        title: "오사카 2박 3일",
        endDate: "2026-05-11",
        activityCount: 9,
      },
    });

    renderApp();

    await waitFor(() => {
      expect(screen.getByText("최근 완료")).toBeInTheDocument();
    });
    expect(screen.queryByText("예정")).toBeNull();
    expect(screen.queryByRole("link", { name: /일정 보드 열기/ })).toBeNull();
  });
});
