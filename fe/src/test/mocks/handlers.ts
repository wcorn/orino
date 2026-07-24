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
  http.get(`${API_BASE}/integrations/google/status`, () => {
    return HttpResponse.json({
      code: "OK",
      data: {
        connected: false,
        googleEmail: null,
        scopes: null,
        connectedAt: null,
        reviewMirrorEnabled: false,
      },
    });
  }),

  // 기본값: 빈 복습 요약. 사이드바 뱃지(counts.now)가 항상 해소되도록. 값 검증은 server.use로 덮어쓴다.
  http.get(`${API_BASE}/planner/reviews/summary`, () => {
    return HttpResponse.json({
      code: "OK",
      data: {
        today: "2026-05-18",
        counts: { now: 0, overdue: 0, upcoming: 0, doneToday: 0 },
        estimatedMinutes: 0,
        materials: [],
      },
    });
  }),

  // 기본값: 빈 주간 계획표. 블록을 검증하는 테스트는 server.use로 덮어쓴다.
  http.get(`${API_BASE}/planner/plan`, () => {
    return HttpResponse.json({ code: "OK", data: { blocks: [] } });
  }),

  // 기본값: 자동 저장(전량 교체) — 요청 블록에 id를 붙여 그대로 반환.
  http.put(`${API_BASE}/planner/plan`, async ({ request }) => {
    const body = (await request.json()) as { blocks: unknown[] };
    const blocks = (body.blocks ?? []).map((b, i) => ({
      id: i + 1,
      ...(b as object),
    }));
    return HttpResponse.json({ code: "OK", data: { blocks } });
  }),

  // 기본값: 공휴일 없음. 공휴일 표시 검증 테스트는 server.use로 덮어쓴다.
  http.get(`${API_BASE}/planner/holidays`, () => {
    return HttpResponse.json({ code: "OK", data: [] });
  }),

  // 기본값: 월간 목표 없음. 목표를 검증하는 테스트는 server.use로 덮어쓴다.
  http.get(`${API_BASE}/planner/monthly-goals/:year/:month`, () => {
    return HttpResponse.json({ code: "OK", data: null });
  }),
];
