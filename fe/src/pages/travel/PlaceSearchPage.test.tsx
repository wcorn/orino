import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import { useToastStore } from "@/shared/lib/toast";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

const API_BASE = "https://api.orino.dev/api";

const DAYS = [
  {
    dayIndex: 1,
    date: "2026-10-24",
    weekday: "토",
    activityCount: 0,
    weather: null,
  },
  {
    dayIndex: 2,
    date: "2026-10-25",
    weekday: "일",
    activityCount: 0,
    weather: null,
  },
];

function place(overrides: Record<string, unknown> = {}) {
  return {
    id: null,
    googlePlaceId: "ChIJ_senso",
    name: "센소지",
    category: "불교사찰",
    address: "도쿄도 다이토구",
    rating: 4.6,
    lat: 35.7147,
    lng: 139.7966,
    ...overrides,
  };
}

function mockBoard() {
  server.use(
    http.get(`${API_BASE}/travel/trips/:tripId/board`, () =>
      HttpResponse.json({
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
          selectedDate: DAYS[0].date,
          archiveCount: 0,
          activities: [],
          travelTimes: [],
        },
      }),
    ),
  );
}

/** 검색 응답. 호출된 URL을 모아 편향(tripId)이 실렸는지 확인할 수 있게 한다. */
function mockSearch(results: unknown[] = [place()]) {
  const calls: URL[] = [];
  server.use(
    http.get(`${API_BASE}/travel/places/search`, ({ request }) => {
      calls.push(new URL(request.url));
      return HttpResponse.json({ code: "OK", data: results });
    }),
  );
  return calls;
}

function renderSearch(path = "/travel/trips/3/places") {
  return renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: [path] },
  );
}

