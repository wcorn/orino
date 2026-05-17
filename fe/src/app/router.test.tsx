import { screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { useAuthStore } from "../features/auth/store/authStore";
import { server } from "../test/mocks/server";
import { renderWithRouter } from "../test/render";
import { Providers } from "./providers";
import { AppRouter } from "./router";

const API_BASE = "https://api.orino.dev/api";

function mockEmptyTodayReviews() {
  server.use(
    http.get(`${API_BASE}/planner/reviews/today`, () => {
      return HttpResponse.json({
        code: "OK",
        data: { today: "2026-05-18", reviews: [] },
      });
    }),
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

describe("AppRouter", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null });
  });

  it("/ 경로에서 랜딩 페이지를 렌더링한다", async () => {
    renderApp(["/"]);

    await waitFor(() => {
      expect(screen.getByText("orino")).toBeInTheDocument();
    });
  });

  it("/login 경로에서 미인증 시 로그인 페이지를 렌더링한다", async () => {
    renderApp(["/login"]);

    await waitFor(() => {
      expect(screen.getByLabelText("아이디")).toBeInTheDocument();
    });
  });

  it("/login 경로에서 인증 시 /home으로 리다이렉트한다", async () => {
    mockEmptyTodayReviews();
    useAuthStore.setState({ accessToken: "valid-token" });

    renderApp(["/login"]);

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /로그아웃/ }),
      ).toBeInTheDocument();
    });
  });

  it("/home 경로에서 미인증 시 /login으로 리다이렉트한다", async () => {
    server.use(
      http.post(`${API_BASE}/auth/reissue`, () => {
        return HttpResponse.json(null, { status: 401 });
      }),
    );

    renderApp(["/home"]);

    await waitFor(() => {
      expect(screen.getByLabelText("아이디")).toBeInTheDocument();
    });
  });

  it("/home 경로에서 인증 시 홈 페이지를 렌더링한다", async () => {
    mockEmptyTodayReviews();
    useAuthStore.setState({ accessToken: "valid-token" });

    renderApp(["/home"]);

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /로그아웃/ }),
      ).toBeInTheDocument();
    });
    expect(screen.getByText("안녕하세요 👋")).toBeInTheDocument();
  });

  it("/planner/materials 경로에서 자료 목록 stub을 렌더링한다", async () => {
    mockEmptyTodayReviews();
    useAuthStore.setState({ accessToken: "valid-token" });

    renderApp(["/planner/materials"]);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "학습 자료" }),
      ).toBeInTheDocument();
    });
  });

  it("/planner/materials/:id 경로에서 자료 상세 stub을 렌더링한다", async () => {
    mockEmptyTodayReviews();
    useAuthStore.setState({ accessToken: "valid-token" });

    renderApp(["/planner/materials/42"]);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "자료 상세" }),
      ).toBeInTheDocument();
    });
  });

  it("/planner/reviews/today 경로에서 오늘 복습 stub을 렌더링한다", async () => {
    mockEmptyTodayReviews();
    useAuthStore.setState({ accessToken: "valid-token" });

    renderApp(["/planner/reviews/today"]);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "오늘 복습" }),
      ).toBeInTheDocument();
    });
  });

  it("정의되지 않은 경로는 /로 리다이렉트된다", async () => {
    renderApp(["/unknown-path"]);

    await waitFor(() => {
      expect(screen.getByText("orino")).toBeInTheDocument();
    });
  });
});
