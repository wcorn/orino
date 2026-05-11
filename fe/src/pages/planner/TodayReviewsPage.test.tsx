import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { Toaster } from "@/components/Toaster";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { TodayReviewsPage } from "./TodayReviewsPage";

const API_BASE = "https://api.orino.dev/api";

function reviewFixture(overrides: Partial<{ id: number; sequence: number; delayDays: number; unitTitle: string }> = {}) {
  return {
    id: overrides.id ?? 1,
    scheduledDate: "2026-05-12",
    delayDays: overrides.delayDays ?? 0,
    sequence: overrides.sequence ?? 1,
    intervalDays: 1,
    easeFactor: 2.5,
    unit: {
      id: 1,
      title: overrides.unitTitle ?? "아이템 1",
      material: { id: 1, title: "이펙티브 자바", type: "BOOK" as const },
    },
    preview: { again: 1, hard: 6, good: 6, easy: 6 },
  };
}

function setupTodayReviewsApi(initial: ReturnType<typeof reviewFixture>[]) {
  let reviews = [...initial];
  let lastRating: string | null = null;

  server.use(
    http.get(`${API_BASE}/planner/reviews/today`, () => {
      return HttpResponse.json({
        code: "OK",
        data: { today: "2026-05-12", reviews },
      });
    }),
    http.post(
      `${API_BASE}/planner/reviews/:id/complete`,
      async ({ params, request }) => {
        const body = (await request.json()) as { rating: string };
        lastRating = body.rating;
        const id = Number(params.id);
        reviews = reviews.filter((r) => r.id !== id);
        return HttpResponse.json({
          code: "OK",
          data: {
            completed: {
              id,
              studyUnitId: 1,
              sequence: 1,
              scheduledDate: "2026-05-12",
              intervalDays: 1,
              easeFactor: 2.5,
              status: "COMPLETED",
              completedAt: "2026-05-12T10:00:00",
            },
            nextReview: {
              id: 999,
              studyUnitId: 1,
              sequence: 2,
              scheduledDate: "2026-05-18",
              intervalDays: 6,
              easeFactor: 2.5,
              status: "PENDING",
              completedAt: null,
            },
          },
        });
      },
    ),
  );

  return { getLastRating: () => lastRating };
}

function renderPage() {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route path="/" element={<TodayReviewsPage />} />
      </Routes>
      <Toaster />
    </Providers>,
  );
}

describe("TodayReviewsPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
  });

  it("복습이 없으면 빈 상태 메시지를 표시한다", async () => {
    setupTodayReviewsApi([]);
    renderPage();

    await waitFor(() => {
      expect(
        screen.getByText("오늘 처리할 복습이 없어요."),
      ).toBeInTheDocument();
    });
  });

  it("복습 카드와 'N건 (밀린 M건)'을 표시한다", async () => {
    setupTodayReviewsApi([
      reviewFixture({ id: 1, delayDays: 2, unitTitle: "밀린 단위" }),
      reviewFixture({ id: 2, delayDays: 0, unitTitle: "오늘 단위" }),
    ]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("밀린 단위")).toBeInTheDocument();
    });
    expect(screen.getByText("오늘 단위")).toBeInTheDocument();
    expect(screen.getByText(/2건/)).toBeInTheDocument();
    expect(screen.getByText(/밀린 1건/)).toBeInTheDocument();
    expect(screen.getByText("2일 지연")).toBeInTheDocument();
  });

  it("Good 클릭 시 GOOD 평가가 전송되고 다음 복습 토스트가 뜬다", async () => {
    const api = setupTodayReviewsApi([reviewFixture({ id: 1 })]);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("아이템 1")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /Good/ }));

    await waitFor(() => {
      expect(api.getLastRating()).toBe("GOOD");
    });
    await waitFor(() => {
      expect(
        screen.getByText(/다음 복습: 6일 후 \(2026-05-18\)/),
      ).toBeInTheDocument();
    });
  });

  it("4가지 평가가 모두 정상적으로 전송된다", async () => {
    const cases: Array<{ label: RegExp; rating: string }> = [
      { label: /Again/, rating: "AGAIN" },
      { label: /Hard/, rating: "HARD" },
      { label: /Good/, rating: "GOOD" },
      { label: /Easy/, rating: "EASY" },
    ];
    for (const c of cases) {
      const api = setupTodayReviewsApi([reviewFixture({ id: 1 })]);
      const user = userEvent.setup();
      const { unmount } = renderPage();

      await waitFor(() => {
        expect(screen.getByText("아이템 1")).toBeInTheDocument();
      });
      await user.click(screen.getByRole("button", { name: c.label }));

      await waitFor(() => {
        expect(api.getLastRating()).toBe(c.rating);
      });
      unmount();
    }
  });

  it("키보드 단축키 1/2/3/4로 평가할 수 있다", async () => {
    const api = setupTodayReviewsApi([reviewFixture({ id: 1 })]);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("아이템 1")).toBeInTheDocument();
    });

    await user.keyboard("3");

    await waitFor(() => {
      expect(api.getLastRating()).toBe("GOOD");
    });
  });
});
