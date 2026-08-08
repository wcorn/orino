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
        await screen.findByText("Open-Meteo · CC BY 4.0"),
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
              legs: [],
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
