import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { Providers } from "@/app/providers";
import { Toaster } from "@/components/Toaster";
import { useAuthStore } from "@/features/auth/store/authStore";
import { useToastStore } from "@/shared/lib/toast";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { TodayReviewsPage } from "./TodayReviewsPage";

const API_BASE = "https://api.orino.dev/api";

function renderPage() {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route
          path="/planner/reviews/today"
          element={
            <>
              <TodayReviewsPage />
              <Toaster />
            </>
          }
        />
        <Route path="/home" element={<div>홈 페이지</div>} />
        <Route path="/planner/materials" element={<div>목록 페이지</div>} />
      </Routes>
    </Providers>,
    { initialEntries: ["/planner/reviews/today"] },
  );
}

function mockToday(reviewIds: number[], opts?: { delayDays?: number }) {
  server.use(
    http.get(`${API_BASE}/planner/reviews/today`, () => {
      return HttpResponse.json({
        code: "OK",
        data: {
          today: "2026-05-18",
          reviews: reviewIds.map((id) => ({
            id,
            scheduledAt: "2026-05-18T04:00:00",
            delayDays: opts?.delayDays ?? 0,
            sequence: 2,
            intervalDays: 6,
            easeFactor: 2.5,
            flashcard: {
              id: id * 10,
              front: `질문 ${id}`,
              back: `답 ${id}`,
              material: { id: 1, title: "테스트 자료", type: "BOOK" },
            },
            preview: { again: 1, hard: 6, good: 6, easy: 15 },
          })),
        },
      });
    }),
  );
}

function mockComplete(captureRatings: Array<{ id: number; rating: string }>) {
  server.use(
    http.post(
      `${API_BASE}/planner/reviews/:id/complete`,
      async ({ params, request }) => {
        const body = (await request.json()) as { rating: string };
        captureRatings.push({ id: Number(params.id), rating: body.rating });
        return HttpResponse.json({
          code: "OK",
          data: {
            completed: {
              id: Number(params.id),
              status: "COMPLETED",
              rating: body.rating,
              elapsedDays: 0,
              completedAt: "2026-05-18T10:00:00",
            },
            nextReview: {
              id: 999,
              flashcardId: 1,
              sequence: 3,
              scheduledAt: "2026-05-24T04:00:00",
              intervalDays: 6,
              easeFactor: 2.5,
              status: "PENDING",
            },
          },
        });
      },
    ),
  );
}

describe("TodayReviewsPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
    useToastStore.setState({ toasts: [] });
    // 오늘을 2026-05-18로 고정 (다음 복습 토스트 메시지가 now 기준이므로).
    // Date만 fake하여 react-query/userEvent의 실제 setTimeout과 충돌하지 않게 한다.
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 4, 18, 9, 0, 0));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("0건이면 빈 상태와 [홈으로] 버튼을 표시한다", async () => {
    mockToday([]);
    renderPage();

    await waitFor(() => {
      expect(
        screen.getByText("오늘은 복습할 카드가 없어요! 🌱"),
      ).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: "홈으로" })).toBeInTheDocument();
  });

  it("진입 시 첫 카드 앞면 + [답 보기] 버튼만 보인다", async () => {
    mockToday([1, 2]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("질문 1")).toBeInTheDocument();
    });
    expect(screen.getByText("1 / 2")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /답 보기/ })).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Again/ }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText("답 1")).not.toBeInTheDocument();
  });

  it("Space로 답이 공개되고 1/2/3/4 키로 평가가 전송된다", async () => {
    mockToday([1]);
    const ratings: Array<{ id: number; rating: string }> = [];
    mockComplete(ratings);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("질문 1")).toBeInTheDocument();
    });

    await user.keyboard(" ");
    expect(await screen.findByText("답 1")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Good/ })).toBeInTheDocument();

    await user.keyboard("3");

    await waitFor(() => {
      expect(ratings).toEqual([{ id: 1, rating: "GOOD" }]);
    });
  });

  it("평가 후 다음 카드로 넘어가고, 마지막 후 완료 화면이 보인다", async () => {
    mockToday([1, 2]);
    mockComplete([]);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("질문 1")).toBeInTheDocument();
    });

    await user.keyboard(" ");
    await user.click(await screen.findByRole("button", { name: /Good/ }));

    await waitFor(() => {
      expect(screen.getByText("질문 2")).toBeInTheDocument();
    });
    expect(screen.getByText("2 / 2")).toBeInTheDocument();

    await user.keyboard(" ");
    await user.click(await screen.findByRole("button", { name: /Good/ }));

    await waitFor(() => {
      expect(screen.getByText(/모두 완료/)).toBeInTheDocument();
    });
  });

  it("평가 성공 시 다음 복습 토스트가 표시된다", async () => {
    mockToday([1]);
    mockComplete([]);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("질문 1")).toBeInTheDocument();
    });

    await user.keyboard(" ");
    await user.click(await screen.findByRole("button", { name: /Good/ }));

    expect(
      await screen.findByText(/다음 복습은 .* \(5\/24\)/),
    ).toBeInTheDocument();
  });

  it("delayDays > 0이면 헤더에 밀린 일수가 표시된다", async () => {
    mockToday([1], { delayDays: 3 });
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("3일 밀린 복습")).toBeInTheDocument();
    });
  });
});
