import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

const API_BASE = "https://api.orino.dev/api";

function city(placeId: number, name: string) {
  return {
    placeId,
    name,
    timezone: "Asia/Tokyo",
    currency: "JPY",
    countryCode: "JP",
    cityPlaceRef: `ChIJ_${placeId}`,
    lat: 35.68,
    lng: 139.76,
  };
}

const TOKYO = city(21, "도쿄");
const NIKKO = city(22, "닛코");

const DAYS = [
  {
    dayId: 501,
    dayIndex: 1,
    date: "2026-10-24",
    weekday: "토",
    activityCount: 2,
    baseCity: TOKYO,
    cityChanged: false,
    legIndex: 1,
    cityMemo: null,
    weather: null,
    stayTonight: null,
    stayCheckout: null,
  },
  {
    dayId: 502,
    dayIndex: 2,
    date: "2026-10-25",
    weekday: "일",
    activityCount: 1,
    baseCity: NIKKO,
    cityChanged: true,
    legIndex: 2,
    cityMemo: null,
    weather: null,
    stayTonight: null,
    stayCheckout: null,
  },
];

/** 도쿄 → 닛코 → 도쿄. 구간은 셋인데 도시는 둘이다 — `전체` 모드의 핵심 사례다. */
const CITY_LEGS = [
  {
    legIndex: 1,
    cityPlaceId: 21,
    cityName: "도쿄",
    days: 3,
    startDate: "2026-10-24",
    endDate: "2026-10-26",
    timezone: "Asia/Tokyo",
    lat: 35.68,
    lng: 139.76,
  },
  {
    legIndex: 2,
    cityPlaceId: 22,
    cityName: "닛코",
    days: 1,
    startDate: "2026-10-25",
    endDate: "2026-10-25",
    timezone: "Asia/Tokyo",
    lat: 36.75,
    lng: 139.6,
  },
  {
    legIndex: 3,
    cityPlaceId: 21,
    cityName: "도쿄",
    days: 2,
    startDate: "2026-10-26",
    endDate: "2026-10-27",
    timezone: "Asia/Tokyo",
    lat: 35.68,
    lng: 139.76,
  },
];

function mockCityLegs(legs: unknown[] = CITY_LEGS) {
  server.use(
    http.get(`${API_BASE}/travel/trips/:tripId/city-legs`, () =>
      HttpResponse.json({ code: "OK", data: legs }),
    ),
  );
}

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
    log: null,
    hasLog: false,
    outOfBaseCity: false,
    canDepartureNotify: false,
    ...overrides,
  };
}

const SENSOJI = {
  id: 10,
  name: "센소지",
  address: "다이토구",
  lat: 35.7147651,
  lng: 139.7966553,
  cityName: "도쿄",
  cityPlaceRef: "ChIJ_21",
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
          moves: [],
          stayMove: null,
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

    it("지도를 못 불러와도 하루 동선이 목록으로 성립한다 (#1159)", async () => {
      // jsdom에는 지도 키도 SDK도 없다 — 로더가 실제로 `unavailable`로 떨어지는 상태다.
      // 이때 회색 상자만 남으면 순서를 아는 곳이 화면에 하나도 없게 된다.
      mockBoard({
        "2026-10-24": [
          activity({
            id: 1,
            title: "센소지",
            startTime: "09:00",
            place: SENSOJI,
          }),
          activity({
            id: 2,
            title: "스카이트리",
            startTime: "13:00",
            place: SENSOJI,
          }),
        ],
      });

      renderMap();

      const list = await screen.findByRole("list", { name: "일정 순서" });
      const rows = within(list).getAllByRole("listitem");
      expect(rows).toHaveLength(2);
      expect(rows[0]).toHaveTextContent("1");
      expect(rows[0]).toHaveTextContent("센소지");
      expect(rows[0]).toHaveTextContent("09:00");
      expect(rows[1]).toHaveTextContent("스카이트리");
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

  describe("`전체` 모드 (§2.6)", () => {
    it("도시를 다시 방문해도 마커는 하나이고 첫 방문 구간 번호를 쓴다", async () => {
      mockBoard({ "2026-10-24": [activity({ id: 1, place: SENSOJI })] });
      mockCityLegs();

      renderMap("/travel/trips/3/map?mode=all");

      // 구간은 셋인데 도시는 둘이다.
      expect(
        await screen.findByRole("heading", { name: "여행 전체" }),
      ).toBeInTheDocument();
      expect(screen.getByText("도시 2곳 · 구간 순서")).toBeInTheDocument();

      // 구간 리스트는 접지 않는다 — 며칠씩 어디에 머무는지가 구간별 사실이다.
      const legRows = screen.getAllByRole("button", { name: /일 · 10\./ });
      expect(legRows).toHaveLength(3);
      expect(legRows[0]).toHaveTextContent("도쿄");
      expect(legRows[0]).toHaveTextContent("3일 · 10.24–10.26");
    });

    it("기본값은 `이 날짜`다 — 하루가 본체다", async () => {
      mockBoard({ "2026-10-24": [activity({ id: 1, place: SENSOJI })] });

      renderMap();

      expect(
        await screen.findByRole("heading", { name: "1일차 동선" }),
      ).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "이 날짜" })).toHaveAttribute(
        "aria-pressed",
        "true",
      );
    });

    it("토글로 모드를 바꾸면 보던 날짜는 그대로 둔다", async () => {
      mockBoard({ "2026-10-25": [activity({ id: 2, place: SENSOJI })] });
      mockCityLegs();

      const user = userEvent.setup();
      renderMap("/travel/trips/3/map?day=1");
      await screen.findByRole("heading", { name: "2일차 동선" });

      await user.click(screen.getByRole("button", { name: "전체" }));
      expect(
        await screen.findByRole("heading", { name: "여행 전체" }),
      ).toBeInTheDocument();

      // 돌아오면 보던 날짜여야 한다 — 모드만 바꾼 것이지 날짜를 버린 게 아니다.
      await user.click(screen.getByRole("button", { name: "이 날짜" }));
      expect(
        await screen.findByRole("heading", { name: "2일차 동선" }),
      ).toBeInTheDocument();
    });

    it("구간 행을 누르면 그 구간 첫날 보드로 간다", async () => {
      mockBoard({
        "2026-10-24": [activity({ id: 1, place: SENSOJI })],
        "2026-10-25": [
          activity({ id: 2, activityDate: "2026-10-25", place: SENSOJI }),
        ],
      });
      mockCityLegs();

      const user = userEvent.setup();
      renderMap("/travel/trips/3/map?mode=all");

      const legRows = await screen.findAllByRole("button", {
        name: /일 · 10\./,
      });
      // 2번째 구간(닛코)의 첫날은 2일차다.
      await user.click(legRows[1]);

      const tabs = await screen.findAllByRole("tab");
      await waitFor(() =>
        expect(tabs[1]).toHaveAttribute("aria-selected", "true"),
      );
    });

    it("좌표를 아는 도시가 없으면 그렇게 말한다 — 장소 검색으로 보내지 않는다", async () => {
      mockBoard({ "2026-10-24": [activity({ id: 1, place: SENSOJI })] });
      mockCityLegs([
        {
          legIndex: 1,
          cityPlaceId: 23,
          cityName: "하코네",
          days: 2,
          startDate: "2026-10-24",
          endDate: "2026-10-25",
          timezone: "Asia/Tokyo",
          lat: null,
          lng: null,
        },
      ]);

      renderMap("/travel/trips/3/map?mode=all");

      expect(
        await screen.findByText("좌표를 아는 도시가 없어요."),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: /장소 검색/ }),
      ).not.toBeInTheDocument();
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
