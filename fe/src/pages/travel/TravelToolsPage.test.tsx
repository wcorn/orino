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

const TRIP = {
  id: 3,
  title: "도쿄 3박 4일",
  destinationName: "도쿄",
  destinationPlaceId: null,
  startDate: "2026-10-24",
  endDate: "2026-10-27",
  timezone: "Asia/Tokyo",
  currency: "JPY",
  lat: 35.6764,
  lng: 139.65,
  defaultNotifyMinutes: 15,
  morningSummaryEnabled: false,
  status: "UPCOMING",
  dDay: 78,
  totalDays: 4,
  activityCount: 3,
};

function mockTrip() {
  server.use(
    http.get(`${API_BASE}/travel/trips/:tripId`, () =>
      HttpResponse.json({ code: "OK", data: TRIP }),
    ),
  );
}

function mockFx(rate = 8.9427) {
  server.use(
    http.get(`${API_BASE}/travel/fx`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          base: "JPY",
          quote: "KRW",
          rate,
          source: "ECB",
          referenceDate: "2026-08-07",
          fetchedAt: "2026-08-08T00:00:00Z",
        },
      }),
    ),
  );
}

function mockWeather(daily: unknown[], hourly: Record<string, unknown[]> = {}) {
  server.use(
    http.get(`${API_BASE}/travel/trips/:tripId/weather`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          source: "Open-Meteo",
          license: "CC BY 4.0",
          fetchedAt: "2026-08-08T00:00:00Z",
          daily,
          hourly,
        },
      }),
    ),
  );
}

/**
 * 오늘(또는 첫날) 기준 도시를 정하는 것은 <b>보드</b>다 — 여행 상세의 통화·타임존은 첫날
 * 도시에서 파생된 값이라 도시를 옮긴 날짜에서 조용히 틀린다.
 */
function mockBoardCity(city: {
  name: string;
  currency: string;
  countryCode: string;
  timezone: string;
}) {
  server.use(
    http.get(`${API_BASE}/travel/trips/:tripId/board`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          trip: {
            id: 3,
            title: "일본",
            startDate: "2026-10-24",
            endDate: "2026-10-25",
            status: "ONGOING",
            recordMode: false,
            cityCount: 2,
            countryCount: 2,
            singleCity: false,
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
                cityPlaceRef: "ChIJ_tokyo",
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
            {
              dayId: 2,
              dayIndex: 2,
              date: "2026-10-25",
              weekday: "일",
              activityCount: 0,
              baseCity: {
                placeId: 22,
                ...city,
                cityPlaceRef: "ChIJ_other",
                lat: null,
                lng: null,
              },
              cityChanged: true,
              legIndex: 2,
              cityMemo: null,
              weather: null,
              stayTonight: null,
              stayCheckout: null,
            },
          ],
          // 오늘이 2일차다 — 서버가 진행 중 여행의 오늘을 골라 준다.
          selectedDate: "2026-10-25",
          archiveCount: 0,
          activities: [],
          moves: [],
          stayMove: null,
        },
      }),
    ),
  );
}

function renderTools() {
  return renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: ["/travel/tools?tripId=3"] },
  );
}

