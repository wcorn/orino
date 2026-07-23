import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { server } from "../../test/mocks/server";
import { refreshAccessToken } from "./refreshCoordinator";
import { useAuthStore } from "./store/authStore";

const API_BASE = "https://api.orino.dev/api";

describe("refreshAccessToken", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null });
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

    expect(a).toBe(true);
    expect(b).toBe(true);
    expect(calls).toBe(1); // 두 호출이 합쳐져 서버는 한 번만
    expect(useAuthStore.getState().accessToken).toBe("t1");
  });

  it("실패 시 false를 반환하고 토큰을 비운다", async () => {
    useAuthStore.setState({ accessToken: "old" });
    server.use(
      http.post(`${API_BASE}/auth/reissue`, () =>
        HttpResponse.json(null, { status: 401 }),
      ),
    );

    const ok = await refreshAccessToken();

    expect(ok).toBe(false);
    expect(useAuthStore.getState().accessToken).toBeNull();
  });
});
