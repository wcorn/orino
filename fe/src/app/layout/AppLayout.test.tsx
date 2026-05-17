import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { useAuthStore } from "@/features/auth/store/authStore";
import { renderWithRouter } from "@/test/render";

import { AppLayout } from "./AppLayout";

function renderLayout(initialEntries: string[] = ["/home"]) {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/home" element={<div>홈 컨텐츠</div>} />
        </Route>
        <Route path="/" element={<div>랜딩 페이지</div>} />
      </Routes>
    </Providers>,
    { initialEntries },
  );
}

describe("AppLayout", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
  });

  it("헤더에 로고와 로그아웃 버튼이 렌더링된다", async () => {
    renderLayout();
    await waitFor(() => {
      expect(screen.getByText("orino")).toBeInTheDocument();
    });
    expect(
      screen.getByRole("button", { name: /로그아웃/ }),
    ).toBeInTheDocument();
  });

  it("사이드바에 홈 메뉴가 있다", async () => {
    renderLayout();
    await waitFor(() => {
      expect(screen.getByRole("link", { name: /홈/ })).toBeInTheDocument();
    });
  });

  it("로그아웃 클릭 시 토큰이 제거되고 /로 이동한다", async () => {
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
