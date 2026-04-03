import { describe, expect, it, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Route, Routes } from "react-router-dom";
import { LoginPage } from "./LoginPage";
import { useAuthStore } from "../features/auth/store/authStore";
import { renderWithRouter } from "../test/render";

function renderLoginPage() {
  return renderWithRouter(
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/home" element={<div>홈 페이지</div>} />
    </Routes>,
    { initialEntries: ["/login"] }
  );
}

describe("LoginPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null });
  });

  it("아이디, 비밀번호 입력과 로그인 버튼이 렌더링된다", () => {
    renderLoginPage();

    expect(screen.getByLabelText("아이디")).toBeInTheDocument();
    expect(screen.getByLabelText("비밀번호")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "로그인" })).toBeInTheDocument();
  });

  it("로그인 성공 시 /home으로 이동한다", async () => {
    const user = userEvent.setup();
    renderLoginPage();

    await user.type(screen.getByLabelText("아이디"), "admin");
    await user.type(screen.getByLabelText("비밀번호"), "password");
    await user.click(screen.getByRole("button", { name: "로그인" }));

    await waitFor(() => {
      expect(screen.getByText("홈 페이지")).toBeInTheDocument();
    });
  });

  it("로그인 실패 시 에러 메시지를 표시한다", async () => {
    const user = userEvent.setup();
    renderLoginPage();

    await user.type(screen.getByLabelText("아이디"), "wrong");
    await user.type(screen.getByLabelText("비밀번호"), "wrong");
    await user.click(screen.getByRole("button", { name: "로그인" }));

    await waitFor(() => {
      expect(
        screen.getByText("아이디 또는 비밀번호가 올바르지 않습니다.")
      ).toBeInTheDocument();
    });
  });

  it("Enter 키로 폼을 제출할 수 있다", async () => {
    const user = userEvent.setup();
    renderLoginPage();

    await user.type(screen.getByLabelText("아이디"), "admin");
    await user.type(screen.getByLabelText("비밀번호"), "password{Enter}");

    await waitFor(() => {
      expect(screen.getByText("홈 페이지")).toBeInTheDocument();
    });
  });
});