describe("PlaceSearchPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
    useToastStore.setState({ toasts: [] });
    localStorage.clear();
    mockBoard();
  });

  describe("검색", () => {
    it("제출하기 전에는 검색하지 않는다 — 호출 하나가 곧 비용이다", async () => {
      const calls = mockSearch();
      const user = userEvent.setup();
      renderSearch();

      await user.type(await screen.findByLabelText("장소 검색"), "센소");

      expect(calls).toHaveLength(0);
    });

    it("제출하면 결과를 카테고리·평점·주소와 함께 보여준다", async () => {
      mockSearch();
      const user = userEvent.setup();
      renderSearch();

      await user.type(
        await screen.findByLabelText("장소 검색"),
        "센소지{Enter}",
      );

      expect(await screen.findByText("센소지")).toBeInTheDocument();
      expect(
        screen.getByText("불교사찰 · ★ 4.6 · 도쿄도 다이토구"),
      ).toBeInTheDocument();
    });

    it("출처를 표기한다 — 지도 없이 Places 데이터를 보여주면 필수다", async () => {
      mockSearch();
      const user = userEvent.setup();
      renderSearch();

      await user.type(
        await screen.findByLabelText("장소 검색"),
        "센소지{Enter}",
      );
      await screen.findByText("센소지");

      expect(screen.getByText("Google Maps")).toBeInTheDocument();
    });

    it("결과가 없으면 출처 표기도 없다 — 보여줄 데이터가 없다", async () => {
      mockSearch([]);
      const user = userEvent.setup();
      renderSearch();

      await user.type(
        await screen.findByLabelText("장소 검색"),
        "센소지{Enter}",
      );
      await screen.findByText("검색 결과가 없어요.");

      expect(screen.queryByText("Google Maps")).toBeNull();
    });

    it("여행 목적지 주변으로 편향시킨다", async () => {
      const calls = mockSearch();
      const user = userEvent.setup();
      renderSearch();

      await user.type(await screen.findByLabelText("장소 검색"), "라멘{Enter}");
      await screen.findByText("센소지");

      expect(calls[0].searchParams.get("q")).toBe("라멘");
      expect(calls[0].searchParams.get("tripId")).toBe("3");
    });

    it("URL의 검색어로 들어오면 곧바로 결과를 보여준다 — 담고 뒤로 와도 남아 있어야 한다", async () => {
      mockSearch();
      renderSearch("/travel/trips/3/places?q=%EC%84%BC%EC%86%8C%EC%A7%80");

      expect(await screen.findByText("센소지")).toBeInTheDocument();
      expect(await screen.findByLabelText("장소 검색")).toHaveValue("센소지");
    });

    it("검색이 실패해도 화면이 죽지 않는다", async () => {
      server.use(
        http.get(`${API_BASE}/travel/places/search`, () =>
          HttpResponse.json({ code: "ERR" }, { status: 500 }),
        ),
      );
      renderSearch("/travel/trips/3/places?q=%EC%84%BC%EC%86%8C%EC%A7%80");

      expect(await screen.findByText(/검색하지 못했어요/)).toBeInTheDocument();
    });
  });

  describe("최근 검색어", () => {
    it("검색어가 비었을 때만 칩으로 보여주고, 누르면 그 검색어로 검색한다", async () => {
      mockSearch();
      const user = userEvent.setup();
      renderSearch();

      const input = await screen.findByLabelText("장소 검색");
      await user.type(input, "센소지{Enter}");
      await screen.findByText("센소지");

      // 검색어를 지우면 최근 검색어가 다시 드러난다.
      await user.clear(input);
      await user.click(screen.getByRole("button", { name: "뒤로" }));
      renderSearch();

      const chip = await screen.findByRole("button", { name: /센소지/ });
      await user.click(chip);

      expect(
        await screen.findByText("불교사찰 · ★ 4.6 · 도쿄도 다이토구"),
      ).toBeInTheDocument();
    });

    it("지우기를 누르면 사라진다", async () => {
      mockSearch();
      const user = userEvent.setup();
      renderSearch();

      await user.type(
        await screen.findByLabelText("장소 검색"),
        "센소지{Enter}",
      );
      await screen.findByText("센소지");
      renderSearch();

      await user.click(await screen.findByRole("button", { name: /지우기/ }));

      expect(
        await screen.findByText("가고 싶은 곳을 검색해 보세요"),
      ).toBeInTheDocument();
    });
  });

  describe("담기", () => {
    it("날짜를 고르면 googlePlaceId로 일정을 만든다 — 프론트가 장소를 먼저 만들지 않는다", async () => {
      mockSearch();
      const bodies: unknown[] = [];
      server.use(
        http.post(
          `${API_BASE}/travel/trips/:tripId/activities`,
          async ({ request }) => {
            bodies.push(await request.json());
            return HttpResponse.json({ code: "OK", data: { id: 11 } });
          },
        ),
      );
      const user = userEvent.setup();
      renderSearch("/travel/trips/3/places?q=%EC%84%BC%EC%86%8C%EC%A7%80");

      await user.click(await screen.findByRole("button", { name: "담기" }));
      await user.click(await screen.findByRole("button", { name: /2일차/ }));

      await waitFor(() => expect(bodies).toHaveLength(1));
      expect(bodies[0]).toMatchObject({
        title: "센소지",
        activityDate: "2026-10-25",
        googlePlaceId: "ChIJ_senso",
      });
      expect(await screen.findByText("2일차에 담았어요")).toBeInTheDocument();
    });

    it("보관함을 고르면 날짜 없이 담는다 — 갈지는 정했는데 언제인지는 아직일 때", async () => {
      mockSearch();
      const bodies: unknown[] = [];
      server.use(
        http.post(
          `${API_BASE}/travel/trips/:tripId/activities`,
          async ({ request }) => {
            bodies.push(await request.json());
            return HttpResponse.json({ code: "OK", data: { id: 11 } });
          },
        ),
      );
      const user = userEvent.setup();
      renderSearch("/travel/trips/3/places?q=%EC%84%BC%EC%86%8C%EC%A7%80");

      await user.click(await screen.findByRole("button", { name: "담기" }));
      await user.click(await screen.findByRole("button", { name: /보관함/ }));

      await waitFor(() => expect(bodies).toHaveLength(1));
      expect(bodies[0]).toMatchObject({ activityDate: null });
      expect(await screen.findByText("보관함에 담았어요")).toBeInTheDocument();
    });

    it("담기가 실패하면 알려준다", async () => {
      mockSearch();
      server.use(
        http.post(`${API_BASE}/travel/trips/:tripId/activities`, () =>
          HttpResponse.json({ code: "ERR" }, { status: 500 }),
        ),
      );
      const user = userEvent.setup();
      renderSearch("/travel/trips/3/places?q=%EC%84%BC%EC%86%8C%EC%A7%80");

      await user.click(await screen.findByRole("button", { name: "담기" }));
      await user.click(await screen.findByRole("button", { name: /1일차/ }));

      expect(await screen.findByText(/담지 못했어요/)).toBeInTheDocument();
    });
  });

  describe("결과가 없을 때", () => {
    it("직접 입력으로 장소를 만들고 곧바로 날짜를 묻는다", async () => {
      mockSearch([]);
      server.use(
        http.post(`${API_BASE}/travel/places`, () =>
          HttpResponse.json({
            code: "OK",
            data: { id: 7, name: "숙소 근처 골목 카페", manualEntry: true },
          }),
        ),
      );
      const bodies: unknown[] = [];
      server.use(
        http.post(
          `${API_BASE}/travel/trips/:tripId/activities`,
          async ({ request }) => {
            bodies.push(await request.json());
            return HttpResponse.json({ code: "OK", data: { id: 12 } });
          },
        ),
      );
      const user = userEvent.setup();
      renderSearch("/travel/trips/3/places?q=%EA%B3%A8%EB%AA%A9");

      await user.click(
        await screen.findByRole("button", { name: /직접 입력/ }),
      );
      await user.type(
        screen.getByLabelText("장소 이름"),
        "숙소 근처 골목 카페",
      );
      await user.click(screen.getByRole("button", { name: "만들기" }));

      // 장소만 만들고 끝나면 어디에도 보이지 않는다 — 바로 날짜를 물어야 한다.
      await user.click(await screen.findByRole("button", { name: /1일차/ }));

      await waitFor(() => expect(bodies).toHaveLength(1));
      expect(bodies[0]).toMatchObject({
        title: "숙소 근처 골목 카페",
        placeId: 7,
        activityDate: "2026-10-24",
      });
    });
  });

  describe("보드에서 들어오기", () => {
    it("일정 추가 시트의 장소 검색이 이 화면으로 데려온다", async () => {
      const user = userEvent.setup();
      renderWithRouter(
        <Providers>
          <AppRouter />
        </Providers>,
        { initialEntries: ["/travel/trips/3/board"] },
      );

      await user.click(
        await screen.findByRole("button", { name: "일정 추가" }),
      );
      await user.click(
        await screen.findByRole("button", { name: "장소 검색" }),
      );

      expect(await screen.findByLabelText("장소 검색")).toBeInTheDocument();
    });
  });
});
