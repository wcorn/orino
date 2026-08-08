import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { server } from "../../test/mocks/server";
import { refreshAccessToken } from "./refreshCoordinator";
import { clearSession, hadSession, markSession } from "./sessionMarker";
import { useAuthStore } from "./store/authStore";

const API_BASE = "https://api.orino.dev/api";

describe("refreshAccessToken", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null });
    clearSession();
  });

  it("동시 호출을 하나의 재발급 요청으로 합친다(단일 비행)", async () => {
    let calls = 0;
    server.use(
      http.post(`${API_BASE}/auth/reissue`, () => {
        calls++;
        return HttpResponse.json({ code: "OK", data: { accessToken: "t1" } });
      }),
    );

    const [a, b] = await Promise.all([
      refreshAccessToken(),
      refreshAccessToken(),
    ]);

    expect(a).toBe("ok");
    expect(b).toBe("ok");
    expect(calls).toBe(1); // 두 호출이 합쳐져 서버는 한 번만
    expect(useAuthStore.getState().accessToken).toBe("t1");
  });

  it("서버가 거절하면 unauthorized를 반환하고 토큰을 비운다", async () => {
    useAuthStore.setState({ accessToken: "old" });
    server.use(
      http.post(`${API_BASE}/auth/reissue`, () =>
        HttpResponse.json(null, { status: 401 }),
      ),
    );

    const ok = await refreshAccessToken();

    expect(ok).toBe("unauthorized");
    expect(useAuthStore.getState().accessToken).toBeNull();
    // 서버가 판단을 내렸으니 이 기기의 세션도 끝난다.
    expect(hadSession()).toBe(false);
  });

  it("서버에 닿지 못하면 offline이다 — 세션을 끝내지 않는다", async () => {
    markSession();
    server.use(
      http.post(`${API_BASE}/auth/reissue`, () => HttpResponse.error()),
    );

    const result = await refreshAccessToken();

    expect(result).toBe("offline");
    // 네트워크가 없는 것은 로그아웃이 아니다 — 이걸 섞으면 비행기 모드에서
    // 새로고침만 해도 로그아웃된다(#1095).
    expect(hadSession()).toBe(true);
  });

  it("재발급에 성공하면 세션 표시를 남긴다", async () => {
    server.use(
      http.post(`${API_BASE}/auth/reissue`, () =>
        HttpResponse.json({ code: "OK", data: { accessToken: "t" } }),
      ),
    );

    await refreshAccessToken();

    expect(hadSession()).toBe(true);
  });
});
