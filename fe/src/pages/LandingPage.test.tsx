import { screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "../app/providers";
import { useAuthStore } from "../features/auth/store/authStore";
import { server } from "../test/mocks/server";
import { renderWithRouter } from "../test/render";
import { LandingPage } from "./LandingPage";

const API_BASE = "https://api.orino.dev/api";

function renderLandingPage() {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/home" element={<div>홈 페이지</div>} />
        <Route path="/login" element={<div>로그인 페이지</div>} />
      </Routes>
    </Providers>,
    { initialEntries: ["/"] },
  );
}

describe("LandingPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null });
  });

  it("미인증 시 랜딩 페이지를 렌더링한다", async () => {
    renderLandingPage();

    await waitFor(() => {
      expect(screen.getByText("orino")).toBeInTheDocument();
      expect(screen.getByText("시작하기")).toBeInTheDocument();
    });
  });

  it("인증 상태면 /home으로 리다이렉트한다", async () => {
    useAuthStore.setState({ accessToken: "valid-token" });

    renderLandingPage();

    await waitFor(() => {
      expect(screen.getByText("홈 페이지")).toBeInTheDocument();
    });
  });

  it("reissue 성공으로 인증되면 /home으로 리다이렉트한다", async () => {
    server.use(
      http.post(`${API_BASE}/auth/reissue`, () => {
        return HttpResponse.json({
          code: "OK",
          data: { accessToken: "refreshed-token" },
        });
      }),
    );

    renderLandingPage();

    await waitFor(() => {
      expect(screen.getByText("orino")).toBeInTheDocument();
    });
  });
});
