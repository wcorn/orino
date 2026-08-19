import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { act } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import { usePendingActions } from "@/features/travel/board/pendingActions";
import { useToastStore } from "@/shared/lib/toast";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

const API_BASE = "https://api.orino.dev/api";

function baseCity(
  placeId: number,
  name: string,
  timezone: string,
  currency = "JPY",
) {
  return {
    placeId,
    name,
    timezone,
    currency,
    countryCode: "JP",
    cityPlaceRef: null,
    lat: null,
    lng: null,
  };
}

const TOKYO = baseCity(21, "도쿄", "Asia/Tokyo");

function day(
  dayIndex: number,
  date: string,
  weekday: string,
  activityCount: number,
  overrides: Record<string, unknown> = {},
) {
  return {
    dayId: 500 + dayIndex,
    dayIndex,
    date,
    weekday,
    activityCount,
    baseCity: TOKYO,
    cityChanged: false,
    legIndex: 1,
    cityMemo: null,
    weather: null,
    stayTonight: null,
    stayCheckout: null,
    ...overrides,
  };
}

const DAYS = [
  day(1, "2026-10-24", "토", 2),
  day(2, "2026-10-25", "일", 0),
  day(3, "2026-10-26", "월", 1),
];

const TRIP = {
  id: 3,
  title: "도쿄 3박 4일",
  startDate: "2026-10-24",
  endDate: "2026-10-26",
  status: "UPCOMING",
  recordMode: false,
  cityCount: 1,
  countryCount: 1,
  singleCity: true,
};

function activity(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    tripId: 3,
    title: "센소지",
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
    canDepartureNotify: true,
    ...overrides,
  };
}

/**
 * 날짜별 보드를 흉내낸다. 서버처럼 `date`·`archive` 파라미터로 갈라 응답한다.
 * `selectedDate`는 요청한 날짜(생략 시 1일차)를 그대로 돌려준다.
 */
function mockBoard(options: {
  byDate?: Record<string, unknown[]>;
  archive?: unknown[];
  trip?: Record<string, unknown>;
  moves?: unknown[];
  days?: unknown[];
  stayMove?: unknown;
  /** `GET /trips/{id}/stays` 응답. 상세 시트·겹침 미리보기가 이 목록을 읽는다. */
  stays?: unknown[];
  /** 그 날짜의 응답을 붙잡아 둔다. 응답 전 화면을 확인할 때 쓴다. */
  hold?: (date: string) => Promise<void> | null;
}) {
  const byDate = options.byDate ?? {};
  const archive = options.archive ?? [];
  server.use(
    http.get(`${API_BASE}/travel/trips/:tripId/board`, async ({ request }) => {
      const url = new URL(request.url);
      const isArchive = url.searchParams.get("archive") === "true";
      const date = url.searchParams.get("date") ?? DAYS[0].date;
      await options.hold?.(date);
      return HttpResponse.json({
        code: "OK",
        data: {
          trip: { ...TRIP, ...options.trip },
          days: options.days ?? DAYS,
          selectedDate: isArchive ? null : date,
          archiveCount: archive.length,
          activities: isArchive ? archive : (byDate[date] ?? []),
          moves: isArchive ? [] : (options.moves ?? []),
          stayMove: isArchive ? null : (options.stayMove ?? null),
        },
      });
    }),
    http.get(`${API_BASE}/travel/trips/:tripId/stays`, () =>
      HttpResponse.json({ code: "OK", data: options.stays ?? [] }),
    ),
  );
}

/**
 * 도시 구간 바의 칸들. 바는 `aria-hidden`이라(도시는 날짜 칸의 이름이 이미 말한다)
 * 역할로는 못 잡는다 — 주(週)별 묶음 그대로 돌려준다.
 */
function cityBandCells(): HTMLElement[] {
  return [...document.querySelectorAll<HTMLElement>("[data-city-band] > span")];
}

/** 주별 도시명 묶음 — `[["도쿄"], ["닛코", "도쿄"]]`. */
function cityBands(): string[][] {
  return [...document.querySelectorAll("[data-city-band]")].map((row) =>
    [...row.children].map((cell) => cell.textContent ?? ""),
  );
}

/** 날짜 칸을 450ms 길게 눌러 기준 도시 시트를 연다. 손가락이 하는 일 그대로다. */
async function openCitySheet(tabIndex: number) {
  const tab = screen.getAllByRole("tab")[tabIndex];
  fireEvent.pointerDown(tab, { clientX: 10, clientY: 10 });
  await screen.findByRole("dialog", undefined, { timeout: 2000 });
  fireEvent.pointerUp(tab);
}

function renderBoard(path = "/travel/trips/3/board") {
  return renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: [path] },
  );
}

