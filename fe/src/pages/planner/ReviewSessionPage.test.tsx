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

import { ReviewSessionPage } from "./ReviewSessionPage";

const API_BASE = "https://api.orino.dev/api";

function renderPage(entry = "/planner/reviews/session") {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route
          path="/planner/reviews/session"
          element={
            <>
              <ReviewSessionPage />
              <Toaster />
            </>
          }
        />
        <Route path="/home" element={<div>홈 페이지</div>} />
        <Route path="/planner/materials" element={<div>목록 페이지</div>} />
      </Routes>
    </Providers>,
    { initialEntries: [entry] },
  );
}

interface ReviewSpec {
  id: number;
  delayDays?: number;
  materialId?: number;
}

function makeReview({ id, delayDays = 0, materialId = 1 }: ReviewSpec) {
  return {
    id,
    scheduledAt: "2026-05-18T04:00:00",
    delayDays,
    sequence: 2,
    intervalDays: 6,
    easeFactor: 2.5,
    flashcard: {
      id: id * 10,
      type: "BASIC",
      front: `질문 ${id}`,
      back: `답 ${id}`,
      items: null,
      siblingGroupId: null,
      material: { id: materialId, title: `자료 ${materialId}`, type: "BOOK" },
    },
    preview: { again: 1, hard: 6, good: 6, easy: 15 },
  };
}

function mockToday(specs: ReviewSpec[]) {
  server.use(
    http.get(`${API_BASE}/planner/reviews/today`, () =>
      HttpResponse.json({
        code: "OK",
        data: { today: "2026-05-18", reviews: specs.map(makeReview) },
      }),
    ),
  );
}

function mockComplete(
  captureRatings: Array<{ id: number; rating: string }>,
  buriedReviewIds: number[] = [],
) {
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
            buriedReviewIds,
          },
        });
      },
    ),
  );
}

describe("ReviewSessionPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
    useToastStore.setState({ toasts: [] });
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
    mockToday([{ id: 1 }, { id: 2 }]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("질문 1")).toBeInTheDocument();
    });
    expect(screen.getByText("1 / 2")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /답 보기/ })).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Again/ }),
    ).not.toBeInTheDocument();
  });

  it("Space로 답이 공개되고 1/2/3/4 키로 평가가 전송된다", async () => {
    mockToday([{ id: 1 }]);
    const ratings: Array<{ id: number; rating: string }> = [];
    mockComplete(ratings);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("질문 1")).toBeInTheDocument();
    });

    await user.keyboard(" ");
    expect(await screen.findByText("답 1")).toBeInTheDocument();

    await user.keyboard("3");
    await waitFor(() => {
      expect(ratings).toEqual([{ id: 1, rating: "GOOD" }]);
    });
  });

  it("평가 후 다음 카드로 넘어가고, 마지막 후 완료 화면이 보인다", async () => {
    mockToday([{ id: 1 }, { id: 2 }]);
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

    await user.keyboard(" ");
    await user.click(await screen.findByRole("button", { name: /Good/ }));

    await waitFor(() => {
      expect(screen.getByText(/모두 완료/)).toBeInTheDocument();
    });
  });

  it("scope=overdue면 밀린 카드(delayDays>0)만 큐에 담긴다", async () => {
    mockToday([
      { id: 1, delayDays: 0 },
      { id: 2, delayDays: 3 },
    ]);
    renderPage("/planner/reviews/session?scope=overdue");

    await waitFor(() => {
      expect(screen.getByText("질문 2")).toBeInTheDocument();
    });
    expect(screen.getByText("1 / 1")).toBeInTheDocument();
    expect(screen.queryByText("질문 1")).not.toBeInTheDocument();
  });

  it("materialId로 그 자료 카드만 큐에 담긴다", async () => {
    mockToday([
      { id: 1, materialId: 1 },
      { id: 2, materialId: 2 },
    ]);
    renderPage("/planner/reviews/session?materialId=2");

    await waitFor(() => {
      expect(screen.getByText("질문 2")).toBeInTheDocument();
    });
    expect(screen.getByText("1 / 1")).toBeInTheDocument();
    expect(screen.queryByText("질문 1")).not.toBeInTheDocument();
  });

  it("필터 결과가 0건이면 빈 상태를 보여준다", async () => {
    mockToday([{ id: 1, delayDays: 0 }]);
    renderPage("/planner/reviews/session?scope=overdue");

    await waitFor(() => {
      expect(
        screen.getByText("오늘은 복습할 카드가 없어요! 🌱"),
      ).toBeInTheDocument();
    });
  });

  it("짝 카드가 밀리면 큐에서 제거되고 카운터 분모가 감소한다", async () => {
    mockToday([{ id: 1 }, { id: 2 }, { id: 3 }]);
    mockComplete([], [2]);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("질문 1")).toBeInTheDocument();
    });
    expect(screen.getByText("1 / 3")).toBeInTheDocument();

    await user.keyboard(" ");
    await user.click(await screen.findByRole("button", { name: /Good/ }));

    await waitFor(() => {
      expect(screen.getByText("질문 3")).toBeInTheDocument();
    });
    expect(screen.getByText("2 / 2")).toBeInTheDocument();
    expect(screen.queryByText("질문 2")).not.toBeInTheDocument();
    expect(
      await screen.findByText(/짝 카드는 다른 날 복습해요/),
    ).toBeInTheDocument();
  });
});
