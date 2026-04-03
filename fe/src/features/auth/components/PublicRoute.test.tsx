import { describe, expect, it, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { Route, Routes } from "react-router-dom";
import { PublicRoute } from "./PublicRoute";
import { useAuthStore } from "../store/authStore";
import { renderWithRouter } from "../../../test/render";
import { Providers } from "../../../app/providers";

function renderWithPublicRoute() {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route element={<PublicRoute />}>
          <Route path="/login" element={<div>로그인 페이지</div>} />
        </Route>
        <Route path="/home" element={<div>홈 페이지</div>} />
      </Routes>
    </Providers>,
    { initialEntries: ["/login"] }
  );
}

describe("PublicRoute", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null });
  });

  it("미인증 시 children을 렌더링한다", async () => {
    renderWithPublicRoute();

    await waitFor(() => {
      expect(screen.getByText("로그인 페이지")).toBeInTheDocument();
    });
  });

  it("인증 시 /home으로 리다이렉트한다", async () => {
    useAuthStore.setState({ accessToken: "mock-token" });
    renderWithPublicRoute();

    await waitFor(() => {
      expect(screen.getByText("홈 페이지")).toBeInTheDocument();
    });
  });
});
