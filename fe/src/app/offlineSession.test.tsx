import { screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

import {
  clearSession,
  hadSession,
  markSession,
} from "../features/auth/sessionMarker";
import { useAuthStore } from "../features/auth/store/authStore";
import { server } from "../test/mocks/server";
import { renderWithRouter } from "../test/render";
import { Providers, useAuth } from "./providers";

const API_BASE = "https://api.orino.dev/api";

function AuthStatus() {
  const { isAuthenticated, offlineSession, loading } = useAuth();
  if (loading) return <div>로딩 중</div>;
  return (
    <div>
      <span>{isAuthenticated ? "인증됨" : "미인증"}</span>
      {offlineSession && <span>오프라인 세션</span>}
    </div>
  );
}

function renderApp(path = "/home") {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route path="/home" element={<AuthStatus />} />
        <Route path="/login" element={<AuthStatus />} />
      </Routes>
    </Providers>,
    { initialEntries: [path] },
  );
}

/** 서버에 닿지 못하는 상황. 응답이 없는 것과 401은 다른 사건이다. */
function mockUnreachable() {
  server.use(http.post(`${API_BASE}/auth/reissue`, () => HttpResponse.error()));
}

function mockRejected() {
  server.use(
    http.post(`${API_BASE}/auth/reissue`, () =>
      HttpResponse.json(null, { status: 401 }),
    ),
  );
}

/**
 * 오프라인 새로고침(#1095).
 *
 * <p>액세스 토큰은 메모리에만 있어 새로고침하면 사라진다. 오프라인이라 재발급도 못 하는데,
 * 그걸 로그아웃으로 처리하면 <b>캐시에 일정이 다 있어도 로그인 화면만 보게 된다.</b>
 */
describe("오프라인 세션", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null });
    clearSession();
  });

  afterEach(() => {
    clearSession();
  });

  it("네트워크가 없고 로그인한 적 있으면 앱을 보여준다 — 캐시로 돈다", async () => {
    markSession();
    mockUnreachable();

    renderApp();

    expect(await screen.findByText("인증됨")).toBeInTheDocument();
    expect(screen.getByText("오프라인 세션")).toBeInTheDocument();
  });

  it("네트워크 실패로는 세션을 끝내지 않는다", async () => {
    markSession();
    mockUnreachable();

    renderApp();
    await screen.findByText("인증됨");

    expect(hadSession()).toBe(true);
  });

  it("로그인한 적 없으면 그냥 미인증이다 — 아무나 들여보내지 않는다", async () => {
    mockUnreachable();

    renderApp();

    expect(await screen.findByText("미인증")).toBeInTheDocument();
    expect(screen.queryByText("오프라인 세션")).toBeNull();
  });

  it("서버가 거절하면 로그아웃이다 — 만료된 세션까지 살려두지 않는다", async () => {
    markSession();
    mockRejected();

    renderApp();

    expect(await screen.findByText("미인증")).toBeInTheDocument();
    expect(hadSession()).toBe(false);
  });

  it("네트워크가 돌아오면 다시 물어본다 — 오프라인 세션은 임시 상태다", async () => {
    markSession();
    mockUnreachable();

    renderApp();
    await screen.findByText("오프라인 세션");

    // 온라인이 되고, 이번엔 서버가 거절한다(리프레시 토큰이 실제로 만료됐던 경우).
    mockRejected();
    window.dispatchEvent(new Event("online"));

    await waitFor(() => expect(screen.getByText("미인증")).toBeInTheDocument());
    expect(hadSession()).toBe(false);
  });

  it("화면으로 돌아올 때도 다시 물어본다 — online 이벤트를 못 받을 수 있다", async () => {
    markSession();
    mockUnreachable();

    renderApp();
    await screen.findByText("오프라인 세션");

    // 앱을 접어 뒀다 여는 사이에 네트워크가 돌아온 경우. online 이벤트는 안 온다.
    mockRejected();
    document.dispatchEvent(new Event("visibilitychange"));

    await waitFor(() => expect(screen.getByText("미인증")).toBeInTheDocument());
  });

  it("돌아온 뒤 재발급에 성공하면 정상 세션이 된다", async () => {
    markSession();
    mockUnreachable();

    renderApp();
    await screen.findByText("오프라인 세션");

    server.use(
      http.post(`${API_BASE}/auth/reissue`, () =>
        HttpResponse.json({ code: "OK", data: { accessToken: "fresh" } }),
      ),
    );
    window.dispatchEvent(new Event("online"));

    await waitFor(() => expect(screen.queryByText("오프라인 세션")).toBeNull());
    expect(screen.getByText("인증됨")).toBeInTheDocument();
    expect(useAuthStore.getState().accessToken).toBe("fresh");
  });
});
