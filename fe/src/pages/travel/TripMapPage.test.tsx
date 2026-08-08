import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

const API_BASE = "https://api.orino.dev/api";

const DAYS = [
  {
    dayIndex: 1,
    date: "2026-10-24",
    weekday: "토",
    activityCount: 2,
    weather: null,
  },
  {
    dayIndex: 2,
    date: "2026-10-25",
    weekday: "일",
    activityCount: 1,
    weather: null,
  },
];

function activity(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    tripId: 3,
    title: "아침 산책",
    activityDate: "2026-10-24",
    startTime: "09:00",
    place: null,
    memo: null,
    url: null,
    notifyEnabled: false,
    notifyMinutes: null,
    departureNotifyEnabled: false,
    sortOrder: 0,
    hasLog: false,
    ...overrides,
  };
}

const SENSOJI = {
  id: 10,
  name: "센소지",
  address: "다이토구",
  lat: 35.7147651,
  lng: 139.7966553,
};

function mockBoard(byDate: Record<string, unknown[]>) {
  server.use(
    http.get(`${API_BASE}/travel/trips/:tripId/board`, ({ request }) => {
      const date =
        new URL(request.url).searchParams.get("date") ?? DAYS[0].date;
      return HttpResponse.json({
        code: "OK",
        data: {
          trip: {
            id: 3,
            title: "도쿄",
            timezone: "Asia/Tokyo",
            currency: "JPY",
            startDate: "2026-10-24",
            endDate: "2026-10-25",
            status: "UPCOMING",
            recordMode: false,
          },
          days: DAYS,
          selectedDate: date,
          archiveCount: 0,
          activities: byDate[date] ?? [],
          legs: [],
        },
      });
    }),
  );
}

function renderMap(path = "/travel/trips/3/map") {
  return renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: [path] },
  );
}

/** 오프라인을 흉내낸다 — 지도 타일은 캐시할 수 없어 이 분기가 실제로 쓰인다. */
function goOffline() {
  vi.spyOn(navigator, "onLine", "get").mockReturnValue(false);
}

describe("TripMapPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("헤더", () => {
    it("몇 일차인지와 지도에 찍힌 개수를 보여준다", async () => {
      mockBoard({
        "2026-10-24": [
          activity({ id: 1, place: SENSOJI }),
          activity({ id: 2 }), // 장소 없음 — 세지 않는다
        ],
      });

      renderMap();

      expect(
        await screen.findByRole("heading", { name: "1일차 동선" }),
      ).toBeInTheDocument();
      expect(
        screen.getByText("장소가 있는 일정 1개 · 직선 연결"),
      ).toBeInTheDocument();
    });

    it("보던 날짜를 그대로 그린다 — 여행 전체를 겹쳐 그리면 얼룩이 된다", async () => {
      mockBoard({
        "2026-10-24": [activity({ id: 1, place: SENSOJI })],
        "2026-10-25": [
          activity({ id: 2, activityDate: "2026-10-25", place: SENSOJI }),
          activity({ id: 3, activityDate: "2026-10-25", place: SENSOJI }),
        ],
      });

      renderMap("/travel/trips/3/map?day=1");

      expect(
        await screen.findByRole("heading", { name: "2일차 동선" }),
      ).toBeInTheDocument();
      expect(
        screen.getByText("장소가 있는 일정 2개 · 직선 연결"),
      ).toBeInTheDocument();
    });
  });

  describe("리스트로 돌아가기", () => {
    it("보던 날짜를 유지한 채 보드로 간다", async () => {
      mockBoard({ "2026-10-25": [activity({ id: 2, place: SENSOJI })] });

      const user = userEvent.setup();
      renderMap("/travel/trips/3/map?day=1");
      await screen.findByRole("heading", { name: "2일차 동선" });

      await user.click(screen.getByRole("button", { name: "리스트로 전환" }));

      // 날짜를 잃으면 현지에서 오늘을 다시 찾아야 한다 — 2일차 탭이 선택된 채로 돌아온다.
      const tabs = await screen.findAllByRole("tab");
      await waitFor(() =>
        expect(tabs[1]).toHaveAttribute("aria-selected", "true"),
      );
    });
  });

  describe("빈 상태", () => {
    it("장소가 있는 일정이 없으면 장소 검색으로 보낸다", async () => {
      mockBoard({ "2026-10-24": [activity({ id: 1 })] });

      const user = userEvent.setup();
      renderMap();

      expect(
        await screen.findByText("장소가 있는 일정이 없어요."),
      ).toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: /장소 검색/ }));

      expect(await screen.findByLabelText("장소 검색")).toBeInTheDocument();
    });

    it("오프라인이면 지도를 못 본다고 알린다 — 타일은 캐시할 수 없다", async () => {
      goOffline();
      mockBoard({ "2026-10-24": [activity({ id: 1, place: SENSOJI })] });

      renderMap();

      expect(
        await screen.findByText("오프라인에서는 지도를 볼 수 없어요."),
      ).toBeInTheDocument();
      // 빈 지도를 보여주고 멈춘 것처럼 두지 않는다. 헤더와 빈 상태 양쪽에 있다.
      expect(
        screen.getAllByRole("button", { name: /리스트로 전환/ }),
      ).toHaveLength(2);
    });
  });

  describe("보드에서 들어오기", () => {
    it("지도 버튼이 보던 날짜를 들고 간다", async () => {
      mockBoard({ "2026-10-25": [activity({ id: 2, place: SENSOJI })] });

      const user = userEvent.setup();
      renderWithRouter(
        <Providers>
          <AppRouter />
        </Providers>,
        { initialEntries: ["/travel/trips/3/board?day=1"] },
      );

      await user.click(await screen.findByRole("button", { name: "지도" }));

      expect(
        await screen.findByRole("heading", { name: "2일차 동선" }),
      ).toBeInTheDocument();
    });
  });
});
