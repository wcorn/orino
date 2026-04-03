import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import { useAuthStore } from "../features/auth/store/authStore";
import { renderWithRouter } from "../test/render";
import { MainPage } from "./MainPage";

function renderMainPage() {
  return renderWithRouter(
    <Routes>
      <Route path="/home" element={<MainPage />} />
      <Route path="/" element={<div>랜딩 페이지</div>} />
    </Routes>,
    { initialEntries: ["/home"] },
  );
}

describe("MainPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
  });

  it("로고와 로그아웃 버튼이 렌더링된다", () => {
    renderMainPage();

    expect(screen.getByText("orino")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /로그아웃/ }),
    ).toBeInTheDocument();
  });

  it("로그아웃 클릭 시 토큰이 제거되고 /로 이동한다", async () => {
    const user = userEvent.setup();
    renderMainPage();

    await user.click(screen.getByRole("button", { name: /로그아웃/ }));

    await waitFor(() => {
      expect(useAuthStore.getState().accessToken).toBeNull();
      expect(screen.getByText("랜딩 페이지")).toBeInTheDocument();
    });
  });
});
