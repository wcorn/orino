import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { HomePage } from "./HomePage";

const API_BASE = "https://api.orino.dev/api";

function renderPage() {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route path="/home" element={<HomePage />} />
        <Route
          path="/planner/reviews/today"
          element={<div>오늘 복습 페이지</div>}
        />
        <Route
          path="/planner/materials"
          element={<div>자료 목록 페이지</div>}
        />
      </Routes>
    </Providers>,
    { initialEntries: ["/home"] },
  );
}

interface Setup {
  todayReviews?: { delayDays: number }[];
  materials?: number;
}

function setup({ todayReviews = [], materials = 0 }: Setup) {
  server.use(
    http.get(`${API_BASE}/planner/reviews/today`, () => {
      return HttpResponse.json({
        code: "OK",
        data: {
          today: "2026-05-12",
          reviews: todayReviews.map((r, i) => ({
            id: i + 1,
            scheduledDate: "2026-05-12",
            delayDays: r.delayDays,
            sequence: 1,
            intervalDays: 1,
            easeFactor: 2.5,
            unit: {
              id: 1,
              title: "u",
              material: { id: 1, title: "m", type: "BOOK" },
            },
            preview: { again: 1, hard: 6, good: 6, easy: 6 },
          })),
        },
      });
    }),
    http.get(`${API_BASE}/planner/materials`, () => {
      const list = Array.from({ length: materials }, (_, i) => ({
        id: i + 1,
        title: `자료 ${i + 1}`,
        type: "BOOK",
        status: "ACTIVE",
        totalUnits: 0,
        completedUnits: 0,
        createdAt: "2026-05-01T10:00:00",
        updatedAt: "2026-05-01T10:00:00",
      }));
      return HttpResponse.json({
        code: "OK",
        data: { materials: list },
      });
    }),
  );
}

describe("HomePage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
  });

  it("복습 0건 / 자료 0개일 때 빈 상태 메시지를 표시한다", async () => {
    setup({});
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("0건 — 모두 완료했어요!")).toBeInTheDocument();
    });
    expect(screen.getByText("아직 등록된 자료가 없어요.")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /첫 자료 추가/ }),
    ).toBeInTheDocument();
  });

  it("복습 3건(밀린 1건) + 자료 5개일 때 카운트를 표시한다", async () => {
    setup({
      todayReviews: [{ delayDays: 0 }, { delayDays: 0 }, { delayDays: 2 }],
      materials: 5,
    });
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("3건")).toBeInTheDocument();
    });
    expect(screen.getByText("밀린 복습 1건 포함")).toBeInTheDocument();
    expect(screen.getByText("5개 진행 중")).toBeInTheDocument();
  });

  it("오늘 복습 카드의 '바로가기' 클릭 시 /planner/reviews/today로 이동한다", async () => {
    setup({ todayReviews: [{ delayDays: 0 }] });
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("1건")).toBeInTheDocument();
    });

    const links = screen.getAllByRole("link", { name: /바로가기/ });
    await user.click(links[0]);

    await waitFor(() => {
      expect(screen.getByText("오늘 복습 페이지")).toBeInTheDocument();
    });
  });

  it("학습 자료 카드의 '바로가기' 클릭 시 /planner/materials로 이동한다", async () => {
    setup({ materials: 2 });
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("2개 진행 중")).toBeInTheDocument();
    });

    const links = screen.getAllByRole("link", { name: /바로가기/ });
    await user.click(links[1]);

    await waitFor(() => {
      expect(screen.getByText("자료 목록 페이지")).toBeInTheDocument();
    });
  });

  it("자료 0개일 때 [첫 자료 추가] 클릭 시 다이얼로그가 열린다", async () => {
    setup({});
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /첫 자료 추가/ }),
      ).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /첫 자료 추가/ }));

    expect(
      await screen.findByRole("dialog", { name: /학습 자료 추가/ }),
    ).toBeInTheDocument();
  });
});
