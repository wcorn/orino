import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { Providers } from "@/app/providers";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { ReviewCalendarPage } from "./ReviewCalendarPage";

const API_BASE = "https://api.orino.dev/api";

function renderPage() {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route path="/planner/calendar" element={<ReviewCalendarPage />} />
        <Route
          path="/planner/reviews/today"
          element={<div>오늘 복습 페이지</div>}
        />
      </Routes>
    </Providers>,
    { initialEntries: ["/planner/calendar"] },
  );
}

interface CalReview {
  id: number;
  scheduledDate: string;
  status: "PENDING" | "COMPLETED";
  rating?: string | null;
}

function mockCalendar(reviewsByCall: CalReview[]) {
  server.use(
    http.get(`${API_BASE}/planner/reviews/calendar`, ({ request }) => {
      const url = new URL(request.url);
      const from = url.searchParams.get("from")!;
      const to = url.searchParams.get("to")!;
      const filtered = reviewsByCall.filter(
        (r) => r.scheduledDate >= from && r.scheduledDate <= to,
      );
      return HttpResponse.json({
        code: "OK",
        data: {
          from,
          to,
          reviews: filtered.map((r) => ({
            id: r.id,
            scheduledDate: r.scheduledDate,
            status: r.status,
            rating: r.rating ?? null,
            sequence: 1,
            flashcard: {
              id: r.id,
              front: `질문 ${r.id}`,
              material: { id: 1, title: "이펙티브 자바", type: "BOOK" },
            },
          })),
        },
      });
    }),
  );
}

describe("ReviewCalendarPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
    // 오늘을 2026-05-18로 고정. Date만 fake하여 react-query/userEvent의
    // 실제 setTimeout과 충돌하지 않게 한다.
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 4, 18, 9, 0, 0));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("현재 월(2026년 5월) 헤더와 요일 헤더를 렌더링한다", async () => {
    mockCalendar([]);
    renderPage();

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "2026년 5월" }),
      ).toBeInTheDocument();
    });
    expect(screen.getByText("일")).toBeInTheDocument();
    expect(screen.getByText("토")).toBeInTheDocument();
  });

  it("복습이 없으면 빈 안내를 표시한다", async () => {
    mockCalendar([]);
    renderPage();

    await waitFor(() => {
      expect(
        screen.getByText("이 달에는 복습 일정이 없어요."),
      ).toBeInTheDocument();
    });
  });

  it("날짜 셀에 복습 건수가 aria-label로 노출된다", async () => {
    mockCalendar([
      { id: 1, scheduledDate: "2026-05-18", status: "PENDING" },
      {
        id: 2,
        scheduledDate: "2026-05-18",
        status: "COMPLETED",
        rating: "GOOD",
      },
    ]);
    renderPage();

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /2026-05-18 복습 2건/ }),
      ).toBeInTheDocument();
    });
  });

  it("날짜 클릭 시 상세 패널에 그날 복습이 상태별로 표시된다", async () => {
    mockCalendar([
      { id: 1, scheduledDate: "2026-05-15", status: "PENDING" }, // 밀림
      {
        id: 2,
        scheduledDate: "2026-05-15",
        status: "COMPLETED",
        rating: "EASY",
      },
    ]);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /2026-05-15 복습 2건/ }),
      ).toBeInTheDocument();
    });

    await user.click(
      screen.getByRole("button", { name: /2026-05-15 복습 2건/ }),
    );

    await waitFor(() => {
      expect(screen.getByText("5월 15일")).toBeInTheDocument();
    });
    expect(screen.getByText(/밀림 \(1\)/)).toBeInTheDocument();
    expect(screen.getByText(/완료 \(1\)/)).toBeInTheDocument();
    expect(screen.getByText("질문 1")).toBeInTheDocument();
  });

  it("밀림/오늘 복습이 있으면 [오늘 복습 하러가기] 노출 + 이동", async () => {
    mockCalendar([{ id: 9, scheduledDate: "2026-05-18", status: "PENDING" }]);
    const user = userEvent.setup();
    renderPage();

    // 초기 선택은 오늘(2026-05-18)
    await waitFor(() => {
      expect(screen.getByText("5월 18일")).toBeInTheDocument();
    });

    const link = await screen.findByRole("link", {
      name: "오늘 복습 하러가기",
    });
    await user.click(link);

    await waitFor(() => {
      expect(screen.getByText("오늘 복습 페이지")).toBeInTheDocument();
    });
  });

  it("다음 달 버튼 클릭 시 헤더가 6월로 바뀐다", async () => {
    mockCalendar([]);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "2026년 5월" }),
      ).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "다음 달" }));

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "2026년 6월" }),
      ).toBeInTheDocument();
    });
  });

  it("범례를 표시한다", async () => {
    mockCalendar([]);
    renderPage();

    await waitFor(() => {
      const legend = screen.getByRole("list");
      expect(within(legend).getByText("밀림")).toBeInTheDocument();
      expect(within(legend).getByText("오늘")).toBeInTheDocument();
      expect(within(legend).getByText("예정")).toBeInTheDocument();
      expect(within(legend).getByText("완료")).toBeInTheDocument();
    });
  });
});
