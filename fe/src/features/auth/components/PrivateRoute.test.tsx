import { describe, expect, it, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { Route, Routes } from "react-router-dom";
import { http, HttpResponse } from "msw";
import { PrivateRoute } from "./PrivateRoute";
import { useAuthStore } from "../store/authStore";
import { server } from "../../../test/mocks/server";
import { renderWithRouter } from "../../../test/render";
import { Providers } from "../../../app/providers";

const API_BASE = "https://api.orino.dev/api";

function renderWithPrivateRoute() {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route element={<PrivateRoute />}>
          <Route path="/home" element={<div>보호된 페이지</div>} />
        </Route>
        <Route path="/login" element={<div>로그인 페이지</div>} />
      </Routes>
    </Providers>,
    { initialEntries: ["/home"] }
  );
}

describe("PrivateRoute", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null });
  });

  it("미인증 시 /login으로 리다이렉트한다", async () => {
    server.use(
      http.post(`${API_BASE}/auth/reissue`, () => {
        return HttpResponse.json(null, { status: 401 });
      })
    );

    renderWithPrivateRoute();

    await waitFor(() => {
      expect(screen.getByText("로그인 페이지")).toBeInTheDocument();
    });
  });

  it("인증 시 children을 렌더링한다", async () => {
    server.use(
      http.post(`${API_BASE}/auth/reissue`, () => {
        return HttpResponse.json({
          code: "OK",
          data: { accessToken: "mock-access-token" },
        });
      })
    );

    renderWithPrivateRoute();

    await waitFor(() => {
      expect(screen.getByText("보호된 페이지")).toBeInTheDocument();
    });
  });
});