describe("TravelToolsPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
    mockTrip();
    mockFx();
    mockWeather([]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("환율", () => {
    it("양방향이다 — 어느 칸에 쳐도 반대가 따라온다", async () => {
      const user = userEvent.setup();
      renderTools();

      const jpy = await screen.findByLabelText("JPY 금액");
      await user.type(jpy, "10000");

      expect(await screen.findByLabelText("KRW 금액")).toHaveValue("89,427");

      // 반대 방향도 된다.
      await user.clear(screen.getByLabelText("KRW 금액"));
      await user.type(screen.getByLabelText("KRW 금액"), "89427");
      await waitFor(() =>
        expect(screen.getByLabelText("JPY 금액")).toHaveValue("10,000"),
      );
    });

    it("프리셋을 누르면 그 금액으로 환산한다", async () => {
      const user = userEvent.setup();
      renderTools();

      await user.click(
        await screen.findByRole("button", { name: /5,000 JPY/ }),
      );

      expect(screen.getByLabelText("JPY 금액")).toHaveValue("5000");
      // 5,000 × 8.9427 = 44,713.5 — 소수는 두 자리까지 남긴다.
      expect(screen.getByLabelText("KRW 금액")).toHaveValue("44,713.5");
    });

    it("면책 문구와 기준일을 밝힌다 — 돈이 걸린 숫자다", async () => {
      renderTools();

      expect(
        await screen.findByText(/ECB 기준 · 2026-08-07/),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/실제 결제 환율과 다를 수 있습니다/),
      ).toBeInTheDocument();
    });

    it("통화를 바꾸면 그 통화로 다시 조회한다 — 경유지에서 다른 돈을 쓴다", async () => {
      const asked: string[] = [];
      server.use(
        http.get(`${API_BASE}/travel/fx`, ({ request }) => {
          const base = new URL(request.url).searchParams.get("base") ?? "";
          asked.push(base);
          return HttpResponse.json({
            code: "OK",
            data: {
              base,
              quote: "KRW",
              rate: base === "USD" ? 1332.5 : 8.9427,
              source: "ECB",
              referenceDate: "2026-08-07",
              fetchedAt: "2026-08-08T00:00:00Z",
            },
          });
        }),
      );

      const user = userEvent.setup();
      renderTools();

      // 여행 통화가 기본이다.
      await screen.findByLabelText("JPY 금액");

      await user.click(screen.getByRole("combobox", { name: "기준 통화" }));
      await user.click(
        await screen.findByRole("option", { name: /USD · 미국 달러/ }),
      );

      expect(await screen.findByLabelText("USD 금액")).toBeInTheDocument();
      expect(asked).toContain("USD");
    });

    it("오프라인이면 최신 아님을 알린다", async () => {
      vi.spyOn(navigator, "onLine", "get").mockReturnValue(false);
      renderTools();

      expect(await screen.findByText("최신 아님")).toBeInTheDocument();
      // 배지는 환율보다 먼저 뜬다 — 기준일 문구는 환율이 온 뒤에야 나온다.
      expect(
        await screen.findByText(
          (_, element) =>
            element?.tagName === "SPAN" &&
            Boolean(element.textContent?.includes("오프라인 캐시")),
        ),
      ).toBeInTheDocument();
    });
  });

  describe("날씨", () => {
    it("강수확률 60% 이상이면 강조한다 — 이 카드를 보는 이유다", async () => {
      mockWeather([
        {
          date: "2026-10-24",
          icon: "CLOUD",
          tempMax: 15,
          tempMin: 8,
          precipProbability: 20,
        },
        {
          date: "2026-10-25",
          icon: "RAIN",
          tempMax: 14,
          tempMin: 9,
          precipProbability: 80,
        },
      ]);

      renderTools();

      const low = await screen.findByText("20%");
      const high = screen.getByText("80%");
      expect(low.className).not.toContain("text-warning");
      expect(high.className).toContain("text-warning");
    });

    it("날짜를 고르면 그날 시간대별을 보여준다", async () => {
      mockWeather(
        [
          {
            date: "2026-10-24",
            icon: "CLEAR",
            tempMax: 15,
            tempMin: 8,
            precipProbability: 10,
          },
        ],
        {
          "2026-10-24": [
            { time: "09:00", icon: "CLEAR", temp: 12 },
            { time: "12:00", icon: "CLOUD", temp: 15 },
          ],
        },
      );

      renderTools();

      expect(await screen.findByText("09:00")).toBeInTheDocument();
      expect(screen.getByText("15°")).toBeInTheDocument();
    });

    it("예보 범위 밖이면 그렇게 말한다 — 오류가 아니다", async () => {
      renderTools();

      expect(await screen.findByText("예보 범위 밖")).toBeInTheDocument();
    });

    it("출처를 표기한다 — Open-Meteo는 필수다", async () => {
      renderTools();

      expect(
        await screen.findByText(
          "도시별로 따로 조회해요 · Open-Meteo · CC BY 4.0",
        ),
      ).toBeInTheDocument();
    });
  });

  describe("번역", () => {
    it("여행 타임존에 맞는 언어로 연다", async () => {
      const open = vi.spyOn(window, "open").mockImplementation(() => null);
      const user = userEvent.setup();
      renderTools();

      await user.click(
        await screen.findByRole("button", { name: /구글 번역 열기/ }),
      );

      expect(open).toHaveBeenCalledWith(
        expect.stringContaining("tl=ja"),
        "_blank",
        "noopener",
      );
    });
  });

  describe("오늘 도시를 따라간다 (§3.7)", () => {
    const BANGKOK = {
      name: "방콕",
      currency: "THB",
      countryCode: "TH",
      timezone: "Asia/Bangkok",
    };

    it("기본 통화는 첫날이 아니라 오늘 도시의 것이다", async () => {
      const calls: URL[] = [];
      server.use(
        http.get(`${API_BASE}/travel/fx`, ({ request }) => {
          calls.push(new URL(request.url));
          return HttpResponse.json({
            code: "OK",
            data: {
              base: "THB",
              quote: "KRW",
              rate: 38.5,
              source: "ECB",
              referenceDate: "2026-08-07",
              fetchedAt: "2026-08-08T00:00:00Z",
            },
          });
        }),
      );
      mockBoardCity(BANGKOK);

      renderTools();

      // 여행 상세는 JPY(첫날 도쿄)라고 말한다. 오늘은 방콕이다.
      await waitFor(() =>
        expect(calls[calls.length - 1]?.searchParams.get("base")).toBe("THB"),
      );
    });

    it("기준 도시 칩이 어느 도시의 통화인지 말한다", async () => {
      mockBoardCity(BANGKOK);

      renderTools();

      expect(await screen.findByText("방콕 · THB")).toBeInTheDocument();
    });

    it("통화 목록은 여행에 등장하는 통화가 먼저다", async () => {
      mockBoardCity(BANGKOK);

      renderTools();
      await screen.findByText("방콕 · THB");
      await userEvent.click(
        screen.getByRole("combobox", { name: "기준 통화" }),
      );

      const options = (await screen.findAllByRole("option")).map(
        (o) => o.textContent,
      );
      // 도쿄(JPY)·방콕(THB)이 앞줄에 선다. 순서는 구간 순서를 따른다.
      expect(options.slice(0, 2)).toEqual(["JPY · 일본 엔", "THB · 태국 바트"]);
    });

    it("번역 목적 언어는 기준 도시 국가를 따라간다", async () => {
      const open = vi.spyOn(window, "open").mockImplementation(() => null);
      mockBoardCity(BANGKOK);

      renderTools();
      await screen.findByText("방콕 · THB");
      await userEvent.click(
        screen.getByRole("button", { name: /구글 번역 열기/ }),
      );

      // 타임존만 보면 Asia/Bangkok → th로 같지만, 국가가 언어를 정하는 것이 규칙이다.
      expect(open).toHaveBeenCalledWith(
        expect.stringContaining("tl=th"),
        "_blank",
        "noopener",
      );
      open.mockRestore();
    });

    it("날씨 행에 그날 도시명이 붙는다", async () => {
      mockWeather([
        {
          date: "2026-10-24",
          cityName: "도쿄",
          icon: "CLEAR",
          tempMax: 20,
          tempMin: 13,
          precipProbability: 10,
        },
        {
          date: "2026-10-25",
          cityName: "닛코",
          icon: "RAIN",
          tempMax: 9,
          tempMin: 3,
          precipProbability: 80,
        },
      ]);

      renderTools();

      expect(await screen.findByText("도쿄")).toBeInTheDocument();
      expect(screen.getByText("닛코")).toBeInTheDocument();
    });

    /**
     * 도시가 바뀌는 날은 <b>같은 날짜로 줄이 둘</b>이다(D-25) — 떠나온 도시가 먼저. 한 줄의
     * 정체가 날짜가 아니라 (날짜, 도시)라서, 날짜로 묶거나 중복을 지우면 오전 도시가 사라진다.
     */
    it("도시가 바뀌는 날은 같은 날짜로 줄이 둘이다 — 떠나온 도시가 먼저", async () => {
      mockWeather([
        {
          date: "2026-10-24",
          cityName: "오사카",
          icon: "RAIN",
          tempMax: 18,
          tempMin: 11,
          precipProbability: 70,
        },
        {
          date: "2026-10-24",
          cityName: "교토",
          icon: "CLOUD",
          tempMax: 16,
          tempMin: 9,
          precipProbability: 30,
        },
      ]);

      renderTools();

      await screen.findByText("오사카");
      expect(screen.getAllByText("10-24")).toHaveLength(2);
      expect(screen.getByText("교토")).toBeInTheDocument();
      // 첫 줄만 눌린 상태다. 날짜를 선택 키로 쓰면 두 줄이 함께 눌린다.
      const pressed = screen
        .getAllByRole("button")
        .filter((row) => row.getAttribute("aria-pressed") === "true");
      expect(pressed).toHaveLength(1);
      expect(pressed[0]).toHaveTextContent("오사카");
    });

    it("한 줄을 눌러도 나머지 한 줄은 눌리지 않는다", async () => {
      mockWeather([
        {
          date: "2026-10-24",
          cityName: "오사카",
          icon: "RAIN",
          tempMax: 18,
          tempMin: 11,
          precipProbability: 70,
        },
        {
          date: "2026-10-24",
          cityName: "교토",
          icon: "CLOUD",
          tempMax: 16,
          tempMin: 9,
          precipProbability: 30,
        },
      ]);

      renderTools();
      await userEvent.click(await screen.findByText("교토"));

      const pressed = screen
        .getAllByRole("button")
        .filter((row) => row.getAttribute("aria-pressed") === "true");
      expect(pressed).toHaveLength(1);
      expect(pressed[0]).toHaveTextContent("교토");
    });
  });

  describe("보드에서 들어오기", () => {
    it("도구 버튼이 그 여행을 들고 간다", async () => {
      server.use(
        http.get(`${API_BASE}/travel/trips/:tripId/board`, () =>
          HttpResponse.json({
            code: "OK",
            data: {
              trip: {
                id: 3,
                title: "도쿄 3박 4일",
                timezone: "Asia/Tokyo",
                currency: "JPY",
                startDate: "2026-10-24",
                endDate: "2026-10-27",
                status: "UPCOMING",
                recordMode: false,
              },
              days: [
                {
                  dayIndex: 1,
                  date: "2026-10-24",
                  weekday: "토",
                  activityCount: 0,
                  weather: null,
                },
              ],
              selectedDate: "2026-10-24",
              archiveCount: 0,
              activities: [],
              moves: [],
            },
          }),
        ),
      );

      const user = userEvent.setup();
      renderWithRouter(
        <Providers>
          <AppRouter />
        </Providers>,
        { initialEntries: ["/travel/trips/3/board"] },
      );

      await user.click(await screen.findByRole("button", { name: "도구" }));

      expect(await screen.findByText("환율")).toBeInTheDocument();
    });
  });
});
