import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { server } from "../../../test/mocks/server";
import { useAuthStore } from "../store/authStore";
import { login, logout, reissue } from "./auth";

const API_BASE = "https://api.orino.dev/api";

describe("auth API", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null });
  });

  describe("login", () => {
    it("로그인 성공 시 accessToken을 저장한다", async () => {
      server.use(
        http.post(`${API_BASE}/auth/login`, () => {
          return HttpResponse.json({
            code: "OK",
            data: { accessToken: "new-token" },
          });
        }),
      );

      await login({ loginId: "admin", password: "password" });

      expect(useAuthStore.getState().accessToken).toBe("new-token");
    });

    it("로그인 실패 시 예외를 던진다", async () => {
      server.use(
        http.post(`${API_BASE}/auth/login`, () => {
          return HttpResponse.json(
            { code: "AUTH-ERR-001", message: "인증 실패" },
            { status: 401 },
          );
        }),
      );

      await expect(
        login({ loginId: "wrong", password: "wrong" }),
      ).rejects.toThrow();
    });
  });

  describe("reissue", () => {
    it("토큰 갱신 성공 시 true를 반환하고 accessToken을 저장한다", async () => {
      server.use(
        http.post(`${API_BASE}/auth/reissue`, () => {
          return HttpResponse.json({
            code: "OK",
            data: { accessToken: "refreshed-token" },
          });
        }),
      );

      const result = await reissue();

      expect(result).toBe(true);
      expect(useAuthStore.getState().accessToken).toBe("refreshed-token");
    });

    it("토큰 갱신 실패 시 false를 반환하고 accessToken을 제거한다", async () => {
      useAuthStore.setState({ accessToken: "old-token" });

      server.use(
        http.post(`${API_BASE}/auth/reissue`, () => {
          return HttpResponse.json(null, { status: 401 });
        }),
      );

      const result = await reissue();

      expect(result).toBe(false);
      expect(useAuthStore.getState().accessToken).toBeNull();
    });
  });

  describe("logout", () => {
    it("로그아웃 성공 시 accessToken을 제거한다", async () => {
      useAuthStore.setState({ accessToken: "some-token" });

      server.use(
        http.post(`${API_BASE}/auth/logout`, () => {
          return HttpResponse.json({ code: "OK", data: null });
        }),
      );

      await logout();

      expect(useAuthStore.getState().accessToken).toBeNull();
    });

    it("로그아웃 API 실패 시에도 accessToken을 제거한다", async () => {
      useAuthStore.setState({ accessToken: "some-token" });

      server.use(
        http.post(`${API_BASE}/auth/logout`, () => {
          return HttpResponse.json(null, { status: 500 });
        }),
      );

      await expect(logout()).rejects.toThrow();

      expect(useAuthStore.getState().accessToken).toBeNull();
    });
  });
});
