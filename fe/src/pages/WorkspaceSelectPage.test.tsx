import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

const API_BASE = "https://api.orino.dev/api";

/** 여행이 거쳐 가는 도시. 오늘 값은 진행 중일 때만 채운다. */
function cities(names: string[], overrides: Record<string, unknown> = {}) {
  return {
    names,
    count: new Set(names).size,
    today: null,
    movedFrom: null,
    todayDayIndex: null,
    todayTimezone: null,
    todayCurrency: null,
    ...overrides,
  };
}

function ongoingTrip(overrides: Record<string, unknown> = {}) {
  return {
    id: 3,
    title: "일본 9박 10일",
    boardPath: "/travel/trips/3/board",
    startDate: "2026-10-24",
    endDate: "2026-11-02",
    activityCount: 27,
    cities: cities(["오사카", "교토"]),
    ...overrides,
  };
}

function mockTravelSummary(data: unknown) {
  server.use(
    http.get(`${API_BASE}/travel/summary`, () =>
      HttpResponse.json({ code: "OK", data }),
    ),
  );
}

function renderApp(initialEntries: string[]) {
  return renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries },
  );
}

describe("WorkspaceSelectPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  it("네 워크스페이스 카드를 보여준다 — 링크도 가계부도 일상의 하위가 아니다", async () => {
    renderApp(["/select"]);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "어디로 갈까요" }),
      ).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: /여행/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /일상/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /링크/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /가계부/ })).toBeInTheDocument();
  });

  it("사이드바가 없다 — 선택 화면은 앱 셸 밖이다", async () => {
    renderApp(["/select"]);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "어디로 갈까요" }),
      ).toBeInTheDocument();
    });
    expect(screen.queryByRole("navigation", { name: "주 메뉴" })).toBeNull();
  });

  it("여행이 없으면 배지도 메타도 그리지 않는다", async () => {
    renderApp(["/select"]);

    const travelCard = await screen.findByRole("button", { name: /여행/ });
    // 더미 텍스트 대신 빈 자리. 설명 줄만 남는다.
    expect(travelCard).toHaveTextContent("일정 보드, 지도, 알림, 환율·날씨");
    expect(travelCard).not.toHaveTextContent(/D-/);
  });

  it("다음 여행이 있으면 D-day 배지와 기간·도시 메타를 보여준다", async () => {
    mockTravelSummary({
      ongoing: null,
      next: {
        id: 3,
        title: "일본 9박 10일",
        destinationName: "오사카",
        startDate: "2026-10-24",
        endDate: "2026-11-02",
        dDay: 78,
        activityCount: 13,
        cities: cities(["오사카", "교토", "나라", "고베", "나고야", "도쿄"]),
      },
      recentCompleted: null,
    });

    renderApp(["/select"]);

    const travelCard = await screen.findByRole("button", { name: /여행/ });
    await waitFor(() => {
      expect(travelCard).toHaveTextContent("D-78");
    });
    expect(travelCard).toHaveTextContent(
      "일본 9박 10일 · 10.24 – 11.02 · 오사카 → 교토 → … → 도쿄 (6개 도시)",
    );
  });

  it("진행 중 여행은 오늘 어디인지 말한다 — 옮기는 날이면 어디서 어디로", async () => {
    mockTravelSummary({
      ongoing: ongoingTrip({
        cities: cities(["오사카", "교토"], {
          today: "교토",
          movedFrom: "오사카",
          todayDayIndex: 4,
          todayTimezone: "Asia/Tokyo",
          todayCurrency: "JPY",
        }),
      }),
      next: null,
      recentCompleted: null,
    });

    renderApp(["/select"]);

    const travelCard = await screen.findByRole("button", { name: /여행/ });
    await waitFor(() => {
      expect(travelCard).toHaveTextContent(
        "일본 9박 10일 · 오늘 오사카 → 교토",
      );
    });
  });

  it("진행 중 여행이 있으면 '진행 중' 배지를 보여주고 눌렀을 때 보드로 간다", async () => {
    mockTravelSummary({
      ongoing: ongoingTrip({ title: "도쿄 3박 4일" }),
      next: null,
      recentCompleted: null,
    });

    renderApp(["/select"]);

    const travelCard = await screen.findByRole("button", { name: /여행/ });
    await waitFor(() => {
      expect(travelCard).toHaveTextContent("진행 중");
    });

    await userEvent.click(travelCard);

    await waitFor(() => {
      expect(screen.getByRole("tab", { name: /10\.24/ })).toBeInTheDocument();
    });
  });

  it("진행 중 여행이 없으면 여행 카드는 여행 홈으로 간다", async () => {
    renderApp(["/select"]);

    const travelCard = await screen.findByRole("button", { name: /여행/ });
    await userEvent.click(travelCard);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "여행" })).toBeInTheDocument();
    });
  });

  it("일상 카드는 미완료 복습 수를 배지로 보여준다", async () => {
    server.use(
      http.get(`${API_BASE}/planner/reviews/summary`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            today: "2026-05-18",
            counts: { now: 3, overdue: 0, upcoming: 0, doneToday: 0 },
            estimatedMinutes: 10,
            materials: [],
          },
        }),
      ),
    );

    renderApp(["/select"]);

    const dailyCard = await screen.findByRole("button", { name: /일상/ });
    await waitFor(() => {
      expect(dailyCard).toHaveTextContent("복습 3");
    });
  });

  it("오늘 루틴이 있으면 개수를 메타로 보여준다", async () => {
    server.use(
      http.get(`${API_BASE}/planner/calendar`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            from: "2026-05-18",
            to: "2026-05-18",
            googleConnected: true,
            partial: false,
            errors: [],
            events: [
              routineEvent("1"),
              routineEvent("2"),
              // 루틴이 아닌 일정은 세지 않는다.
              {
                id: "plain",
                title: "회의",
                allDay: false,
                start: "2026-05-18T10:00:00",
                end: null,
                location: null,
                recurring: false,
                source: "google",
                routine: null,
              },
            ],
            tasks: [],
            reviews: [],
          },
        }),
      ),
    );

    renderApp(["/select"]);

    const dailyCard = await screen.findByRole("button", { name: /일상/ });
    await waitFor(() => {
      expect(dailyCard).toHaveTextContent("오늘 루틴 2개");
    });
  });

  it("일상 카드를 누르면 홈으로 간다", async () => {
    server.use(
      http.get(`${API_BASE}/planner/reviews/today`, () =>
        HttpResponse.json({
          code: "OK",
          data: { today: "2026-05-18", reviews: [] },
        }),
      ),
    );

    renderApp(["/select"]);

    const dailyCard = await screen.findByRole("button", { name: /일상/ });
    await userEvent.click(dailyCard);

    await waitFor(() => {
      expect(screen.getByText("안녕하세요 👋")).toBeInTheDocument();
    });
  });

  it("링크 카드는 발급 수와 이번 주 방문을 메타로 보여준다", async () => {
    server.use(
      http.get(`${API_BASE}/shortlinks/summary`, () =>
        HttpResponse.json({
          code: "OK",
          data: { total: 34, visitsThisWeek: 128 },
        }),
      ),
    );

    renderApp(["/select"]);

    const linkCard = await screen.findByRole("button", { name: /링크/ });
    await waitFor(() => {
      expect(linkCard).toHaveTextContent("링크 34개 · 이번 주 방문 128");
    });
    expect(linkCard).toHaveTextContent("짧은 주소 발급, QR, 방문 통계");
  });

  it("요약을 못 받으면 메타 줄 자체를 그리지 않는다 — 카드는 그대로 눌린다", async () => {
    server.use(
      http.get(`${API_BASE}/shortlinks/summary`, () =>
        HttpResponse.json(
          { code: "GLB-ERR-003", message: "내부 서버 오류입니다." },
          { status: 500 },
        ),
      ),
    );

    renderApp(["/select"]);

    const linkCard = await screen.findByRole("button", { name: /링크/ });
    // `링크 0개`가 아니라 줄이 없다 — 0개인 것과 아직 모르는 것은 다르다.
    await waitFor(() => {
      expect(linkCard).not.toHaveTextContent(/링크 \d+개/);
    });
    expect(linkCard).not.toHaveTextContent(/이번 주 방문/);

    await userEvent.click(linkCard);
    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "링크" })).toBeInTheDocument();
    });
  });

  it("링크 카드를 누르면 /links로 간다", async () => {
    renderApp(["/select"]);

    const linkCard = await screen.findByRole("button", { name: /링크/ });
    await userEvent.click(linkCard);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "링크" })).toBeInTheDocument();
    });
    // 링크 워크스페이스로 들어왔으므로 사이드바 스위처도 링크를 가리킨다.
    expect(
      screen.getByRole("button", { name: "워크스페이스 전환 — 현재 링크" }),
    ).toBeInTheDocument();
  });

  it("가계부 카드는 요약이 없어 배지도 메타도 그리지 않는다", async () => {
    renderApp(["/select"]);

    const ledgerCard = await screen.findByRole("button", { name: /가계부/ });
    expect(ledgerCard).toHaveTextContent("내역, 카드 청구서, 정기 항목, 예산");
    // `미납 0`도 `이번 달 예상 0`도 그리지 않는다 — 없는 것과 모르는 것은 다르다.
    expect(ledgerCard).not.toHaveTextContent(/미납/);
    expect(ledgerCard).not.toHaveTextContent(/이번 달 예상/);
  });

  it("가계부 카드를 누르면 /ledger로 간다", async () => {
    renderApp(["/select"]);

    const ledgerCard = await screen.findByRole("button", { name: /가계부/ });
    await userEvent.click(ledgerCard);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "가계부" }),
      ).toBeInTheDocument();
    });
    expect(
      screen.getByRole("button", { name: "워크스페이스 전환 — 현재 가계부" }),
    ).toBeInTheDocument();
  });

  it("아직 없는 가계부 하위 경로는 가계부 홈으로 보낸다 — 랜딩으로 튕기지 않는다", async () => {
    // 가져오기는 v2라 아직 라우트가 없다. 예산·정기 항목은 v1.5에서 생겼다.
    renderApp(["/ledger/import"]);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "가계부" }),
      ).toBeInTheDocument();
    });
  });
});

function routineEvent(id: string) {
  return {
    id,
    title: `루틴 ${id}`,
    allDay: false,
    start: "2026-05-18T08:00:00",
    end: null,
    location: null,
    recurring: true,
    source: "google",
    routine: { type: "habit", recurringEventId: id, done: false },
  };
}
