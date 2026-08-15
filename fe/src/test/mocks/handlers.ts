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

  // 기본값: 여행 없음. 세 필드가 전부 null이면 "여행 만들기"만 보이는 상태다.
  http.get(`${API_BASE}/travel/summary`, () => {
    return HttpResponse.json({
      code: "OK",
      data: { ongoing: null, next: null, recentCompleted: null },
    });
  }),

  // 기본값: 일정 없는 하루짜리 보드. 보드를 거쳐가는 테스트(생성 후 이동 등)가
  // 매번 핸들러를 세우지 않아도 되도록. 내용을 검증하는 테스트는 server.use로 덮어쓴다.
  http.get(`${API_BASE}/travel/trips/:tripId/board`, ({ params }) => {
    return HttpResponse.json({
      code: "OK",
      data: {
        trip: {
          id: Number(params.tripId),
          title: "여행",
          startDate: "2026-10-24",
          endDate: "2026-10-24",
          status: "UPCOMING",
          recordMode: false,
          cityCount: 1,
          countryCount: 1,
          singleCity: true,
        },
        days: [
          {
            dayId: 1,
            dayIndex: 1,
            date: "2026-10-24",
            weekday: "토",
            activityCount: 0,
            baseCity: {
              placeId: 21,
              name: "도쿄",
              timezone: "Asia/Tokyo",
              currency: "JPY",
              countryCode: "JP",
              cityPlaceRef: null,
              lat: null,
              lng: null,
            },
            cityChanged: false,
            legIndex: 1,
            cityMemo: null,
            weather: null,
            stayTonight: null,
            stayCheckout: null,
          },
        ],
        selectedDate: "2026-10-24",
        archiveCount: 0,
        activities: [],
        moves: [],
        stayMove: null,
      },
    });
  }),

  // 기본값: 숙소 없음. 보드는 숙소 목록을 항상 함께 읽는다(상세 시트·겹침 미리보기).
  http.get(`${API_BASE}/travel/trips/:tripId/stays`, () => {
    return HttpResponse.json({ code: "OK", data: [] });
  }),

  // 기본값: 빈 캘린더 피드. `/select`의 "오늘 루틴" 메타가 항상 해소되도록.
  http.get(`${API_BASE}/planner/calendar`, () => {
    return HttpResponse.json({
      code: "OK",
      data: {
        from: "2026-05-18",
        to: "2026-05-18",
        googleConnected: false,
        partial: false,
        errors: [],
        events: [],
        tasks: [],
        reviews: [],
      },
    });
  }),
];