describe("TripBoardPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
    // 스낵바는 모듈 스토어라 테스트 사이에 남는다. 이전 실행취소 스낵바가 섞이면
    // 다음 테스트가 엉뚱한 버튼을 누른다.
    useToastStore.setState({ toasts: [] });
    // 보류함도 모듈 스토어라 테스트 사이에 남는다.
    usePendingActions.setState({ pendingIds: [], commits: new Map() });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("날짜 달력", () => {
    it("기간의 모든 날짜를 달력 칸으로 깔고 보관함은 그 아래 따로 선다", async () => {
      mockBoard({
        byDate: { "2026-10-24": [activity()] },
        archive: [activity({ id: 9 })],
      });

      renderBoard();

      const tabs = await screen.findAllByRole("tab");
      expect(tabs).toHaveLength(4);
      // 칸에는 일(日)만 남는다 — 월은 헤더가 말한다.
      expect(tabs[0]).toHaveTextContent("24");
      // 칸 글자가 숫자뿐이라 나머지는 이름으로 읽힌다.
      expect(tabs[0]).toHaveAccessibleName("10.24 토 · 도쿄 · 일정 2개");
      // 보관함은 날짜가 아니라 "아직 날짜를 못 정한 것"이라 마지막이다.
      expect(tabs[3]).toHaveTextContent("보관함");
      expect(tabs[3]).toHaveTextContent("1개");
    });

    it("선택한 날짜는 스크롤과 무관하게 헤더에 남는다 — 이게 달력으로 바꾼 이유다", async () => {
      mockBoard({ byDate: { "2026-10-24": [activity()] } });

      renderBoard();

      expect(await screen.findByText("10.24")).toBeInTheDocument();
      expect(screen.getByText("(토)")).toBeInTheDocument();
    });

    it("헤더의 화살표로 하루씩 움직인다 — 양 끝에서는 눌리지 않는다", async () => {
      mockBoard({
        byDate: {
          "2026-10-24": [activity()],
          "2026-10-25": [
            activity({ id: 5, title: "디즈니씨", activityDate: "2026-10-25" }),
          ],
        },
      });

      renderBoard();
      await screen.findByText("센소지");

      // 첫날이라 이전은 없다.
      expect(screen.getByRole("button", { name: "이전 날짜" })).toBeDisabled();

      await userEvent.click(screen.getByRole("button", { name: "다음 날짜" }));

      expect(await screen.findByText("디즈니씨")).toBeInTheDocument();
      expect(screen.getByText("10.25")).toBeInTheDocument();
    });

    it("날짜를 옮기는 동안 달력과 헤더가 그대로 있다 — 새로고침처럼 보이지 않게", async () => {
      // 새 날짜 응답을 붙잡아 둔다. 그 사이 화면이 어떻게 보이는지가 이 테스트의 전부다.
      let release = () => {};
      const held = new Promise<void>((resolve) => {
        release = () => resolve();
      });
      mockBoard({
        byDate: { "2026-10-24": [activity()], "2026-10-25": [] },
        hold: (date) => (date === "2026-10-25" ? held : null),
      });

      renderBoard();
      await screen.findByText("센소지");

      await userEvent.click(screen.getByRole("button", { name: "다음 날짜" }));

      // 응답을 기다리는 동안 — 달력·헤더는 그대로다(전체 로딩으로 갈아치우지 않는다).
      expect(screen.getAllByRole("tab").length).toBeGreaterThan(1);
      expect(screen.getByText("도쿄 3박 4일")).toBeInTheDocument();
      // 선택 표시는 응답을 기다리지 않는다 — 누른 칸이 곧바로 켜진다.
      expect(screen.getByRole("tab", { selected: true })).toHaveTextContent(
        "25",
      );
      // 앞 날짜의 일정을 그 날의 것처럼 남겨두지도 않는다. 기다리는 건 목록뿐이다.
      expect(screen.queryByText("센소지")).not.toBeInTheDocument();
      expect(screen.getByText("불러오는 중…")).toBeInTheDocument();

      release();
      // 응답이 오면 그 날짜의 빈 상태로 바뀐다.
      expect(await screen.findByText("일정이 없어요")).toBeInTheDocument();
    });

    it("달력을 접어도 선택일은 남는다 — 긴 여행에서 자리를 돌려준다", async () => {
      mockBoard({ byDate: { "2026-10-24": [activity()] } });

      renderBoard();
      await screen.findAllByRole("tab");

      await userEvent.click(screen.getByRole("button", { name: "달력 접기" }));

      // 날짜 칸은 사라지고 보관함만 남는다.
      expect(screen.getAllByRole("tab")).toHaveLength(1);
      expect(screen.getByText("10.24")).toBeInTheDocument();
    });

    it("서버가 고른 날짜가 기본 선택된다", async () => {
      mockBoard({ byDate: { "2026-10-24": [activity()] } });

      renderBoard();

      const tabs = await screen.findAllByRole("tab");
      await waitFor(() => {
        expect(tabs[0]).toHaveAttribute("aria-selected", "true");
      });
    });

    it("날짜 칸을 누르면 URL에 ?day= 가 남고 그 날짜를 조회한다", async () => {
      mockBoard({
        byDate: {
          "2026-10-24": [activity()],
          "2026-10-26": [
            activity({ id: 5, title: "디즈니씨", activityDate: "2026-10-26" }),
          ],
        },
      });

      renderBoard();
      await screen.findByText("센소지");

      await userEvent.click(screen.getByRole("tab", { name: /10\.26/ }));

      expect(await screen.findByText("디즈니씨")).toBeInTheDocument();
      expect(screen.queryByText("센소지")).toBeNull();
    });

    it("?day= 로 직접 들어오면 그 날짜가 열린다", async () => {
      mockBoard({
        byDate: {
          "2026-10-24": [activity()],
          "2026-10-26": [
            activity({ id: 5, title: "디즈니씨", activityDate: "2026-10-26" }),
          ],
        },
      });

      renderBoard("/travel/trips/3/board?day=2");

      expect(await screen.findByText("디즈니씨")).toBeInTheDocument();
      const tabs = await screen.findAllByRole("tab");
      await waitFor(() => {
        expect(tabs[2]).toHaveAttribute("aria-selected", "true");
      });
    });

    it("?day=archive 로 들어오면 보관함이 열린다", async () => {
      mockBoard({
        byDate: { "2026-10-24": [activity()] },
        archive: [
          activity({ id: 9, title: "가고 싶은 라멘집", activityDate: null }),
        ],
      });

      renderBoard("/travel/trips/3/board?day=archive");

      expect(await screen.findByText("가고 싶은 라멘집")).toBeInTheDocument();
      const tabs = await screen.findAllByRole("tab");
      expect(tabs[3]).toHaveAttribute("aria-selected", "true");
    });
  });

  describe("보관함 — 도시별 그룹 (v2.1)", () => {
    const OSAKA = baseCity(21, "오사카", "Asia/Tokyo");
    const KYOTO = baseCity(22, "교토", "Asia/Tokyo");
    const TWO_CITY_DAYS = [
      day(1, "2026-10-24", "토", 0, {
        baseCity: { ...OSAKA, cityPlaceRef: "ChIJ_osaka" },
      }),
      day(2, "2026-10-25", "일", 0, {
        baseCity: { ...KYOTO, cityPlaceRef: "ChIJ_kyoto" },
        cityChanged: true,
        legIndex: 2,
      }),
    ];

    /** 도시 식별자를 가진 장소가 붙은 보관함 일정. */
    function archived(id: number, title: string, city: string | null) {
      return activity({
        id,
        title,
        activityDate: null,
        place: {
          id: 100 + id,
          name: title,
          address: null,
          lat: null,
          lng: null,
          cityName: city === "ChIJ_kyoto" ? "교토" : "오사카",
          cityPlaceRef: city,
        },
      });
    }

    function mockArchive(archive: unknown[]) {
      mockBoard({
        byDate: { "2026-10-24": [], "2026-10-25": [] },
        trip: { singleCity: false, cityCount: 2 },
        days: TWO_CITY_DAYS,
        archive,
      });
    }

    it("도시별로 묶어 구간 순서대로 보여주고, 모르는 도시는 맨 뒤로 보낸다", async () => {
      mockArchive([
        archived(9, "니시키 시장", "ChIJ_kyoto"),
        archived(10, "이름만 아는 곳", null),
        archived(11, "구로몬 시장", "ChIJ_osaka"),
      ]);

      renderBoard("/travel/trips/3/board?day=archive");

      await screen.findAllByText("구로몬 시장");
      const headings = screen.getAllByRole("heading", { level: 2 });
      expect(headings.map((h) => h.textContent)).toEqual([
        "오사카",
        "교토",
        "도시 없음",
      ]);
    });

    it("담기 버튼을 누르면 그 장소의 도시 날짜가 목록 위에 온다", async () => {
      mockArchive([archived(9, "니시키 시장", "ChIJ_kyoto")]);

      renderBoard("/travel/trips/3/board?day=archive");
      await userEvent.click(
        await screen.findByRole("button", { name: "니시키 시장 날짜에 담기" }),
      );

      const sheet = await screen.findByRole("dialog");
      const options = within(sheet).getAllByRole("button");
      // 교토 장소라 교토 날짜(2일차)가 오사카 날짜보다 위에 있다.
      expect(options[0]).toHaveTextContent("2일차 · 교토");
      expect(options[1]).toHaveTextContent("1일차 · 오사카");
    });

    it("고른 날짜로 옮긴다", async () => {
      const seen: Record<string, unknown>[] = [];
      mockArchive([archived(9, "니시키 시장", "ChIJ_kyoto")]);
      server.use(
        http.put(`${API_BASE}/travel/activities/:id`, async ({ request }) => {
          seen.push((await request.json()) as Record<string, unknown>);
          return HttpResponse.json({ code: "OK", data: activity() });
        }),
      );

      renderBoard("/travel/trips/3/board?day=archive");
      await userEvent.click(
        await screen.findByRole("button", { name: "니시키 시장 날짜에 담기" }),
      );
      const sheet = await screen.findByRole("dialog");
      await userEvent.click(
        within(sheet).getByRole("button", { name: /2일차 · 교토/ }),
      );

      await waitFor(() => expect(seen).toHaveLength(1));
      // 날짜만 바꾸는 수정이지만 `PUT`은 전체 교체다 — 장소를 빼면 서버가 지운다(#1197).
      expect(seen[0]).toMatchObject({
        activityDate: "2026-10-25",
        placeId: 109,
      });
    });

    it("빈 보관함 문구는 그대로다", async () => {
      mockArchive([]);

      renderBoard("/travel/trips/3/board?day=archive");

      expect(
        await screen.findByText("가고 싶은 곳을 미리 담아두세요"),
      ).toBeInTheDocument();
    });
  });

  describe("달력이 도시를 말한다 (v2.1)", () => {
    /** 도쿄 → 닛코 → 도쿄. 하루만 다른 도시라 구간이 셋으로 쪼개진 여행이다. */
    const NIKKO = baseCity(22, "닛코", "Asia/Tokyo");
    const MULTI_DAYS = [
      day(1, "2026-10-24", "토", 0),
      day(2, "2026-10-25", "일", 0, {
        baseCity: NIKKO,
        cityChanged: true,
        legIndex: 2,
      }),
      day(3, "2026-10-26", "월", 0, { cityChanged: true, legIndex: 3 }),
    ];

    function mockMultiCity(overrides: Record<string, unknown> = {}) {
      mockBoard({
        byDate: { "2026-10-24": [], "2026-10-25": [], "2026-10-26": [] },
        trip: { singleCity: false, cityCount: 2, ...overrides },
        days: MULTI_DAYS,
      });
    }

    it("도시는 날짜 아래 구간 바가 말한다 — 칸마다 반복하지 않는다", async () => {
      mockMultiCity();

      renderBoard();
      await screen.findAllByRole("tab");

      // 10.24(토)는 첫 주 마지막 칸이라 주가 갈린다. 둘째 주에 닛코·도쿄가 나란히 선다.
      expect(cityBands()).toEqual([["도쿄"], ["닛코", "도쿄"]]);
    });

    it("연속으로 같은 도시인 날들은 바 하나로 묶인다 — 경계가 곧 이동일이다", async () => {
      mockBoard({
        byDate: {
          "2026-10-25": [],
          "2026-10-26": [],
          "2026-10-27": [],
          "2026-10-28": [],
        },
        trip: { singleCity: false, cityCount: 2 },
        days: [
          day(1, "2026-10-25", "일", 0),
          day(2, "2026-10-26", "월", 0),
          day(3, "2026-10-27", "화", 0, {
            baseCity: NIKKO,
            cityChanged: true,
            legIndex: 2,
          }),
          day(4, "2026-10-28", "수", 0, { baseCity: NIKKO, legIndex: 2 }),
        ],
      });

      renderBoard();
      await screen.findAllByRole("tab");

      // 한 주 안에 도쿄 2일 + 닛코 2일 — 이름이 네 번이 아니라 두 번 나온다.
      expect(cityBands()).toEqual([["도쿄", "닛코"]]);
      const [tokyo, nikko] = cityBandCells();
      expect(tokyo.style.gridColumn).toBe("1 / span 2");
      expect(nikko.style.gridColumn).toBe("3 / span 2");
    });

    /**
     * 도시가 바뀌는 날은 그 하루가 두 도시에 속한다(D-25). 구간 바는 "여기서 옮긴다"만
     * 말하고 <b>어디서 오는지는 말하지 않는다</b> — 그날 오전 일정이 왜 거기 있는지가
     * 헤더에서 읽혀야 한다.
     */
    it("도시가 바뀌는 날은 헤더가 `도쿄 → 닛코`를 쓴다", async () => {
      mockBoard({
        byDate: { "2026-10-24": [], "2026-10-25": [], "2026-10-26": [] },
        trip: { singleCity: false, cityCount: 2 },
        days: [
          day(1, "2026-10-24", "토", 0),
          day(2, "2026-10-25", "일", 0, {
            baseCity: NIKKO,
            cityChanged: true,
            arrivingFrom: TOKYO,
            legIndex: 2,
          }),
          day(3, "2026-10-26", "월", 0, {
            cityChanged: true,
            arrivingFrom: NIKKO,
            legIndex: 3,
          }),
        ],
      });

      // `?day=`는 날짜가 아니라 일차 인덱스다 — 1이 둘째 날(이동일)이다.
      renderBoard("/travel/trips/3/board?day=1");
      await screen.findAllByRole("tab");

      expect(await screen.findByText("· 도쿄 → 닛코")).toBeInTheDocument();
    });

    it("첫날에는 떠나온 도시가 없다 — 비교할 앞 날짜가 없다", async () => {
      mockMultiCity();

      renderBoard();

      expect(await screen.findByText("· 도쿄")).toBeInTheDocument();
      expect(screen.queryByText(/→/)).not.toBeInTheDocument();
    });

    /**
     * 아침에 뭘 입을지는 <b>오전을 보낼 도시</b>가 정한다 — 오사카에 비가 오면 교토가
     * 맑아도 우산을 든다. 탭은 도착 도시의 날씨만 보여주므로 그 값이 여기 있어야 한다.
     */
    it("이동일에는 두 도시와 두 날씨가 한 줄로 선다", async () => {
      mockBoard({
        byDate: { "2026-10-24": [], "2026-10-25": [] },
        trip: { singleCity: false, cityCount: 2 },
        days: [
          day(1, "2026-10-24", "토", 0),
          day(2, "2026-10-25", "일", 0, {
            baseCity: NIKKO,
            cityChanged: true,
            arrivingFrom: TOKYO,
            legIndex: 2,
            weather: {
              date: "2026-10-25",
              cityName: "닛코",
              icon: "CLOUD",
              tempMax: 16,
              tempMin: 9,
              precipProbability: 30,
            },
            arrivingFromWeather: {
              date: "2026-10-25",
              cityName: "도쿄",
              icon: "RAIN",
              tempMax: 18,
              tempMin: 11,
              precipProbability: 70,
            },
          }),
        ],
      });

      // `?day=`는 날짜가 아니라 탭 인덱스다 — 1이 둘째 날(이동일)이다.
      renderBoard("/travel/trips/3/board?day=1");
      await screen.findAllByRole("tab");

      const line = await screen.findByLabelText("도쿄에서 닛코로 이동하는 날");
      // 떠나온 도시가 먼저다 — 그날은 그 도시에서 시작한다.
      expect(line).toHaveTextContent("도쿄18°/11°");
      expect(line).toHaveTextContent("닛코16°/9°");
    });

    it("도시가 안 바뀌는 날에는 그 줄이 없다 — 빈 자리를 남기지 않는다", async () => {
      mockMultiCity();

      renderBoard();
      await screen.findAllByRole("tab");

      expect(screen.queryByLabelText(/이동하는 날$/)).not.toBeInTheDocument();
    });

    /**
     * 오프라인 캐시(Workbox)에는 이 필드가 생기기 전 응답이 남아 있을 수 있다. 없다고
     * 화면이 죽으면 비행기 모드에서 보드가 통째로 사라진다.
     */
    it("떠나온 도시가 없는 응답이면 도착 도시만 쓴다", async () => {
      mockMultiCity();

      renderBoard("/travel/trips/3/board?day=1");
      await screen.findAllByRole("tab");

      expect(await screen.findByText("· 닛코")).toBeInTheDocument();
      expect(screen.queryByText(/→/)).not.toBeInTheDocument();
    });

    it("전 기간 한 도시면 도시명을 화면에서 뺀다 — 반복은 정보가 아니다", async () => {
      mockBoard({ byDate: { "2026-10-24": [] } });

      renderBoard();
      await screen.findAllByRole("tab");

      // 헤더에도 구간 바에도 도시가 없다. 도시를 아는 건 제목 아래 현지 시계뿐이다.
      expect(screen.queryByText("· 도쿄")).not.toBeInTheDocument();
      expect(cityBands()).toEqual([]);
    });

    it("같은 도시는 어느 날짜를 봐도 같은 색이다 — 색이 구간을 잇는다", async () => {
      mockMultiCity();

      renderBoard();
      await screen.findAllByRole("tab");

      const [tokyoWeek1] = cityBandCells();
      const [nikko, tokyoWeek2] = cityBandCells().slice(1);
      expect(tokyoWeek2.style.background).toBe(tokyoWeek1.style.background);
      expect(nikko.style.background).not.toBe(tokyoWeek1.style.background);
    });

    it("450ms 눌러야 기준 도시 시트가 열린다 — 400ms는 아직 아니다", async () => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      mockMultiCity();

      renderBoard();
      const tab = (await screen.findAllByRole("tab"))[1];

      // 일정 행의 드래그 진입(400ms)과 같은 길이로는 열리지 않는다.
      await act(async () => {
        fireEvent.pointerDown(tab, { clientX: 10, clientY: 10 });
        vi.advanceTimersByTime(420);
      });
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

      await act(async () => {
        vi.advanceTimersByTime(60);
      });
      expect(screen.getByRole("dialog")).toBeInTheDocument();
      expect(screen.getByText(/2일차 10.25 · 지금은 닛코/)).toBeInTheDocument();
      vi.useRealTimers();
    });

    it("이 여행의 도시를 고르면 그 날짜만 바꾸는 요청이 나간다", async () => {
      const seen: { dayId: string; body: Record<string, unknown> }[] = [];
      mockMultiCity();
      server.use(
        http.put(
          `${API_BASE}/travel/days/:dayId`,
          async ({ params, request }) => {
            seen.push({
              dayId: String(params.dayId),
              body: (await request.json()) as Record<string, unknown>,
            });
            return HttpResponse.json({ code: "OK", data: [] });
          },
        ),
      );

      renderBoard();
      await screen.findAllByRole("tab");
      await openCitySheet(1);

      const sheet = screen.getByRole("dialog");
      await userEvent.click(
        within(sheet).getByRole("button", { name: "도쿄" }),
      );
      await userEvent.click(
        within(sheet).getByRole("button", { name: "저장" }),
      );

      await waitFor(() => expect(seen).toHaveLength(1));
      // 2일차의 dayId. 보고 있던 날짜가 아니라 길게 누른 날짜다.
      expect(seen[0].dayId).toBe("502");
      expect(seen[0].body).toEqual({ baseCityPlaceId: 21 });
    });

    it("검색으로 고른 도시는 고른 그대로 보낸다 — 서버가 담으며 식별자를 붙인다", async () => {
      const seen: Record<string, unknown>[] = [];
      mockMultiCity();
      server.use(
        http.get(`${API_BASE}/travel/places/cities`, () =>
          HttpResponse.json({
            code: "OK",
            data: [
              {
                googlePlaceId: "ChIJ_kyoto",
                name: "교토",
                address: "일본 교토부",
                lat: 35.0116,
                lng: 135.7681,
                timezone: "Asia/Tokyo",
                currency: "JPY",
              },
            ],
          }),
        ),
        http.put(`${API_BASE}/travel/days/:dayId`, async ({ request }) => {
          seen.push((await request.json()) as Record<string, unknown>);
          return HttpResponse.json({ code: "OK", data: [] });
        }),
      );

      renderBoard();
      await screen.findAllByRole("tab");
      await openCitySheet(1);

      const sheet = screen.getByRole("dialog");
      await userEvent.type(within(sheet).getByLabelText("도시 검색"), "교토");
      await userEvent.click(
        within(sheet).getByRole("button", { name: "검색" }),
      );
      await userEvent.click(
        await within(sheet).findByRole("button", { name: /교토/ }),
      );
      await userEvent.click(
        within(sheet).getByRole("button", { name: "저장" }),
      );

      await waitFor(() => expect(seen).toHaveLength(1));
      expect(seen[0]).toEqual({ baseCityGooglePlaceId: "ChIJ_kyoto" });
    });

    it("도시 메모는 있을 때만 달력 아래 한 줄로 보인다", async () => {
      mockBoard({
        byDate: { "2026-10-24": [], "2026-10-25": [] },
        days: [
          day(1, "2026-10-24", "토", 0, { cityMemo: "코인로커에 짐 보관" }),
          day(2, "2026-10-25", "일", 0),
        ],
      });

      renderBoard();

      expect(await screen.findByText("코인로커에 짐 보관")).toBeInTheDocument();

      await userEvent.click(screen.getByRole("tab", { name: /10\.25/ }));

      await waitFor(() => {
        expect(
          screen.queryByText("코인로커에 짐 보관"),
        ).not.toBeInTheDocument();
      });
    });

    it("메모만 고치면 도시는 건드리지 않는다", async () => {
      const seen: Record<string, unknown>[] = [];
      mockBoard({ byDate: { "2026-10-24": [] } });
      server.use(
        http.put(`${API_BASE}/travel/days/:dayId`, async ({ request }) => {
          seen.push((await request.json()) as Record<string, unknown>);
          return HttpResponse.json({ code: "OK", data: [] });
        }),
      );

      renderBoard();
      await screen.findAllByRole("tab");
      await openCitySheet(0);

      const sheet = screen.getByRole("dialog");
      await userEvent.type(
        within(sheet).getByLabelText("도시 메모"),
        "코인로커",
      );
      await userEvent.click(
        within(sheet).getByRole("button", { name: "저장" }),
      );

      await waitFor(() => expect(seen).toHaveLength(1));
      expect(seen[0]).toEqual({ cityMemo: "코인로커" });
    });

    it("메뉴로도 같은 시트를 연다 — 롱프레스는 손가락의 길일 뿐이다", async () => {
      mockMultiCity();

      renderBoard();
      await screen.findAllByRole("tab");

      await userEvent.click(screen.getByRole("button", { name: "여행 메뉴" }));
      await userEvent.click(
        await screen.findByRole("menuitem", { name: "기준 도시 변경" }),
      );

      expect(await screen.findByRole("dialog")).toHaveTextContent(
        "1일차 10.24 · 지금은 도쿄",
      );
    });
  });

  describe("일정 행", () => {
    it("그날 도시가 아닌 장소면 도시명을 덧붙인다 — 막지는 않는다", async () => {
      mockBoard({
        byDate: {
          "2026-10-24": [
            activity({
              title: "구로몬 시장",
              outOfBaseCity: true,
              place: {
                id: 7,
                name: "구로몬 시장",
                address: null,
                lat: null,
                lng: null,
                cityName: "오사카",
                cityPlaceRef: "ChIJ_osaka",
              },
            }),
          ],
        },
      });

      renderBoard();

      expect(await screen.findByText("· 오사카")).toBeInTheDocument();
      // 경고일 뿐 일정은 그대로 있다(제목 + 장소명).
      expect(screen.getAllByText("구로몬 시장")).toHaveLength(2);
    });

    it("같은 도시의 장소에는 아무것도 붙이지 않는다", async () => {
      mockBoard({
        byDate: {
          "2026-10-24": [
            activity({
              place: {
                id: 8,
                name: "센소지",
                address: null,
                lat: null,
                lng: null,
                cityName: "도쿄",
                cityPlaceRef: "ChIJ_tokyo",
              },
            }),
          ],
        },
      });

      renderBoard();

      await screen.findAllByText("센소지");
      expect(screen.queryByText("· 도쿄")).not.toBeInTheDocument();
    });

    it("시각을 tabular-nums로 보여주고, 없으면 ── 로 자리를 지킨다", async () => {
      mockBoard({
        byDate: {
          "2026-10-24": [
            activity(),
            activity({
              id: 2,
              title: "동네 산책",
              startTime: null,
              sortOrder: 1,
            }),
          ],
        },
      });

      renderBoard();

      expect(await screen.findByText("09:00")).toBeInTheDocument();
      expect(screen.getByText("──")).toBeInTheDocument();
    });

    it("장소가 있으면 아래 줄에 장소명을 보여준다", async () => {
      mockBoard({
        byDate: {
          "2026-10-24": [
            activity({
              place: {
                id: 1,
                name: "시부야 스카이",
                address: null,
                lat: null,
                lng: null,
              },
            }),
          ],
        },
      });

      renderBoard();

      expect(await screen.findByText("시부야 스카이")).toBeInTheDocument();
    });

    it("알림이 켜진 일정에 종 아이콘을 붙인다", async () => {
      mockBoard({
        byDate: { "2026-10-24": [activity({ notifyEnabled: true })] },
      });

      renderBoard();

      expect(await screen.findByLabelText("알림 켜짐")).toBeInTheDocument();
    });

    it("일정을 누르면 상세로 간다", async () => {
      mockBoard({ byDate: { "2026-10-24": [activity()] } });

      renderBoard();

      expect(
        await screen.findByRole("link", { name: /센소지/ }),
      ).toHaveAttribute("href", "/travel/activities/1");
    });

    it("드래그 모드가 아닐 때 행이 비활성 버튼으로 읽히지 않는다", async () => {
      mockBoard({ byDate: { "2026-10-24": [activity()] } });

      renderBoard();
      await screen.findByText("센소지");

      // dnd의 disabled sortable은 role="button" aria-disabled를 남긴다.
      // 그러면 행 전체가 비활성으로 읽혀 안의 링크를 누를 수 없다.
      const row = screen.getByRole("link", { name: /센소지/ }).closest("li");
      expect(row).not.toHaveAttribute("aria-disabled");
      expect(row).not.toHaveAttribute("role", "button");
    });

    it("보관함에서는 '보관함으로' 액션을 감춘다", async () => {
      mockBoard({
        byDate: { "2026-10-24": [] },
        archive: [activity({ id: 9, title: "후보", activityDate: null })],
      });

      renderBoard("/travel/trips/3/board?day=archive");
      await screen.findByText("후보");

      expect(screen.queryByLabelText("후보 보관함으로")).toBeNull();
      expect(screen.getByLabelText("후보 삭제")).toBeInTheDocument();
    });
  });

  describe("빈 상태", () => {
    it("일정 없는 날짜와 빈 보관함의 문구가 다르다", async () => {
      mockBoard({ byDate: { "2026-10-24": [] }, archive: [] });

      renderBoard();
      expect(await screen.findByText("일정이 없어요")).toBeInTheDocument();

      await userEvent.click(screen.getByRole("tab", { name: /보관함/ }));
      expect(
        await screen.findByText("가고 싶은 곳을 미리 담아두세요"),
      ).toBeInTheDocument();
    });
  });

  describe("일정 추가", () => {
    it("직접 입력으로 지금 보는 날짜에 일정을 만든다", async () => {
      mockBoard({ byDate: { "2026-10-24": [] } });
      const seen: Record<string, unknown>[] = [];
      server.use(
        http.post(
          `${API_BASE}/travel/trips/:tripId/activities`,
          async ({ request }) => {
            seen.push((await request.json()) as Record<string, unknown>);
            return HttpResponse.json({ code: "OK", data: activity() });
          },
        ),
      );

      renderBoard();
      await screen.findByText("일정이 없어요");

      await userEvent.click(screen.getByRole("button", { name: "일정 추가" }));
      await userEvent.click(
        await screen.findByRole("button", { name: /직접 입력/ }),
      );
      await userEvent.type(screen.getByLabelText("일정 제목"), "센소지");
      await userEvent.type(screen.getByLabelText("시각 (선택)"), "09:00");
      await userEvent.click(screen.getByRole("button", { name: "추가" }));

      await waitFor(() => expect(seen).toHaveLength(1));
      expect(seen[0]).toMatchObject({
        title: "센소지",
        activityDate: "2026-10-24",
        startTime: "09:00",
      });
    });

    it("장소 검색은 시트 안이 아니라 검색 화면으로 나간다", async () => {
      mockBoard({ byDate: { "2026-10-24": [] } });

      renderBoard();
      await screen.findByText("일정이 없어요");
      await userEvent.click(screen.getByRole("button", { name: "일정 추가" }));

      const sheet = await screen.findByRole("dialog");
      await userEvent.click(
        within(sheet).getByRole("button", { name: /장소 검색/ }),
      );

      // 결과 20개와 날짜 선택 시트가 시트 위에 쌓이지 않게 화면을 바꾼다.
      expect(await screen.findByLabelText("장소 검색")).toBeInTheDocument();
    });

    it("보관함 일정을 지금 보는 날짜로 가져온다 — 장소·메모·알림도 함께 온다", async () => {
      mockBoard({
        byDate: { "2026-10-24": [] },
        archive: [
          activity({
            id: 9,
            title: "가고 싶은 라멘집",
            activityDate: null,
            place: {
              id: 42,
              name: "이치란 시부야",
              address: "시부야구",
              lat: 35.6595,
              lng: 139.7005,
              cityName: "도쿄",
              cityPlaceRef: "ChIJ_tokyo",
            },
            memo: "1층 자판기",
            notifyEnabled: true,
            notifyMinutes: 20,
          }),
        ],
      });
      const seen: Record<string, unknown>[] = [];
      server.use(
        http.put(`${API_BASE}/travel/activities/:id`, async ({ request }) => {
          seen.push((await request.json()) as Record<string, unknown>);
          return HttpResponse.json({ code: "OK", data: activity() });
        }),
      );

      renderBoard();
      await screen.findByText("일정이 없어요");
      await userEvent.click(screen.getByRole("button", { name: "일정 추가" }));

      const sheet = await screen.findByRole("dialog");
      await userEvent.click(
        await within(sheet).findByRole("button", { name: "가고 싶은 라멘집" }),
      );

      await waitFor(() => expect(seen).toHaveLength(1));
      // 담아 둘 때 붙여 놓은 것들이 날짜를 정하는 순간 사라지면 안 된다(#1197).
      expect(seen[0]).toMatchObject({
        activityDate: "2026-10-24",
        placeId: 42,
        memo: "1층 자판기",
        notifyEnabled: true,
        notifyMinutes: 20,
      });
    });

    it("보관함을 보고 있으면 '가져오기'를 띄우지 않는다", async () => {
      mockBoard({ byDate: { "2026-10-24": [] }, archive: [] });

      renderBoard("/travel/trips/3/board?day=archive");
      await screen.findByText("가고 싶은 곳을 미리 담아두세요");
      await userEvent.click(screen.getByRole("button", { name: "일정 추가" }));

      const sheet = await screen.findByRole("dialog");
      expect(within(sheet).queryByText("보관함에서 가져오기")).toBeNull();
      expect(sheet).toHaveTextContent("미배정 보관함에 담습니다");
    });
  });

  describe("일정 액션 · 실행취소", () => {
    /** 보류 중인 요청을 잡아 두고, 실제로 나갔는지 세는 핸들러. */
    function captureWrites() {
      const puts: Record<string, unknown>[] = [];
      const deletes: string[] = [];
      server.use(
        http.put(`${API_BASE}/travel/activities/:id`, async ({ request }) => {
          puts.push((await request.json()) as Record<string, unknown>);
          return HttpResponse.json({ code: "OK", data: activity() });
        }),
        http.delete(`${API_BASE}/travel/activities/:id`, ({ params }) => {
          deletes.push(String(params.id));
          return HttpResponse.json({ code: "OK", data: null });
        }),
      );
      return { puts, deletes };
    }

    it("보관함으로 옮기면 즉시 사라지지만 요청은 아직 나가지 않는다", async () => {
      mockBoard({ byDate: { "2026-10-24": [activity()] } });
      const { puts } = captureWrites();

      renderBoard();
      await screen.findByText("센소지");

      await userEvent.click(screen.getByLabelText("센소지 보관함으로"));

      // 낙관적으로 목록에서 빠지고, 되돌릴 수 있는 스낵바가 뜬다.
      await waitFor(() => {
        expect(screen.queryByLabelText("센소지 보관함으로")).toBeNull();
      });
      expect(
        await screen.findByRole("button", { name: /실행취소/ }),
      ).toBeInTheDocument();
      expect(puts).toHaveLength(0);
    });

    it("보관함으로 내려도 장소·메모·알림은 그대로다 — 되돌려 놓을 때 필요하다", async () => {
      // 보관함행은 `activityDate`만 null로 바꾸는 수정인데, `PUT`이 전체 교체라
      // 나머지를 안 보내면 담아 둔 장소까지 함께 지워졌다(#1197).
      vi.useFakeTimers({ shouldAdvanceTime: true });
      mockBoard({
        byDate: {
          "2026-10-24": [
            activity({
              place: {
                id: 42,
                name: "센소지 본당",
                address: "다이토구",
                lat: 35.7148,
                lng: 139.7967,
                cityName: "도쿄",
                cityPlaceRef: "ChIJ_tokyo",
              },
              memo: "가미나리몬 앞",
              notifyEnabled: true,
              notifyMinutes: 20,
            }),
          ],
        },
      });
      const { puts } = captureWrites();

      renderBoard();
      await screen.findByText("센소지");
      await userEvent.click(screen.getByLabelText("센소지 보관함으로"));
      await screen.findByRole("button", { name: /실행취소/ });

      await act(async () => {
        vi.advanceTimersByTime(5000);
      });

      await waitFor(() => expect(puts).toHaveLength(1));
      expect(puts[0]).toMatchObject({
        activityDate: null,
        placeId: 42,
        startTime: "09:00",
        memo: "가미나리몬 앞",
        notifyEnabled: true,
        notifyMinutes: 20,
      });
      vi.useRealTimers();
    });

    it("실행취소를 누르면 요청이 아예 나가지 않고 일정이 돌아온다", async () => {
      mockBoard({ byDate: { "2026-10-24": [activity()] } });
      const { puts, deletes } = captureWrites();

      renderBoard();
      await screen.findByText("센소지");
      await userEvent.click(screen.getByLabelText("센소지 삭제"));
      await waitFor(() => {
        expect(screen.queryByLabelText("센소지 삭제")).toBeNull();
      });

      await userEvent.click(
        await screen.findByRole("button", { name: /실행취소/ }),
      );

      expect(await screen.findByText("센소지")).toBeInTheDocument();
      expect(deletes).toHaveLength(0);
      expect(puts).toHaveLength(0);
    });

    it("5초가 지나면 삭제 요청이 실제로 나간다", async () => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      mockBoard({ byDate: { "2026-10-24": [activity()] } });
      const { deletes } = captureWrites();

      renderBoard();
      await screen.findByText("센소지");
      await userEvent.click(screen.getByLabelText("센소지 삭제"));
      await screen.findByRole("button", { name: /실행취소/ });

      await act(async () => {
        vi.advanceTimersByTime(5000);
      });

      await waitFor(() => expect(deletes).toEqual(["1"]));
      vi.useRealTimers();
    });

    it("보류가 풀린 뒤 요청이 실패하면 알려 주고 목록을 되돌린다", async () => {
      mockBoard({ byDate: { "2026-10-24": [activity()] } });
      server.use(
        http.delete(`${API_BASE}/travel/activities/:id`, () =>
          HttpResponse.error(),
        ),
      );

      // 스낵바 타이머가 등록되기 전에 가짜 시계를 켜야 앞당길 수 있다.
      vi.useFakeTimers({ shouldAdvanceTime: true });
      renderBoard();
      await screen.findByText("센소지");
      await userEvent.click(screen.getByLabelText("센소지 삭제"));
      await screen.findByRole("button", { name: /실행취소/ });

      await act(async () => {
        vi.advanceTimersByTime(5000);
      });

      // 화면에서는 이미 지운 뒤라, 실패를 알리지 않으면 서버와 어긋난 채로 남는다.
      expect(
        await screen.findByText("변경을 저장하지 못했어요."),
      ).toBeInTheDocument();
      vi.useRealTimers();
    });

    it("보드를 떠나도 보류가 유지된다 — 되돌릴 기회가 사라지면 안 된다", async () => {
      mockBoard({ byDate: { "2026-10-24": [activity()] } });
      const { deletes } = captureWrites();

      const { unmount } = renderBoard();
      await screen.findByText("센소지");
      await userEvent.click(screen.getByLabelText("센소지 삭제"));
      await screen.findByRole("button", { name: /실행취소/ });

      // 일정 상세 등 다른 화면으로 옮겨 가는 상황. 타이머는 스낵바가 들고 있다.
      unmount();

      expect(deletes).toHaveLength(0);
      expect(usePendingActions.getState().pendingIds).toEqual([1]);
    });

    it("탭을 닫으면 보류 중인 요청을 즉시 보낸다", async () => {
      mockBoard({ byDate: { "2026-10-24": [activity()] } });
      const { deletes } = captureWrites();

      renderBoard();
      await screen.findByText("센소지");
      await userEvent.click(screen.getByLabelText("센소지 삭제"));
      await screen.findByRole("button", { name: /실행취소/ });
      expect(deletes).toHaveLength(0);

      // 그냥 사라지면 사용자는 지웠다고 믿는데 서버에는 남는다.
      window.dispatchEvent(new Event("pagehide"));

      await waitFor(() => expect(deletes).toEqual(["1"]));
    });
  });

  describe("헤더", () => {
    it("현지 시계가 보고 있는 날짜의 기준 도시를 따른다", async () => {
      // 1일차는 파리, 2일차는 호놀룰루. 같은 여행인데 탭을 넘기면 시계가 바뀐다.
      // 둘 다 기기와 오프셋이 다른 도시로 고른다 — 같으면 줄 자체를 숨기는 것이 규칙이다.
      const paris = baseCity(23, "파리", "Europe/Paris", "EUR");
      const honolulu = baseCity(22, "호놀룰루", "Pacific/Honolulu", "USD");
      mockBoard({
        byDate: { "2026-10-24": [], "2026-10-25": [] },
        trip: { singleCity: false, cityCount: 2 },
        days: [
          day(1, "2026-10-24", "토", 0, { baseCity: paris }),
          day(2, "2026-10-25", "일", 0, {
            baseCity: honolulu,
            cityChanged: true,
            legIndex: 2,
          }),
          day(3, "2026-10-26", "월", 0, { baseCity: paris }),
        ],
      });

      renderBoard();

      expect(await screen.findByText(/Europe\/Paris/)).toBeInTheDocument();

      // 도시가 둘이면 탭이 도시명을 쓴다 — `2일차`가 아니라 `2 호놀룰루`다.
      await userEvent.click(screen.getByRole("tab", { name: /호놀룰루/ }));

      expect(await screen.findByText(/Pacific\/Honolulu/)).toBeInTheDocument();
      expect(screen.queryByText(/Europe\/Paris/)).not.toBeInTheDocument();
    });

    it("부제가 도시·타임존·통화를 함께 말한다 — v2.1에서 이 값들의 주인은 날짜다", async () => {
      const paris = baseCity(23, "파리", "Europe/Paris", "EUR");
      mockBoard({
        byDate: { "2026-10-24": [] },
        trip: { singleCity: false, cityCount: 2 },
        days: [day(1, "2026-10-24", "토", 0, { baseCity: paris })],
      });

      renderBoard();

      expect(
        await screen.findByText(
          /현지 \d{2}:\d{2} · 파리 · Europe\/Paris · EUR/,
        ),
      ).toBeInTheDocument();
    });

    it("기기와 오프셋이 같으면 시각만 감추고 도시·타임존은 남긴다", async () => {
      // 기기 타임존을 그대로 쓴다 — 어느 환경에서 돌려도 오프셋이 0이다.
      const here = Intl.DateTimeFormat().resolvedOptions().timeZone;
      mockBoard({
        byDate: { "2026-10-24": [] },
        days: [
          day(1, "2026-10-24", "토", 0, {
            baseCity: baseCity(24, "여기", here, "KRW"),
          }),
        ],
      });

      renderBoard();

      expect(
        await screen.findByText(`여기 · ${here} · KRW`),
      ).toBeInTheDocument();
      expect(screen.queryByText(/현지 /)).not.toBeInTheDocument();
    });

    it("보관함에는 기준 도시가 없다 — 부제도 그렇게 말한다", async () => {
      mockBoard({ byDate: { "2026-10-24": [] }, archive: [] });

      renderBoard("/travel/trips/3/board?day=archive");

      expect(await screen.findByText("미배정 보관함")).toBeInTheDocument();
    });

    it("메뉴에서 구간 수정으로 간다", async () => {
      mockBoard({ byDate: { "2026-10-24": [] } });
      server.use(
        http.get(`${API_BASE}/travel/trips/:tripId`, () =>
          HttpResponse.json({
            code: "OK",
            data: {
              ...TRIP,
              destinationName: "도쿄",
              destinationPlaceId: null,
              lat: null,
              lng: null,
              defaultNotifyMinutes: 15,
              morningSummaryEnabled: false,
              dDay: 78,
              totalDays: 3,
              activityCount: 0,
            },
          }),
        ),
      );

      renderBoard();
      await screen.findByText("일정이 없어요");

      await userEvent.click(screen.getByRole("button", { name: "여행 메뉴" }));
      await userEvent.click(
        await screen.findByRole("menuitem", { name: "구간 수정" }),
      );

      await waitFor(() => {
        expect(
          screen.getByRole("heading", { name: "여행 수정" }),
        ).toBeInTheDocument();
      });
    });

    it("오프라인이면 도구를 열 수 없다 — 환율·날씨는 네트워크가 있어야 한다", async () => {
      vi.spyOn(navigator, "onLine", "get").mockReturnValue(false);
      mockBoard({ byDate: { "2026-10-24": [] } });

      renderBoard();
      await screen.findByText("일정이 없어요");

      expect(screen.getByRole("button", { name: "도구" })).toBeDisabled();
    });

    it("보관함에서는 지도를 열 수 없다 — 배정되지 않은 목록엔 동선이 없다", async () => {
      mockBoard({ archive: [activity()] });

      renderBoard("/travel/trips/3/board?day=archive");
      await screen.findByText("센소지");

      expect(screen.getByRole("button", { name: "지도" })).toBeDisabled();
    });
  });

  describe("이동 (#1208)", () => {
    /** 저장된 이동 하나. `mode`가 null이면 아직 아무것도 적지 않은 구간이다. */
    function move(overrides: Record<string, unknown> = {}) {
      return {
        fromActivityId: 1,
        toActivityId: 2,
        toStayId: null,
        mode: "SUBWAY",
        name: "긴자선",
        durationMinutes: 12,
        url: null,
        memo: null,
        ...overrides,
      };
    }

    function emptyMove(overrides: Record<string, unknown> = {}) {
      return move({
        mode: null,
        name: null,
        durationMinutes: null,
        ...overrides,
      });
    }

    function twoActivities() {
      return [
        activity({
          id: 1,
          title: "아침 산책",
          place: {
            id: 10,
            name: "센소지",
            address: "다이토구",
            lat: 35.7147651,
            lng: 139.7966553,
          },
        }),
        activity({
          id: 2,
          title: "전망대",
          place: {
            id: 11,
            name: "도쿄 스카이트리",
            address: "스미다구",
            lat: 35.7100627,
            lng: 139.8107004,
          },
        }),
      ];
    }

    it("적어 둔 이동은 이름과 시간을 함께 보여준다 — 현지에서 찾는 건 이름이다", async () => {
      mockBoard({
        byDate: { "2026-10-24": twoActivities() },
        moves: [move()],
      });

      renderBoard();

      expect(
        await screen.findByRole("button", { name: "이동 긴자선 · 12분" }),
      ).toBeInTheDocument();
    });

    it("이름이 없으면 수단 이름으로 대신한다", async () => {
      mockBoard({
        byDate: { "2026-10-24": twoActivities() },
        moves: [move({ name: null })],
      });

      renderBoard();

      expect(
        await screen.findByRole("button", {
          name: "이동 지하철 · 전철 · 12분",
        }),
      ).toBeInTheDocument();
    });

    it("아직 안 적은 구간도 행으로 남는다 — 그 자리가 입력 지점이다", async () => {
      mockBoard({
        byDate: { "2026-10-24": twoActivities() },
        moves: [emptyMove()],
      });

      renderBoard();

      expect(
        await screen.findByRole("button", { name: "이동 이동 추가" }),
      ).toBeInTheDocument();
    });

    it("시간을 안 적었으면 지어내지 않고 수단만 말한다", async () => {
      mockBoard({
        byDate: { "2026-10-24": twoActivities() },
        moves: [move({ name: null, durationMinutes: null })],
      });

      renderBoard();

      expect(
        await screen.findByRole("button", { name: "이동 지하철 · 전철" }),
      ).toBeInTheDocument();
    });

    it("드래그 모드에서는 감춘다 — 순서가 바뀌는 중이라 어느 구간인지가 곧 거짓이 된다", async () => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      mockBoard({
        byDate: { "2026-10-24": twoActivities() },
        moves: [move()],
      });

      renderBoard();
      const row = await screen.findByRole("button", {
        name: "이동 긴자선 · 12분",
      });

      // 400ms 길게 누르면 드래그 모드로 들어간다.
      await act(async () => {
        fireEvent.pointerDown(screen.getByText("아침 산책"));
        vi.advanceTimersByTime(500);
      });

      expect(row).not.toBeInTheDocument();
      vi.useRealTimers();
    });

    it("탭하면 편집 시트가 열리고 적어 둔 값이 채워져 있다", async () => {
      mockBoard({
        byDate: { "2026-10-24": twoActivities() },
        moves: [move({ memo: "5호차 12A" })],
      });

      const user = userEvent.setup();
      renderBoard();
      await user.click(
        await screen.findByRole("button", { name: "이동 긴자선 · 12분" }),
      );

      const sheet = await screen.findByRole("dialog");
      expect(within(sheet).getByText("아침 산책 → 전망대")).toBeInTheDocument();
      expect(within(sheet).getByLabelText("이동수단 이름")).toHaveValue(
        "긴자선",
      );
      expect(within(sheet).getByLabelText("소요 시간(분)")).toHaveValue(12);
      expect(within(sheet).getByLabelText("메모")).toHaveValue("5호차 12A");
    });

    it("수단을 고르고 시간을 넣어 저장한다 — 앱이 계산하지 않는다", async () => {
      const saved: unknown[] = [];
      server.use(
        http.put(
          `${API_BASE}/travel/trips/:tripId/moves`,
          async ({ request }) => {
            saved.push(await request.json());
            return HttpResponse.json({ code: "OK", data: move() });
          },
        ),
      );
      mockBoard({
        byDate: { "2026-10-24": twoActivities() },
        moves: [emptyMove()],
      });

      const user = userEvent.setup();
      renderBoard();
      await user.click(
        await screen.findByRole("button", { name: "이동 이동 추가" }),
      );

      const sheet = await screen.findByRole("dialog");
      await user.click(within(sheet).getByRole("button", { name: "기차" }));
      await user.type(
        within(sheet).getByLabelText("이동수단 이름"),
        "노조미 21호",
      );
      await user.type(within(sheet).getByLabelText("소요 시간(분)"), "138");
      await user.click(within(sheet).getByRole("button", { name: "저장" }));

      await waitFor(() => expect(saved).toHaveLength(1));
      expect(saved[0]).toMatchObject({
        fromActivityId: 1,
        toActivityId: 2,
        mode: "TRAIN",
        name: "노조미 21호",
        durationMinutes: 138,
      });
    });

    it("수단만 고르고 시간은 비워 둘 수 있다 — 확인은 나중에 한다", async () => {
      const saved: unknown[] = [];
      server.use(
        http.put(
          `${API_BASE}/travel/trips/:tripId/moves`,
          async ({ request }) => {
            saved.push(await request.json());
            return HttpResponse.json({ code: "OK", data: move() });
          },
        ),
      );
      mockBoard({
        byDate: { "2026-10-24": twoActivities() },
        moves: [emptyMove()],
      });

      const user = userEvent.setup();
      renderBoard();
      await user.click(
        await screen.findByRole("button", { name: "이동 이동 추가" }),
      );

      const sheet = await screen.findByRole("dialog");
      await user.click(within(sheet).getByRole("button", { name: "비행기" }));
      await user.click(within(sheet).getByRole("button", { name: "저장" }));

      await waitFor(() => expect(saved).toHaveLength(1));
      expect(saved[0]).toMatchObject({ mode: "FLIGHT", durationMinutes: null });
    });

    it("수단을 안 고르면 저장할 수 없다", async () => {
      mockBoard({
        byDate: { "2026-10-24": twoActivities() },
        moves: [emptyMove()],
      });

      const user = userEvent.setup();
      renderBoard();
      await user.click(
        await screen.findByRole("button", { name: "이동 이동 추가" }),
      );

      const sheet = await screen.findByRole("dialog");
      expect(
        within(sheet).getByRole("button", { name: "저장" }),
      ).toBeDisabled();
    });

    it("적어 둔 이동은 지울 수 있다", async () => {
      const deleted: URL[] = [];
      server.use(
        http.delete(`${API_BASE}/travel/trips/:tripId/moves`, ({ request }) => {
          deleted.push(new URL(request.url));
          return HttpResponse.json({ code: "OK", data: null });
        }),
      );
      mockBoard({
        byDate: { "2026-10-24": twoActivities() },
        moves: [move()],
      });

      const user = userEvent.setup();
      renderBoard();
      await user.click(
        await screen.findByRole("button", { name: "이동 긴자선 · 12분" }),
      );

      const sheet = await screen.findByRole("dialog");
      await user.click(
        within(sheet).getByRole("button", { name: "이동 지우기" }),
      );

      await waitFor(() => expect(deleted).toHaveLength(1));
      expect(deleted[0].searchParams.get("from")).toBe("1");
      expect(deleted[0].searchParams.get("to")).toBe("2");
    });

    it("아직 안 적은 구간에는 지우기가 없다", async () => {
      mockBoard({
        byDate: { "2026-10-24": twoActivities() },
        moves: [emptyMove()],
      });

      const user = userEvent.setup();
      renderBoard();
      await user.click(
        await screen.findByRole("button", { name: "이동 이동 추가" }),
      );

      const sheet = await screen.findByRole("dialog");
      expect(
        within(sheet).queryByRole("button", { name: "이동 지우기" }),
      ).not.toBeInTheDocument();
    });

    it("시간을 확인하러 나가는 통로가 있다 — 딥링크는 항상 대중교통이다", async () => {
      const open = vi.spyOn(window, "open").mockImplementation(() => null);
      mockBoard({
        byDate: { "2026-10-24": twoActivities() },
        moves: [move()],
      });

      const user = userEvent.setup();
      renderBoard();
      await user.click(
        await screen.findByRole("button", { name: "이동 긴자선 · 12분" }),
      );

      const sheet = await screen.findByRole("dialog");
      await user.click(
        within(sheet).getByRole("button", { name: /구글 지도에서 시간 확인/ }),
      );

      expect(open).toHaveBeenCalledWith(
        expect.stringContaining("travelmode=transit"),
        "_blank",
        "noopener",
      );
      open.mockRestore();
    });

    it("적어 둔 예매 링크를 시트에서 바로 연다", async () => {
      const open = vi.spyOn(window, "open").mockImplementation(() => null);
      mockBoard({
        byDate: { "2026-10-24": twoActivities() },
        moves: [move({ url: "https://www.jreast.co.jp/" })],
      });

      const user = userEvent.setup();
      renderBoard();
      await user.click(
        await screen.findByRole("button", { name: "이동 긴자선 · 12분" }),
      );

      const sheet = await screen.findByRole("dialog");
      await user.click(
        within(sheet).getByRole("button", { name: /저장한 링크 열기/ }),
      );

      expect(open).toHaveBeenCalledWith(
        "https://www.jreast.co.jp/",
        "_blank",
        "noopener",
      );
      open.mockRestore();
    });

    it("보관함에는 이동이 없다", async () => {
      mockBoard({ archive: twoActivities(), moves: [move()] });

      renderBoard("/travel/trips/3/board?day=archive");
      await screen.findByText("아침 산책");

      expect(
        screen.queryByRole("button", { name: /^이동 / }),
      ).not.toBeInTheDocument();
    });
  });

  describe("오프라인 (§4.6)", () => {
    /**
     * `navigator.onLine`은 게터라 `vi.spyOn(navigator, "onLine", "get")`으로 바꾼다.
     * 되돌리기는 `restoreAllMocks`가 해 준다 — `defineProperty`와 달리 새지 않는다.
     */
    function goOffline() {
      vi.spyOn(navigator, "onLine", "get").mockReturnValue(false);
    }

    afterEach(() => {
      vi.restoreAllMocks();
    });

    it("배너로 왜 안 되는지와 무엇은 되는지를 말한다", async () => {
      goOffline();
      mockBoard({ byDate: { "2026-10-24": [activity()] } });

      renderBoard();

      expect(
        await screen.findByText("오프라인 · 일정 조회만 가능합니다"),
      ).toBeInTheDocument();
      // 조회는 그대로 된다.
      expect(screen.getByText("센소지")).toBeInTheDocument();
    });

    it("편집 진입을 없앤다 — 실패할 요청을 보내지 않는다", async () => {
      goOffline();
      mockBoard({ byDate: { "2026-10-24": [activity()] } });

      renderBoard();
      await screen.findByText("센소지");

      // 추가 버튼은 아예 없다.
      expect(
        screen.queryByRole("button", { name: "일정 추가" }),
      ).not.toBeInTheDocument();
      // 행 액션도 없다.
      expect(screen.queryByLabelText("센소지 삭제")).not.toBeInTheDocument();
      expect(
        screen.queryByLabelText("센소지 보관함으로"),
      ).not.toBeInTheDocument();
    });

    it("지도는 열 수 없다 — 타일을 캐시할 수 없다", async () => {
      goOffline();
      mockBoard({ byDate: { "2026-10-24": [activity()] } });

      renderBoard();
      await screen.findByText("센소지");

      expect(screen.getByRole("button", { name: "지도" })).toBeDisabled();
    });

    it("이동시간은 캐시값이라 눌러도 다른 수단을 물어볼 수 없다", async () => {
      goOffline();
      mockBoard({
        byDate: {
          "2026-10-24": [
            activity({
              id: 1,
              title: "아침 산책",
              place: {
                id: 10,
                name: "센소지",
                address: "다이토구",
                lat: 35.7147651,
                lng: 139.7966553,
              },
            }),
            activity({
              id: 2,
              title: "전망대",
              place: {
                id: 11,
                name: "도쿄 스카이트리",
                address: "스미다구",
                lat: 35.7100627,
                lng: 139.8107004,
              },
            }),
          ],
        },
        moves: [
          {
            fromActivityId: 1,
            toActivityId: 2,
            toStayId: null,
            mode: "SUBWAY",
            name: "긴자선",
            durationMinutes: 12,
            url: null,
            memo: null,
          },
        ],
      });

      renderBoard();

      expect(
        await screen.findByRole("button", { name: "이동 긴자선 · 12분" }),
      ).toBeDisabled();
    });

    it("온라인이면 그대로 편집할 수 있다", async () => {
      mockBoard({ byDate: { "2026-10-24": [activity()] } });

      renderBoard();
      await screen.findByText("센소지");

      expect(
        screen.queryByText("오프라인 · 일정 조회만 가능합니다"),
      ).not.toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: "일정 추가" }),
      ).toBeInTheDocument();
    });
  });

  describe("숙소 (§2.5 · §9.6)", () => {
    const DOTONBORI = {
      stayId: 76,
      name: "도톤보리 호텔",
      placeId: null,
      checkInDate: "2026-10-24",
      checkOutDate: "2026-10-26",
      checkInTime: "15:00",
      checkOutTime: "11:00",
      bookingUrl: "https://booking.example/76",
      memo: "1층 편의점",
      nights: 2,
    };

    const KYOTO = {
      ...DOTONBORI,
      stayId: 77,
      name: "교토 게스트하우스",
      checkInDate: "2026-10-26",
      checkOutDate: "2026-10-27",
      bookingUrl: null,
      memo: null,
      nights: 1,
    };

    /** 보드가 내려주는 배지 값 — 서버가 기간에서 파생해 채운다. */
    const tonight = (stayId: number, name: string, isCheckInDay = false) => ({
      stayId,
      name,
      sameCity: true,
      checkInTime: "15:00",
      isCheckInDay,
    });
    const checkout = (stayId: number, name: string) => ({
      stayId,
      name,
      checkOutTime: "11:00",
    });

    it("체크아웃하는 날 아침에는 체크아웃 시각이 배지에 뜬다", async () => {
      mockBoard({
        days: [
          day(1, "2026-10-24", "토", 0, {
            stayCheckout: checkout(76, "도톤보리 호텔"),
            stayTonight: tonight(77, "교토 게스트하우스", true),
          }),
          ...DAYS.slice(1),
        ],
      });

      renderBoard();

      // 위는 체크아웃 — 급한 정보가 먼저다.
      expect(
        await screen.findByRole("button", {
          name: "숙소 도톤보리 호텔 · 오늘 체크아웃 11:00",
        }),
      ).toBeInTheDocument();
    });

    it("이동일에는 위(체크아웃)와 아래(오늘 밤)에 서로 다른 숙소가 뜬다", async () => {
      mockBoard({
        byDate: { "2026-10-24": [activity()] },
        days: [
          day(1, "2026-10-24", "토", 1, {
            stayCheckout: checkout(76, "도톤보리 호텔"),
            stayTonight: tonight(77, "교토 게스트하우스", true),
          }),
          ...DAYS.slice(1),
        ],
      });

      renderBoard();
      await screen.findByText("센소지");

      expect(
        screen.getByRole("button", {
          name: "숙소 도톤보리 호텔 · 오늘 체크아웃 11:00",
        }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", {
          name: "숙소 교토 게스트하우스 · 오늘 체크인 15:00",
        }),
      ).toBeInTheDocument();
    });

    it("옮기지 않는 날에는 배지가 하나뿐이다 — 같은 숙소를 두 번 쓰지 않는다", async () => {
      mockBoard({
        byDate: { "2026-10-24": [activity()] },
        days: [
          day(1, "2026-10-24", "토", 1, {
            stayTonight: tonight(76, "도톤보리 호텔"),
          }),
          ...DAYS.slice(1),
        ],
      });

      renderBoard();
      await screen.findByText("센소지");

      expect(
        screen.getAllByRole("button", { name: /^숙소 도톤보리 호텔/ }),
      ).toHaveLength(1);
    });

    it("숙소가 없으면 `숙소 추가`가 선다", async () => {
      mockBoard({});

      renderBoard();

      expect(
        await screen.findByRole("button", { name: "숙소 추가" }),
      ).toBeInTheDocument();
    });

    it("마지막 일정에서 숙소까지 이동이 리스트 아래에 붙는다", async () => {
      mockBoard({
        byDate: { "2026-10-24": [activity()] },
        stayMove: {
          fromActivityId: 1,
          toActivityId: null,
          toStayId: 76,
          mode: "WALK",
          name: null,
          durationMinutes: 8,
          url: null,
          memo: null,
        },
      });

      renderBoard();

      expect(
        await screen.findByRole("button", { name: "이동 도보 · 8분" }),
      ).toBeInTheDocument();
    });

    it("아직 안 적었으면 `숙소로 이동 추가`가 선다 — 그 자리가 입력 지점이다", async () => {
      mockBoard({
        byDate: { "2026-10-24": [activity()] },
        stayMove: {
          fromActivityId: 1,
          toActivityId: null,
          toStayId: 76,
          mode: null,
          name: null,
          durationMinutes: null,
          url: null,
          memo: null,
        },
      });

      renderBoard();

      expect(
        await screen.findByRole("button", { name: "이동 숙소로 이동 추가" }),
      ).toBeInTheDocument();
      expect(screen.queryByText(/분$/)).not.toBeInTheDocument();
    });

    it("숙소 이동도 시트에서 적는다 — 일정 사이 이동과 같은 저장소다", async () => {
      const saved: unknown[] = [];
      server.use(
        http.put(
          `${API_BASE}/travel/trips/:tripId/moves`,
          async ({ request }) => {
            saved.push(await request.json());
            return HttpResponse.json({ code: "OK", data: null });
          },
        ),
      );
      mockBoard({
        byDate: { "2026-10-24": [activity()] },
        stayMove: {
          fromActivityId: 1,
          toActivityId: null,
          toStayId: 76,
          mode: null,
          name: null,
          durationMinutes: null,
          url: null,
          memo: null,
        },
        stays: [DOTONBORI],
      });

      const user = userEvent.setup();
      renderBoard();
      await user.click(
        await screen.findByRole("button", { name: "이동 숙소로 이동 추가" }),
      );

      const sheet = await screen.findByRole("dialog");
      await user.click(within(sheet).getByRole("button", { name: "버스" }));
      await user.type(within(sheet).getByLabelText("소요 시간(분)"), "22");
      await user.click(within(sheet).getByRole("button", { name: "저장" }));

      await waitFor(() => expect(saved).toHaveLength(1));
      expect(saved[0]).toMatchObject({
        fromActivityId: 1,
        toStayId: 76,
        mode: "BUS",
        durationMinutes: 22,
      });
    });

    it("배지를 탭하면 상세가 열린다 — 기간·메모·예약 링크", async () => {
      mockBoard({
        days: [
          day(1, "2026-10-24", "토", 0, {
            stayTonight: tonight(76, "도톤보리 호텔", true),
          }),
          ...DAYS.slice(1),
        ],
        stays: [DOTONBORI],
      });

      const user = userEvent.setup();
      renderBoard();
      await user.click(
        await screen.findByRole("button", { name: /^숙소 도톤보리 호텔/ }),
      );

      const sheet = await screen.findByRole("dialog");
      expect(within(sheet).getByText("2박")).toBeInTheDocument();
      expect(within(sheet).getByText("1층 편의점")).toBeInTheDocument();
      expect(
        within(sheet).getByRole("link", { name: "예약 확인" }),
      ).toHaveAttribute("href", "https://booking.example/76");
    });

    it("`일정으로 추가`는 누를 때만 요청한다 — 자동 생성하면 지워도 되살아난다", async () => {
      const created: Record<string, unknown>[] = [];
      server.use(
        http.post(
          `${API_BASE}/travel/trips/:tripId/activities`,
          async ({ request }) => {
            created.push((await request.json()) as Record<string, unknown>);
            return HttpResponse.json({ code: "OK", data: activity() });
          },
        ),
      );
      mockBoard({
        days: [
          day(1, "2026-10-24", "토", 0, {
            stayTonight: tonight(76, "도톤보리 호텔", true),
          }),
          ...DAYS.slice(1),
        ],
        stays: [DOTONBORI],
      });

      const user = userEvent.setup();
      renderBoard();
      await user.click(
        await screen.findByRole("button", { name: /^숙소 도톤보리 호텔/ }),
      );

      // 시트를 여는 것만으로는 아무것도 만들지 않는다.
      const sheet = await screen.findByRole("dialog");
      expect(created).toHaveLength(0);

      await user.click(
        within(sheet).getByRole("button", { name: /일정으로 추가/ }),
      );

      await waitFor(() => expect(created).toHaveLength(2));
      expect(created.map((body) => body.title)).toEqual([
        "도톤보리 호텔 체크인",
        "도톤보리 호텔 체크아웃",
      ]);
      expect(created[0].activityDate).toBe("2026-10-24");
      expect(created[1].activityDate).toBe("2026-10-26");
    });

    it("겹치는 기간은 저장을 누르기 전에 막고 어느 숙소와 겹치는지 말한다", async () => {
      const posted: unknown[] = [];
      server.use(
        http.post(`${API_BASE}/travel/trips/:tripId/stays`, async () => {
          posted.push(1);
          return HttpResponse.json({ code: "OK", data: KYOTO });
        }),
      );
      mockBoard({ stays: [DOTONBORI] });

      const user = userEvent.setup();
      renderBoard();
      await user.click(
        await screen.findByRole("button", { name: "숙소 추가" }),
      );

      const sheet = await screen.findByRole("dialog");
      await user.type(within(sheet).getByLabelText("이름"), "겹치는 호텔");
      fireEvent.change(within(sheet).getByLabelText("체크인"), {
        target: { value: "2026-10-25" },
      });
      fireEvent.change(within(sheet).getByLabelText("체크아웃"), {
        target: { value: "2026-10-26" },
      });
      await user.click(within(sheet).getByRole("button", { name: "저장" }));

      expect(
        await within(sheet).findByText(
          "도톤보리 호텔(10.24–10.26)와 기간이 겹쳐요. 기존 숙소 기간을 먼저 줄여주세요.",
        ),
      ).toBeInTheDocument();
      // 서버에 묻기 전에 답한다 — 요청 자체가 나가지 않는다.
      expect(posted).toHaveLength(0);
    });

    it("서버가 겹침을 돌려주면 시트를 닫지 않고 그대로 보여준다", async () => {
      server.use(
        http.post(`${API_BASE}/travel/trips/:tripId/stays`, () =>
          HttpResponse.json(
            {
              code: "TRAVEL-ERR-017",
              message: "이미 숙소가 잡힌 기간입니다.",
              data: {
                stayId: 76,
                name: "도톤보리 호텔",
                checkInDate: "2026-10-24",
                checkOutDate: "2026-10-26",
              },
            },
            { status: 409 },
          ),
        ),
      );
      // 클라이언트는 겹칠 것을 모른다(목록이 비어 있다) — 서버 안내가 유일한 답이다.
      mockBoard({ stays: [] });

      const user = userEvent.setup();
      renderBoard();
      await user.click(
        await screen.findByRole("button", { name: "숙소 추가" }),
      );

      const sheet = await screen.findByRole("dialog");
      await user.type(
        within(sheet).getByLabelText("이름"),
        "교토 게스트하우스",
      );
      fireEvent.change(within(sheet).getByLabelText("체크인"), {
        target: { value: "2026-10-24" },
      });
      fireEvent.change(within(sheet).getByLabelText("체크아웃"), {
        target: { value: "2026-10-26" },
      });
      await user.click(within(sheet).getByRole("button", { name: "저장" }));

      expect(
        await within(sheet).findByText(
          "도톤보리 호텔(10.24–10.26)와 기간이 겹쳐요. 기존 숙소 기간을 먼저 줄여주세요.",
        ),
      ).toBeInTheDocument();
      // 입력이 살아 있어야 한다 — 닫아 버리면 방금 친 것을 다시 쳐야 한다.
      expect(within(sheet).getByLabelText("이름")).toHaveValue(
        "교토 게스트하우스",
      );
    });

    it("여행 마지막 날 밤은 담을 수 없고, 왜인지 말해 준다", async () => {
      mockBoard({ stays: [] });

      const user = userEvent.setup();
      renderBoard();
      await user.click(
        await screen.findByRole("button", { name: "숙소 추가" }),
      );

      const sheet = await screen.findByRole("dialog");
      await user.type(within(sheet).getByLabelText("이름"), "마지막 밤");
      fireEvent.change(within(sheet).getByLabelText("체크인"), {
        target: { value: "2026-10-26" },
      });
      fireEvent.change(within(sheet).getByLabelText("체크아웃"), {
        target: { value: "2026-10-27" },
      });
      await user.click(within(sheet).getByRole("button", { name: "저장" }));

      expect(
        await within(sheet).findByText(/여행 기간을 하루 늘려주세요/),
      ).toBeInTheDocument();
    });

    it("삭제는 무엇이 사라지는지 말하고 확인을 받는다", async () => {
      const deleted: string[] = [];
      server.use(
        http.delete(`${API_BASE}/travel/stays/:stayId`, ({ params }) => {
          deleted.push(String(params.stayId));
          return HttpResponse.json({ code: "OK", data: null });
        }),
      );
      mockBoard({
        days: [
          day(1, "2026-10-24", "토", 0, {
            stayTonight: tonight(76, "도톤보리 호텔", true),
          }),
          ...DAYS.slice(1),
        ],
        stays: [DOTONBORI],
      });

      const user = userEvent.setup();
      renderBoard();
      await user.click(
        await screen.findByRole("button", { name: /^숙소 도톤보리 호텔/ }),
      );
      await user.click(
        within(await screen.findByRole("dialog")).getByRole("button", {
          name: "삭제",
        }),
      );

      expect(
        await screen.findByText(
          "이 숙소가 붙어 있던 날짜에서 모두 사라집니다.",
        ),
      ).toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: "삭제" }));
      await waitFor(() => expect(deleted).toEqual(["76"]));
    });

    it("숙소 폼에서 장소를 검색해 붙이면 googlePlaceId와 도시를 함께 보낸다", async () => {
      const posted: Record<string, unknown>[] = [];
      server.use(
        http.get(`${API_BASE}/travel/places/search`, () =>
          HttpResponse.json({
            code: "OK",
            data: [
              {
                id: null,
                googlePlaceId: "ChIJ_namba",
                name: "난바 호텔",
                category: "호텔",
                address: "오사카시 주오구",
                rating: 4.2,
                lat: 34.6656,
                lng: 135.5061,
              },
            ],
          }),
        ),
        http.post(
          `${API_BASE}/travel/trips/:tripId/stays`,
          async ({ request }) => {
            posted.push((await request.json()) as Record<string, unknown>);
            return HttpResponse.json({ code: "OK", data: DOTONBORI });
          },
        ),
      );
      mockBoard({ stays: [] });

      const user = userEvent.setup();
      renderBoard();
      await user.click(
        await screen.findByRole("button", { name: "숙소 추가" }),
      );

      const sheet = await screen.findByRole("dialog");
      await user.type(
        within(sheet).getByLabelText("숙소 장소 검색"),
        "난바 호텔",
      );
      await user.click(within(sheet).getByRole("button", { name: /검색/ }));
      await user.click(await within(sheet).findByText("난바 호텔"));

      // 이름이 비어 있으면 고른 장소 이름으로 채운다.
      expect(within(sheet).getByLabelText("이름")).toHaveValue("난바 호텔");

      fireEvent.change(within(sheet).getByLabelText("체크인"), {
        target: { value: "2026-10-24" },
      });
      fireEvent.change(within(sheet).getByLabelText("체크아웃"), {
        target: { value: "2026-10-25" },
      });
      await user.click(within(sheet).getByRole("button", { name: "저장" }));

      await waitFor(() => expect(posted).toHaveLength(1));
      expect(posted[0].googlePlaceId).toBe("ChIJ_namba");
      // 도시는 보던 날짜의 것으로 떨어진다 — 숙소는 기준 도시와 무관하지만 기본값은 필요하다.
      expect(posted[0].cityPlaceId).toBe(21);
    });

    it("장소가 붙은 숙소는 상세에 주소와 길찾기가 나온다", async () => {
      server.use(
        http.get(`${API_BASE}/travel/places/:placeId`, () =>
          HttpResponse.json({
            code: "OK",
            data: {
              id: 31,
              googlePlaceId: "ChIJ_namba",
              name: "난바 호텔",
              address: "오사카시 주오구",
              lat: 34.6656,
              lng: 135.5061,
              category: "호텔",
              phone: null,
              rating: null,
              openingHours: null,
              manualEntry: false,
            },
          }),
        ),
      );
      mockBoard({
        days: [
          day(1, "2026-10-24", "토", 0, {
            stayTonight: tonight(76, "도톤보리 호텔", true),
          }),
          ...DAYS.slice(1),
        ],
        stays: [{ ...DOTONBORI, placeId: 31 }],
      });

      const user = userEvent.setup();
      renderBoard();
      await user.click(
        await screen.findByRole("button", { name: /^숙소 도톤보리 호텔/ }),
      );

      const sheet = await screen.findByRole("dialog");
      expect(
        await within(sheet).findByText("오사카시 주오구"),
      ).toBeInTheDocument();
      expect(
        within(sheet).getByRole("button", { name: /구글 지도에서 길찾기/ }),
      ).toBeInTheDocument();
    });

    it("장소 없는 숙소에는 길찾기가 없다 — 이름만으로는 엉뚱한 곳을 연다", async () => {
      mockBoard({
        days: [
          day(1, "2026-10-24", "토", 0, {
            stayTonight: tonight(76, "도톤보리 호텔", true),
          }),
          ...DAYS.slice(1),
        ],
        stays: [DOTONBORI],
      });

      const user = userEvent.setup();
      renderBoard();
      await user.click(
        await screen.findByRole("button", { name: /^숙소 도톤보리 호텔/ }),
      );

      const sheet = await screen.findByRole("dialog");
      expect(
        within(sheet).queryByRole("button", { name: /구글 지도에서 길찾기/ }),
      ).not.toBeInTheDocument();
    });

    it("보관함에는 그날 밤이 없다 — 배지도 숙소 이동도 없다", async () => {
      mockBoard({
        archive: [activity()],
        days: [
          day(1, "2026-10-24", "토", 0, {
            stayTonight: tonight(76, "도톤보리 호텔", true),
          }),
          ...DAYS.slice(1),
        ],
      });

      renderBoard("/travel/trips/3/board?day=archive");
      await screen.findByText("센소지");

      expect(
        screen.queryByRole("button", { name: /^숙소 / }),
      ).not.toBeInTheDocument();
      expect(screen.queryByText(/숙소로 이동/)).not.toBeInTheDocument();
    });
  });
});
