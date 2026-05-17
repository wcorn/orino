import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { AppLayout } from "./AppLayout";

const API_BASE = "https://api.orino.dev/api";

function renderLayout(initialEntries: string[] = ["/home"]) {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/home" element={<div>홈 컨텐츠</div>} />
          <Route
            path="/planner/materials"
            element={<div>자료 목록 컨텐츠</div>}
          />
          <Route
            path="/planner/reviews/today"
            element={<div>오늘 복습 컨텐츠</div>}
          />
        </Route>
        <Route path="/" element={<div>랜딩 페이지</div>} />
      </Routes>
    </Providers>,
    { initialEntries },
  );
}

function mockTodayReviews(count: number) {
  server.use(
    http.get(`${API_BASE}/planner/reviews/today`, () => {
      return HttpResponse.json({
        code: "OK",
        data: {
          today: "2026-05-18",
          reviews: Array.from({ length: count }, (_, i) => ({
            id: i + 1,
            scheduledDate: "2026-05-18",
            delayDays: 0,
            sequence: 1,
            intervalDays: 1,
            easeFactor: 2.5,
            flashcard: {
              id: 1,
              front: "Q",
              back: "A",
              material: { id: 1, title: "M", type: "BOOK" },
            },
            preview: { again: 1, hard: 6, good: 6, easy: 6 },
          })),
        },
      });
    }),
  );
}

describe("AppLayout", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
  });

  it("헤더에 로고와 로그아웃 버튼이 렌더링된다", async () => {
    mockTodayReviews(0);
    renderLayout();
    await waitFor(() => {
      expect(screen.getByText("orino")).toBeInTheDocument();
    });
    expect(
      screen.getByRole("button", { name: /로그아웃/ }),
    ).toBeInTheDocument();
  });

  it("사이드바에 홈/학습 자료/오늘 복습 메뉴가 있다", async () => {
    mockTodayReviews(0);
    renderLayout();
    await waitFor(() => {
      expect(screen.getByRole("link", { name: /홈/ })).toBeInTheDocument();
    });
    expect(screen.getByRole("link", { name: /학습 자료/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /오늘 복습/ })).toBeInTheDocument();
  });

  it("자료 목록 메뉴 클릭 시 /planner/materials로 이동한다", async () => {
    mockTodayReviews(0);
    const user = userEvent.setup();
    renderLayout();
    await waitFor(() => {
      expect(screen.getByText("홈 컨텐츠")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("link", { name: /학습 자료/ }));

    await waitFor(() => {
      expect(screen.getByText("자료 목록 컨텐츠")).toBeInTheDocument();
    });
  });

  it("미완료 복습이 3건이면 사이드바에 뱃지를 표시한다", async () => {
    mockTodayReviews(3);
    renderLayout();

    await waitFor(() => {
      expect(screen.getByLabelText("미완료 3건")).toBeInTheDocument();
    });
  });

  it("미완료 복습이 0건이면 뱃지를 표시하지 않는다", async () => {
    mockTodayReviews(0);
    renderLayout();

    await waitFor(() => {
      expect(
        screen.getByRole("link", { name: /오늘 복습/ }),
      ).toBeInTheDocument();
    });
    expect(screen.queryByLabelText(/미완료 \d+건/)).not.toBeInTheDocument();
  });

  it("모바일 햄버거 버튼이 헤더에 존재한다", async () => {
    mockTodayReviews(0);
    renderLayout();
    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /메뉴 열기/ }),
      ).toBeInTheDocument();
    });
  });

  it("로그아웃 클릭 시 토큰이 제거되고 /로 이동한다", async () => {
    mockTodayReviews(0);
    const user = userEvent.setup();
    renderLayout();

    await waitFor(() => {
      expect(screen.getByText("홈 컨텐츠")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /로그아웃/ }));

    await waitFor(() => {
      expect(useAuthStore.getState().accessToken).toBeNull();
      expect(screen.getByText("랜딩 페이지")).toBeInTheDocument();
    });
  });
});
