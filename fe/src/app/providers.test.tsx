import { screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import { useAuthStore } from "../features/auth/store/authStore";
import { server } from "../test/mocks/server";
import { renderWithRouter } from "../test/render";
import { Providers, useAuth } from "./providers";

const API_BASE = "https://api.orino.dev/api";

function AuthStatus() {
  const { isAuthenticated, loading } = useAuth();
  if (loading) return <div>로딩 중</div>;
  return <div>{isAuthenticated ? "인증됨" : "미인증"}</div>;
}

function renderWithProviders(initialEntries: string[] = ["/"]) {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route path="/" element={<AuthStatus />} />
        <Route path="/login" element={<AuthStatus />} />
        <Route path="/home" element={<AuthStatus />} />
      </Routes>
    </Providers>,
    { initialEntries },
  );
}

describe("Providers", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null });
  });

  it("accessToken이 있으면 인증 상태를 반환한다", async () => {
    useAuthStore.setState({ accessToken: "valid-token" });

    renderWithProviders();

    await waitFor(() => {
      expect(screen.getByText("인증됨")).toBeInTheDocument();
    });
  });

  it("PUBLIC_ROUTES에서는 reissue를 호출하지 않고 미인증 상태를 반환한다", async () => {
    renderWithProviders(["/"]);

    await waitFor(() => {
      expect(screen.getByText("미인증")).toBeInTheDocument();
    });
  });

  it("보호된 경로에서 토큰이 없으면 reissue를 시도한다", async () => {
    server.use(
      http.post(`${API_BASE}/auth/reissue`, () => {
        return HttpResponse.json({
          code: "OK",
          data: { accessToken: "refreshed-token" },
        });
      }),
    );

    renderWithProviders(["/home"]);

    await waitFor(() => {
      expect(screen.getByText("인증됨")).toBeInTheDocument();
    });
  });

  it("보호된 경로에서 reissue 실패 시 미인증 상태를 반환한다", async () => {
    server.use(
      http.post(`${API_BASE}/auth/reissue`, () => {
        return HttpResponse.json(null, { status: 401 });
      }),
    );

    renderWithProviders(["/home"]);

    await waitFor(() => {
      expect(screen.getByText("미인증")).toBeInTheDocument();
    });
  });
});
