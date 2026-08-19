import { fireEvent, screen, waitFor, within } from "@testing-library/react";
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

function city(placeId: number, name: string, cityPlaceRef: string) {
  return {
    placeId,
    name,
    timezone: "Asia/Tokyo",
    currency: "JPY",
    countryCode: "JP",
    cityPlaceRef,
    lat: 35.6762,
    lng: 139.6503,
  };
}

const OSAKA = city(21, "오사카", "ChIJ_osaka");
const KYOTO = city(22, "교토", "ChIJ_kyoto");

const DAYS = [
  {
    dayId: 501,
    dayIndex: 1,
    date: "2026-10-24",
    weekday: "토",
    activityCount: 0,
    baseCity: OSAKA,
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
    activityCount: 0,
    baseCity: KYOTO,
    cityChanged: true,
    legIndex: 2,
    cityMemo: null,
    weather: null,
    stayTonight: null,
    stayCheckout: null,
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
    http.get(`${API_BASE}/travel/trips/:tripId/board`, ({ request }) => {
      // 서버처럼 요청한 날짜를 그대로 돌려준다 — 날짜를 무시하면 탭을 옮겨도 같은 날이다.
      const date = new URL(request.url).searchParams.get("date");
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
          selectedDate: date ?? DAYS[0].date,
          archiveCount: 0,
          activities: [],
          moves: [],
          stayMove: null,
        },
      });
    }),
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

    it("할당량에 걸리면 '결과 없음'이 아니라 지금 못 찾는다고 말한다 (#1159)", async () => {
      // "검색 결과가 없어요"를 보면 사용자는 검색어를 계속 바꾼다 — 바꿀 때마다 또 거절된다.
      server.use(
        http.get(`${API_BASE}/travel/places/search`, () =>
          HttpResponse.json({ code: "TRAVEL-ERR-021" }, { status: 503 }),
        ),
      );
      renderSearch("/travel/trips/3/places?q=%EC%84%BC%EC%86%8C%EC%A7%80");

      expect(
        await screen.findByText(/지금은 새 장소를 검색할 수 없어요/),
      ).toBeInTheDocument();
      expect(screen.queryByText("검색 결과가 없어요.")).not.toBeInTheDocument();
      // 직접 입력은 구글을 부르지 않는다 — 검색이 막혀도 일정은 채울 수 있어야 한다.
      expect(
        screen.getByRole("button", { name: /직접 입력/ }),
      ).toBeInTheDocument();
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

      // 고른 순간 담기지 않는다 — 저장을 눌러야 만들어진다.
      expect(await screen.findByRole("dialog")).toBeInTheDocument();
      expect(bodies).toHaveLength(0);

      await user.click(screen.getByRole("combobox", { name: "날짜" }));
      await user.click(await screen.findByRole("option", { name: /2일차/ }));
      await user.click(screen.getByRole("button", { name: "저장" }));

      await waitFor(() => expect(bodies).toHaveLength(1));
      expect(bodies[0]).toMatchObject({
        title: "센소지",
        activityDate: "2026-10-25",
        googlePlaceId: "ChIJ_senso",
      });
      expect(await screen.findByText("2일차에 담았어요")).toBeInTheDocument();
    });

    it("보던 날짜가 미리 골라져 있다 — 3일차를 짜다 들어왔으면 담을 곳도 그 날짜다", async () => {
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
      // 보드가 넘겨주는 형태 그대로다(`?date=`).
      renderSearch(
        "/travel/trips/3/places?q=%EC%84%BC%EC%86%8C%EC%A7%80&date=2026-10-25",
      );

      await user.click(await screen.findByRole("button", { name: "담기" }));
      // 날짜를 고르지 않고 곧바로 저장한다.
      await user.click(await screen.findByRole("button", { name: "저장" }));

      await waitFor(() => expect(bodies).toHaveLength(1));
      expect(bodies[0]).toMatchObject({ activityDate: "2026-10-25" });
    });

    it("시각과 메모를 담는 김에 같이 적는다 — 담고 나서 다시 찾아 열지 않게", async () => {
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
      renderSearch(
        "/travel/trips/3/places?q=%EC%84%BC%EC%86%8C%EC%A7%80&date=2026-10-24",
      );

      await user.click(await screen.findByRole("button", { name: "담기" }));
      const title = await screen.findByLabelText("일정 제목");
      await user.clear(title);
      await user.type(title, "센소지 야경");
      fireEvent.change(screen.getByLabelText("시각 (선택)"), {
        target: { value: "18:30" },
      });
      await user.type(screen.getByLabelText("메모 (선택)"), "나카미세 통과");
      await user.click(screen.getByRole("button", { name: "저장" }));

      await waitFor(() => expect(bodies).toHaveLength(1));
      expect(bodies[0]).toMatchObject({
        title: "센소지 야경",
        activityDate: "2026-10-24",
        startTime: "18:30",
        memo: "나카미세 통과",
      });
    });

    it("여행에 없는 날짜가 넘어오면 무시하고 보관함으로 둔다 — URL을 그대로 믿지 않는다", async () => {
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
      renderSearch(
        "/travel/trips/3/places?q=%EC%84%BC%EC%86%8C%EC%A7%80&date=1999-01-01",
      );

      await user.click(await screen.findByRole("button", { name: "담기" }));
      await user.click(await screen.findByRole("button", { name: "저장" }));

      await waitFor(() => expect(bodies).toHaveLength(1));
      expect(bodies[0]).toMatchObject({ activityDate: null });
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
      await user.click(await screen.findByRole("combobox", { name: "날짜" }));
      await user.click(await screen.findByRole("option", { name: /보관함/ }));
      await user.click(screen.getByRole("button", { name: "저장" }));

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
      await user.click(await screen.findByRole("combobox", { name: "날짜" }));
      await user.click(await screen.findByRole("option", { name: /1일차/ }));
      await user.click(screen.getByRole("button", { name: "저장" }));

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

      // 장소만 만들고 끝나면 어디에도 보이지 않는다 — 바로 담기 시트를 열어야 한다.
      await user.click(await screen.findByRole("combobox", { name: "날짜" }));
      await user.click(await screen.findByRole("option", { name: /1일차/ }));
      await user.click(screen.getByRole("button", { name: "저장" }));

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

    it("보던 날짜를 들고 와 담기 시트에 미리 골라 둔다", async () => {
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
      // 2일차를 보다가 검색으로 들어간다.
      renderWithRouter(
        <Providers>
          <AppRouter />
        </Providers>,
        { initialEntries: ["/travel/trips/3/board?day=1"] },
      );

      await user.click(
        await screen.findByRole("button", { name: "장소 검색" }),
      );
      await user.type(await screen.findByLabelText("장소 검색"), "센소지");
      await user.keyboard("{Enter}");

      await user.click(await screen.findByRole("button", { name: "담기" }));
      await user.click(await screen.findByRole("button", { name: "저장" }));

      await waitFor(() => expect(bodies).toHaveLength(1));
      expect(bodies[0]).toMatchObject({ activityDate: "2026-10-25" });
    });

    it("보던 날짜의 도시를 들고 온다 — 이 화면의 기본 조회로는 알 수 없는 값이다", async () => {
      mockSearch();
      const user = userEvent.setup();
      // 2일차(교토)를 보는 중. 검색 화면의 기본 보드 조회는 1일차(오사카)를 돌려주므로,
      // 들고 오지 않으면 교토에서 검색했는데 오사카로 편향되는 그 상태가 된다.
      renderWithRouter(
        <Providers>
          <AppRouter />
        </Providers>,
        { initialEntries: ["/travel/trips/3/board?day=1"] },
      );

      await user.click(
        await screen.findByRole("button", { name: "장소 검색" }),
      );

      expect(
        await screen.findByRole("button", { name: "검색 기준 도시 교토" }),
      ).toBeInTheDocument();
    });
  });

  describe("검색 기준 도시 (§2.7)", () => {
    it("보던 날짜의 도시가 기준이 되고, placeholder도 따라간다", async () => {
      const calls = mockSearch();
      const user = userEvent.setup();
      // 서버가 고른 날짜(1일차)는 오사카다.
      renderSearch();

      expect(
        await screen.findByRole("button", { name: "검색 기준 도시 오사카" }),
      ).toBeInTheDocument();
      const input = screen.getByLabelText("장소 검색");
      expect(input).toHaveAttribute("placeholder", "오사카 주변 장소 검색");

      await user.type(input, "라멘{Enter}");

      await waitFor(() => expect(calls).toHaveLength(1));
      expect(calls[0].searchParams.get("city")).toBe("21");
    });

    it("`?city=`가 있으면 그 도시로 연다 — 보드에서 들고 온 값이다", async () => {
      mockSearch();
      renderSearch("/travel/trips/3/places?city=22");

      expect(
        await screen.findByRole("button", { name: "검색 기준 도시 교토" }),
      ).toBeInTheDocument();
    });

    it("칩으로 도시를 바꾸면 그 자리에서 다시 검색하고 검색어는 남는다", async () => {
      const calls = mockSearch();
      const user = userEvent.setup();
      renderSearch("/travel/trips/3/places?q=라멘");

      await waitFor(() => expect(calls).toHaveLength(1));
      expect(calls[0].searchParams.get("city")).toBe("21");

      await user.click(
        screen.getByRole("button", { name: "검색 기준 도시 오사카" }),
      );
      const sheet = await screen.findByRole("dialog");
      await user.click(within(sheet).getByRole("button", { name: "교토" }));

      // 도시가 바뀌면 다시 부른다 — 편향이 다르면 다른 결과다.
      await waitFor(() => expect(calls).toHaveLength(2));
      expect(calls[1].searchParams.get("city")).toBe("22");
      // 검색어를 지우지 않는다 — 한 URL에 같이 산다.
      expect(calls[1].searchParams.get("q")).toBe("라멘");
      expect(screen.getByLabelText("장소 검색")).toHaveValue("라멘");
    });

    it("담을 때 기준 도시를 함께 보낸다 — 그래야 보관함에서 그 도시로 묶인다", async () => {
      const created: Record<string, unknown>[] = [];
      server.use(
        http.post(
          `${API_BASE}/travel/trips/:tripId/activities`,
          async ({ request }) => {
            created.push((await request.json()) as Record<string, unknown>);
            return HttpResponse.json({ code: "OK", data: { id: 1 } });
          },
        ),
      );
      mockSearch();
      const user = userEvent.setup();
      renderSearch("/travel/trips/3/places?q=센소지&city=22");

      await user.click(await screen.findByRole("button", { name: "담기" }));
      await user.click(await screen.findByRole("combobox", { name: "날짜" }));
      await user.click(await screen.findByRole("option", { name: /1일차/ }));
      await user.click(screen.getByRole("button", { name: "저장" }));

      await waitFor(() => expect(created).toHaveLength(1));
      expect(created[0].googlePlaceId).toBe("ChIJ_senso");
      expect(created[0].cityPlaceId).toBe(22);
    });

    it("담기 시트는 기준 도시인 날짜를 위로 올린다 (#1134)", async () => {
      mockSearch();
      const user = userEvent.setup();
      // 교토 기준이면 교토 날짜(2일차)가 먼저 나와야 한다.
      renderSearch("/travel/trips/3/places?q=센소지&city=22");

      await user.click(await screen.findByRole("button", { name: "담기" }));
      await user.click(await screen.findByRole("combobox", { name: "날짜" }));

      const options = (await screen.findAllByRole("option"))
        .map((option) => option.textContent ?? "")
        .filter((text) => text.includes("일차"));
      expect(options[0]).toContain("2일차");
    });
  });
});
