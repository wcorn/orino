import { http, HttpResponse } from "msw";

const API_BASE = "https://api.orino.dev/api";

export const handlers = [
  http.post(`${API_BASE}/auth/login`, async ({ request }) => {
    const body = (await request.json()) as {
      loginId: string;
      password: string;
    };

    if (body.loginId === "admin" && body.password === "password") {
      return HttpResponse.json({
        code: "OK",
        data: { accessToken: "mock-access-token" },
      });
    }

    return HttpResponse.json(
      {
        code: "AUTH-ERR-001",
        message: "아이디 또는 비밀번호가 올바르지 않습니다.",
      },
      { status: 401 },
    );
  }),

  http.post(`${API_BASE}/auth/reissue`, () => {
    return HttpResponse.json(
      { code: "AUTH-ERR-002", message: "유효하지 않은 토큰입니다." },
      { status: 401 },
    );
  }),

  http.post(`${API_BASE}/auth/logout`, () => {
    return HttpResponse.json({ code: "OK", data: null });
  }),

  // 기본값: Google 미연동. 연동 상태를 검증하는 테스트는 server.use로 덮어쓴다.
  http.get(`${API_BASE}/planner/google/status`, () => {
    return HttpResponse.json({
      code: "OK",
      data: {
        connected: false,
        googleEmail: null,
        scopes: null,
        connectedAt: null,
      },
    });
  }),

  // 기본값: 공휴일 없음. 공휴일 표시 검증 테스트는 server.use로 덮어쓴다.
  http.get(`${API_BASE}/planner/holidays`, () => {
    return HttpResponse.json({ code: "OK", data: [] });
  }),
];
