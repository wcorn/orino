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

  it("/login 경로에서 인증 시 /select로 리다이렉트한다", async () => {
    mockEmptyTodayReviews();
    useAuthStore.setState({ accessToken: "valid-token" });

    renderApp(["/login"]);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "어디로 갈까요" }),
      ).toBeInTheDocument();
    });
  });

  it("/ 경로에서 인증 시 /select로 리다이렉트한다", async () => {
    mockEmptyTodayReviews();
    useAuthStore.setState({ accessToken: "valid-token" });

    renderApp(["/"]);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "어디로 갈까요" }),
      ).toBeInTheDocument();
    });
  });

  it("/travel 딥링크는 선택 화면으로 되돌리지 않는다 (푸시 알림 진입점)", async () => {
    mockEmptyTodayReviews();
    useAuthStore.setState({ accessToken: "valid-token" });

    renderApp(["/travel/trips/3/board"]);

    // 보드가 실제로 열린다(선택 화면으로 튕기지 않는다).
    await waitFor(() => {
      expect(screen.getByRole("tab", { name: /10\.24/ })).toBeInTheDocument();
    });
    expect(screen.queryByRole("heading", { name: "어디로 갈까요" })).toBeNull();
  });

  it("/select 는 미인증 시 로그인으로 보낸다", async () => {
    server.use(
      http.post(`${API_BASE}/auth/reissue`, () => {
        return HttpResponse.json(null, { status: 401 });
      }),
    );

    renderApp(["/select"]);

    await waitFor(() => {
      expect(screen.getByLabelText("아이디")).toBeInTheDocument();
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

    // 헤더는 lazy 페이지보다 먼저 뜨므로, 페이지 본문이 나타날 때까지 기다린다.
    expect(await screen.findByText("안녕하세요 👋")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /로그아웃/ }),
    ).toBeInTheDocument();
  });

  it("/planner/materials 경로에서 자료 목록을 렌더링한다", async () => {
    mockEmptyTodayReviews();
    server.use(
      http.get(`${API_BASE}/planner/materials`, () => {
        return HttpResponse.json({
          code: "OK",
          data: { materials: [] },
        });
      }),
    );
    useAuthStore.setState({ accessToken: "valid-token" });

    renderApp(["/planner/materials"]);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "학습 자료" }),
      ).toBeInTheDocument();
    });
  });

  it("/planner/materials/:id 경로에서 자료 상세를 렌더링한다", async () => {
    mockEmptyTodayReviews();
    server.use(
      http.get(`${API_BASE}/planner/materials/42`, () => {
        return HttpResponse.json({
          code: "OK",
          data: {
            id: 42,
            title: "테스트 자료",
            type: "BOOK",
            status: "ACTIVE",
            flashcardCount: 0,
            dueReviewCount: 0,
            createdAt: "2026-05-18T00:00:00",
            updatedAt: "2026-05-18T00:00:00",
          },
        });
      }),
      http.get(`${API_BASE}/planner/materials/42/note`, () => {
        return HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            materialId: 42,
            content: { type: "doc", content: [] },
            updatedAt: "2026-05-18T00:00:00",
          },
        });
      }),
    );
    useAuthStore.setState({ accessToken: "valid-token" });

    renderApp(["/planner/materials/42"]);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "테스트 자료" }),
      ).toBeInTheDocument();
    });
  });

  it("/planner/reviews 경로에서 복습 허브를 렌더링한다", async () => {
    server.use(
      http.get(`${API_BASE}/planner/reviews/upcoming`, () =>
        HttpResponse.json({
          code: "OK",
          data: { today: "2026-05-18", items: [], hasNext: false },
        }),
      ),
    );
    useAuthStore.setState({ accessToken: "valid-token" });

    renderApp(["/planner/reviews"]);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "복습", level: 1 }),
      ).toBeInTheDocument();
    });
  });

  it("/planner/reviews/today 는 복습 허브로 리다이렉트된다", async () => {
    server.use(
      http.get(`${API_BASE}/planner/reviews/upcoming`, () =>
        HttpResponse.json({
          code: "OK",
          data: { today: "2026-05-18", items: [], hasNext: false },
        }),
      ),
    );
    useAuthStore.setState({ accessToken: "valid-token" });

    renderApp(["/planner/reviews/today"]);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "복습", level: 1 }),
      ).toBeInTheDocument();
    });
  });

  it("/planner/calendar 경로에서 통합 캘린더(일정+복습)를 렌더링한다", async () => {
    mockEmptyTodayReviews();
    server.use(
      http.get(`${API_BASE}/planner/calendar`, () => {
        return HttpResponse.json({
          code: "OK",
          data: {
            from: "2026-06-01",
            to: "2026-06-30",
            googleConnected: false,
            partial: false,
            errors: [],
            events: [],
            tasks: [],
            reviews: [],
          },
        });
      }),
    );
    useAuthStore.setState({ accessToken: "valid-token" });

    renderApp(["/planner/calendar"]);

    // "+할 일" 버튼은 통합 뷰에만 있다(기존 복습 전용 캘린더와 구분).
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "할 일" })).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: "오늘" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "캘린더" })).toBeInTheDocument();
  });

  it("정의되지 않은 경로는 /로 리다이렉트된다", async () => {
    renderApp(["/unknown-path"]);

    await waitFor(() => {
      expect(screen.getByText("orino")).toBeInTheDocument();
    });
  });
});
