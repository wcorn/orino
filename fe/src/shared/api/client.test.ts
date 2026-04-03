import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { useAuthStore } from "../../features/auth/store/authStore";
import { server } from "../../test/mocks/server";
import client from "./client";

const API_BASE = "https://api.orino.dev/api";

describe("Axios interceptor", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  it("요청에 Authorization 헤더를 자동 첨부한다", async () => {
    server.use(
      http.get(`${API_BASE}/test`, ({ request }) => {
        const auth = request.headers.get("Authorization");
        return HttpResponse.json({ auth });
      }),
    );

    const { data } = await client.get("/test");
    expect(data.auth).toBe("Bearer valid-token");
  });

  it("401 응답 시 reissue로 토큰 갱신 후 원래 요청을 재시도한다", async () => {
    let callCount = 0;

    server.use(
      http.get(`${API_BASE}/protected`, () => {
        callCount++;
        if (callCount === 1) {
          return HttpResponse.json(null, { status: 401 });
        }
        return HttpResponse.json({ success: true });
      }),
      http.post(`${API_BASE}/auth/reissue`, () => {
        return HttpResponse.json({
          code: "OK",
          data: { accessToken: "new-access-token" },
        });
      }),
    );

    const { data } = await client.get("/protected");
    expect(data.success).toBe(true);
    expect(useAuthStore.getState().accessToken).toBe("new-access-token");
  });

  it("reissue 실패 시 토큰을 제거한다", async () => {
    server.use(
      http.get(`${API_BASE}/protected`, () => {
        return HttpResponse.json(null, { status: 401 });
      }),
      http.post(`${API_BASE}/auth/reissue`, () => {
        return HttpResponse.json(null, { status: 401 });
      }),
    );

    await expect(client.get("/protected")).rejects.toThrow();
    expect(useAuthStore.getState().accessToken).toBeNull();
  });
});
