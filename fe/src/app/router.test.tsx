import { screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { useAuthStore } from "../features/auth/store/authStore";
import { server } from "../test/mocks/server";
import { renderWithRouter } from "../test/render";
import { Providers } from "./providers";
import { AppRouter } from "./router";

const API_BASE = "https://api.orino.dev/api";

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
    useAuthStore.setState({ accessToken: "valid-token" });

    renderApp(["/home"]);

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /로그아웃/ }),
      ).toBeInTheDocument();
    });
  });
});
