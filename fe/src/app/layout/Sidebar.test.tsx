import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { Sidebar } from "./Sidebar";

const API_BASE = "https://api.orino.dev/api";

function mockSummary(now = 0) {
  server.use(
    http.get(`${API_BASE}/planner/reviews/summary`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          today: "2026-05-18",
          counts: { now, overdue: 0, upcoming: now, doneToday: 0 },
          estimatedMinutes: 0,
          materials: [],
        },
      }),
    ),
  );
}

function mockTravelSummary(data: unknown) {
  server.use(
    http.get(`${API_BASE}/travel/summary`, () =>
      HttpResponse.json({ code: "OK", data }),
    ),
  );
}

function renderSidebar(path = "/home") {
  return renderWithRouter(
    <Providers>
      <Sidebar open={false} onClose={() => {}} />
    </Providers>,
    { initialEntries: [path] },
  );
}

describe("Sidebar", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
    // 마지막으로 본 여행은 폴백이라 남아 있으면 다음 테스트의 판정을 바꾼다.
    localStorage.clear();
    mockSummary(0);
  });

  it("플래너를 단일 항목으로 노출하고 캘린더/주간 계획표/루틴을 개별 항목으로 두지 않는다", async () => {
    renderSidebar();
    await waitFor(() => {
      expect(screen.getByRole("link", { name: /플래너/ })).toBeInTheDocument();
    });
    expect(screen.queryByRole("link", { name: "주간 계획표" })).toBeNull();
    expect(screen.queryByRole("link", { name: "루틴" })).toBeNull();
    expect(screen.queryByRole("link", { name: "캘린더" })).toBeNull();
    // 연동 설정은 별도 항목으로 유지(공용화는 #962)
    expect(screen.getByRole("link", { name: /연동 설정/ })).toBeInTheDocument();
  });

  it("플래너 링크는 /planner/calendar를 가리킨다", async () => {
    renderSidebar();
    const link = await screen.findByRole("link", { name: /플래너/ });
    expect(link).toHaveAttribute("href", "/planner/calendar");
  });

  it.each(["/planner/calendar", "/planner/plan", "/planner/routines"])(
    "%s 에서 플래너 항목이 활성 하이라이트된다",
    async (path) => {
      renderSidebar(path);
      const link = await screen.findByRole("link", { name: /플래너/ });
      expect(link.className).toContain("text-primary");
    },
  );

  it("/integrations 에서는 플래너가 아니라 연동 설정이 활성화된다", async () => {
    renderSidebar("/integrations");
    const planner = await screen.findByRole("link", { name: /플래너/ });
    expect(planner.className).not.toContain("text-primary");
    expect(screen.getByRole("link", { name: /연동 설정/ }).className).toContain(
      "text-primary",
    );
  });

  describe("워크스페이스 스위처", () => {
    it("일상 경로에서는 일상 메뉴를, 여행 경로에서는 여행 메뉴를 보여준다", async () => {
      const { unmount } = renderSidebar("/home");
      await waitFor(() => {
        expect(
          screen.getByRole("link", { name: /학습 자료/ }),
        ).toBeInTheDocument();
      });
      expect(screen.queryByRole("link", { name: /여행 목록/ })).toBeNull();
      unmount();

      renderSidebar("/travel");
      await waitFor(() => {
        expect(
          screen.getByRole("link", { name: /여행 목록/ }),
        ).toBeInTheDocument();
      });
      expect(screen.queryByRole("link", { name: /학습 자료/ })).toBeNull();
      expect(screen.getByRole("link", { name: /도구/ })).toBeInTheDocument();
    });

    it("트리거가 지금 있는 워크스페이스를 말한다", async () => {
      const { unmount } = renderSidebar("/home");
      await waitFor(() => {
        expect(switcher("일상")).toBeInTheDocument();
      });
      unmount();

      renderSidebar("/travel/trips");
      await waitFor(() => {
        expect(switcher("여행")).toBeInTheDocument();
      });
    });

    it("일상 워크스페이스에서는 여행 요약을 부르지 않는다", async () => {
      let called = false;
      server.use(
        http.get(`${API_BASE}/travel/summary`, () => {
          called = true;
          return HttpResponse.json({
            code: "OK",
            data: { ongoing: null, next: null, recentCompleted: null },
          });
        }),
      );

      renderSidebar("/home");
      await waitFor(() => {
        expect(
          screen.getByRole("link", { name: /학습 자료/ }),
        ).toBeInTheDocument();
      });

      expect(called).toBe(false);
    });

    /** 사이드바 여행 트리(#1346 · 화면 §10.8). 요약의 `trips[]` 하나로 그린다. */
    function tripsSummary(
      trips: unknown[],
      completedCount = 0,
      extra: Record<string, unknown> = {},
    ) {
      mockTravelSummary({
        ongoing: null,
        next: null,
        recentCompleted: null,
        trips,
        completedCount,
        ...extra,
      });
    }

    function trip(
      id: number,
      title: string,
      over: Record<string, unknown> = {},
    ) {
      return {
        id,
        title,
        status: "UPCOMING",
        startDate: "2026-10-24",
        endDate: "2026-10-27",
        dDay: 49,
        dayNumber: null,
        prep: { total: 24, done: 18, overdueCount: 0 },
        expense: { budget: null, spent: 0 },
        ...over,
      };
    }

    it("여행이 없으면 펼칠 줄도 없다 — 보드는 여행 없이 열 수 없다", async () => {
      renderSidebar("/travel");
      // 「여행 만들기」는 늘 있다 — 여기서 시작할 수 있어야 한다.
      expect(
        await screen.findByRole("link", { name: "여행 만들기" }),
      ).toHaveAttribute("href", "/travel/trips/new");
      expect(screen.queryByRole("link", { name: /일정 보드/ })).toBeNull();
      expect(screen.queryByRole("link", { name: /여행 트리/ })).toBeNull();
    });

    it("진행 중·예정 여행을 한 줄씩 펼치고 선택된 여행만 자식을 편다", async () => {
      tripsSummary([
        trip(3, "일본 가을", { status: "ONGOING", dDay: null, dayNumber: 4 }),
        trip(7, "도쿄 3박 4일"),
      ]);

      renderSidebar("/travel/trips/3/board");

      await screen.findByRole("link", { name: /일본 가을/ });
      expect(
        screen.getByRole("link", { name: /도쿄 3박 4일/ }),
      ).toBeInTheDocument();
      // 선택된 여행의 자식 셋만 그린다 — 다른 여행은 한 줄로 접힌다.
      expect(screen.getByRole("link", { name: /일정 보드/ })).toHaveAttribute(
        "href",
        "/travel/trips/3/board",
      );
      expect(screen.getByRole("link", { name: /^준비/ })).toHaveAttribute(
        "href",
        "/travel/trips/3/prep",
      );
      expect(screen.getByRole("link", { name: /경비/ })).toHaveAttribute(
        "href",
        "/travel/trips/3/expenses",
      );
      expect(screen.getAllByRole("link", { name: /일정 보드/ })).toHaveLength(
        1,
      );
    });

    it("진행 중이면 「4일차」, 예정이면 「D-49」 — 둘이 같은 자리를 나눠 쓴다", async () => {
      tripsSummary([
        trip(3, "일본 가을", { status: "ONGOING", dDay: null, dayNumber: 4 }),
        trip(7, "도쿄 3박 4일"),
      ]);

      renderSidebar("/travel");

      const ongoing = await screen.findByRole("link", { name: /일본 가을/ });
      expect(ongoing).toHaveTextContent("4일차");
      expect(ongoing).not.toHaveTextContent("D-");
      expect(
        screen.getByRole("link", { name: /도쿄 3박 4일/ }),
      ).toHaveTextContent("D-49");
    });

    it("여행 행을 누르면 보던 탭을 유지한 채 그 여행으로 간다", async () => {
      tripsSummary([trip(3, "일본 가을"), trip(7, "도쿄 3박 4일")]);

      // 준비를 보는 중이면 다른 여행 행도 그 여행의 「준비」를 가리킨다.
      renderSidebar("/travel/trips/3/prep");

      await waitFor(() => {
        expect(
          screen.getByRole("link", { name: /도쿄 3박 4일/ }),
        ).toHaveAttribute("href", "/travel/trips/7/prep");
      });
    });

    it("경비를 보는 중이면 다른 여행 행도 그 여행의 경비를 가리킨다", async () => {
      tripsSummary([trip(3, "일본 가을"), trip(7, "도쿄 3박 4일")]);

      renderSidebar("/travel/trips/3/expenses");

      await waitFor(() => {
        expect(
          screen.getByRole("link", { name: /도쿄 3박 4일/ }),
        ).toHaveAttribute("href", "/travel/trips/7/expenses");
      });
    });

    it("탭 밖(여행 홈)에서는 여행 행이 보드로 들어간다", async () => {
      tripsSummary([trip(3, "일본 가을"), trip(7, "도쿄 3박 4일")]);

      renderSidebar("/travel");

      await waitFor(() => {
        expect(
          screen.getByRole("link", { name: /도쿄 3박 4일/ }),
        ).toHaveAttribute("href", "/travel/trips/7/board");
      });
    });

    it("URL에 여행이 없으면 진행 중 → 다음 예정 순으로 편다", async () => {
      mockTravelSummary({
        ongoing: {
          id: 7,
          title: "도쿄 3박 4일",
          boardPath: "/travel/trips/7/board",
        },
        next: { id: 3, title: "일본 가을", prepPath: "/travel/trips/3/prep" },
        recentCompleted: null,
        trips: [
          trip(3, "일본 가을"),
          trip(7, "도쿄 3박 4일", {
            status: "ONGOING",
            dDay: null,
            dayNumber: 4,
          }),
        ],
        completedCount: 0,
      });

      renderSidebar("/travel");

      await waitFor(() => {
        expect(screen.getByRole("link", { name: /일정 보드/ })).toHaveAttribute(
          "href",
          "/travel/trips/7/board",
        );
      });
    });

    it("URL의 여행이 요약에 없으면 죽은 id를 펼치지 않고 기본 여행으로 내려간다", async () => {
      tripsSummary([trip(3, "일본 가을")]);

      // 삭제된 여행(99)의 준비로 들어온 경우.
      renderSidebar("/travel/trips/99/prep");

      await waitFor(() => {
        expect(screen.getByRole("link", { name: /^준비/ })).toHaveAttribute(
          "href",
          "/travel/trips/3/prep",
        );
      });
    });

    it("다녀온 여행은 줄로 늘어놓지 않고 개수 한 줄로 접는다", async () => {
      tripsSummary([trip(3, "일본 가을")], 2);

      renderSidebar("/travel");

      const link = await screen.findByRole("link", { name: "다녀온 여행 2개" });
      expect(link).toHaveAttribute("href", "/travel/trips");
    });

    it("다녀온 여행이 없으면 그 줄도 없다 — 0은 그리지 않는다", async () => {
      tripsSummary([trip(3, "일본 가을")], 0);

      renderSidebar("/travel");

      await screen.findByRole("link", { name: /일본 가을/ });
      expect(screen.queryByText(/다녀온 여행/)).toBeNull();
    });

    it("기한 지난 개수를 선택된 여행의 준비에 배지로 단다", async () => {
      tripsSummary([
        trip(3, "일본 가을", {
          prep: { total: 24, done: 18, overdueCount: 2 },
        }),
        trip(7, "도쿄 3박 4일", {
          prep: { total: 3, done: 0, overdueCount: 5 },
        }),
      ]);

      renderSidebar("/travel/trips/3/prep");

      const badge = await screen.findByLabelText("기한 지난 것 2개");
      expect(badge).toHaveTextContent("2");
      // 접힌 여행의 배지는 그리지 않는다 — 자식 줄 자체가 없다.
      expect(screen.queryByLabelText("기한 지난 것 5개")).toBeNull();
      // 「무시」가 없다 — 체크하거나 기한을 옮겨야 사라진다.
      expect(
        screen.queryByRole("button", { name: /무시/ }),
      ).not.toBeInTheDocument();
    });

    it("기한 지난 게 없으면 배지를 달지 않는다 — 0은 그리지 않는다", async () => {
      tripsSummary([
        trip(3, "일본 가을", {
          prep: { total: 24, done: 24, overdueCount: 0 },
        }),
      ]);

      renderSidebar("/travel/trips/3/prep");

      await screen.findByRole("link", { name: /^준비/ });
      expect(screen.queryByLabelText(/기한 지난 것/)).not.toBeInTheDocument();
    });

    describe("경로별 활성 판정", () => {
      /** 활성 표시가 틀려도 화면은 열린다 — 그래서 경로마다 못박아 둔다(R-18). */
      beforeEach(() => {
        tripsSummary([trip(3, "일본 가을"), trip(7, "도쿄 3박 4일")]);
      });

      it("보드 경로는 그 여행의 「일정 보드」를 켠다", async () => {
        renderSidebar("/travel/trips/3/board");
        const board = await screen.findByRole("link", { name: /일정 보드/ });
        expect(board.className).toContain("text-primary");
        expect(
          screen.getByRole("link", { name: /여행 목록/ }).className,
        ).not.toContain("text-primary");
      });

      it("지도 경로도 「일정 보드」다 — 보드의 다른 보기다", async () => {
        renderSidebar("/travel/trips/3/map");
        const board = await screen.findByRole("link", { name: /일정 보드/ });
        expect(board.className).toContain("text-primary");
      });

      it("일정 상세도 「일정 보드」다 — URL에 여행 id가 없어도 보드에서 온 화면이다", async () => {
        renderSidebar("/travel/activities/12");
        const board = await screen.findByRole("link", { name: /일정 보드/ });
        expect(board.className).toContain("text-primary");
      });

      it("준비 경로는 「준비」를, 경비 경로는 「경비」를 켠다", async () => {
        const { unmount } = renderSidebar("/travel/trips/3/prep");
        const prep = await screen.findByRole("link", { name: /^준비/ });
        expect(prep.className).toContain("text-primary");
        expect(
          screen.getByRole("link", { name: /경비/ }).className,
        ).not.toContain("text-primary");
        unmount();

        renderSidebar("/travel/trips/3/expenses");
        const expenses = await screen.findByRole("link", { name: /경비/ });
        expect(expenses.className).toContain("text-primary");
      });

      it("여행 목록 경로에서는 자식이 아무것도 안 켜진다", async () => {
        renderSidebar("/travel/trips");
        // 트리가 붙기를 기다린 뒤에 본다 — 상단 항목은 요약 없이도 먼저 그려진다.
        const board = await screen.findByRole("link", { name: /일정 보드/ });
        expect(
          screen.getByRole("link", { name: /여행 목록/ }).className,
        ).toContain("text-primary");
        expect(board.className).not.toContain("text-primary");
      });
    });

    it("여행을 못 정하면 준비·경비가 폴백 화면을 가리킨다 — 목록으로 보내지 않는다", async () => {
      // 다녀온 여행만 있는 상태. 트리에는 펼칠 줄이 없지만 들어갈 문은 남아야 한다.
      tripsSummary([], 2);

      renderSidebar("/travel");

      const prep = await screen.findByRole("link", { name: /^준비/ });
      expect(prep).toHaveAttribute("href", "/travel/prep");
      expect(screen.getByRole("link", { name: /경비/ })).toHaveAttribute(
        "href",
        "/travel/expenses",
      );
      // 보드는 없다 — 여행 없이 열 수 없고 고를 것도 없다.
      expect(screen.queryByRole("link", { name: /일정 보드/ })).toBeNull();
    });

    it("여행을 펼치고 있으면 폴백 줄은 없다", async () => {
      tripsSummary([trip(3, "일본 가을")]);

      renderSidebar("/travel");

      await screen.findByRole("link", { name: /^준비/ });
      expect(screen.getByRole("link", { name: /^준비/ })).toHaveAttribute(
        "href",
        "/travel/trips/3/prep",
      );
    });

    it("진행 중·예정이 없으면 마지막으로 본 여행을 편다", async () => {
      localStorage.setItem("travel.lastTripId", "7");
      tripsSummary([trip(3, "일본 가을"), trip(7, "도쿄")]);

      renderSidebar("/travel");

      await waitFor(() => {
        expect(screen.getByRole("link", { name: /^준비/ })).toHaveAttribute(
          "href",
          "/travel/trips/7/prep",
        );
      });
    });

    it("마지막으로 본 여행이 사라졌으면 조용히 버린다 — 죽은 id를 펼치지 않는다", async () => {
      // 지웠거나·다른 기기에서 만든 값이다. 요약에 없으면 다음 폴백으로 내려간다.
      localStorage.setItem("travel.lastTripId", "99");
      tripsSummary([trip(3, "일본 가을")]);

      renderSidebar("/travel");

      await waitFor(() => {
        expect(screen.getByRole("link", { name: /^준비/ })).toHaveAttribute(
          "href",
          "/travel/trips/3/prep",
        );
      });
    });

    it("여행을 열면 그 여행을 기억한다 — 다음에 여행 id 없이 들어와도 여기로 온다", async () => {
      tripsSummary([trip(3, "일본 가을"), trip(7, "도쿄")]);

      const { unmount } = renderSidebar("/travel/trips/7/prep");
      await screen.findByRole("link", { name: /도쿄/ });
      await waitFor(() => {
        expect(localStorage.getItem("travel.lastTripId")).toBe("7");
      });
      unmount();

      renderSidebar("/travel");
      await waitFor(() => {
        expect(screen.getByRole("link", { name: /^준비/ })).toHaveAttribute(
          "href",
          "/travel/trips/7/prep",
        );
      });
    });

    it("진행 중·예정이 6개를 넘으면 여행 줄만 드롭다운으로 접는다", async () => {
      tripsSummary(
        Array.from({ length: 7 }, (_, i) => trip(i + 1, `여행${i + 1}`)),
      );

      renderSidebar("/travel/trips/3/prep");

      // 일곱 줄을 늘어놓지 않는다 — 트리가 사이드바를 통째로 차지한다.
      await screen.findByRole("button", { name: /여행 전환 — 현재 여행3/ });
      expect(screen.queryByRole("link", { name: /여행7/ })).toBeNull();
      // 탭은 그대로 편다 — 접히는 것은 여행을 고르는 줄뿐이다.
      expect(screen.getByRole("link", { name: /^준비/ })).toHaveAttribute(
        "href",
        "/travel/trips/3/prep",
      );
    });

    it("드롭다운에 워크스페이스 4개와 「선택 화면으로」가 있고 현재 항목에 표시가 붙는다", async () => {
      renderSidebar("/home");
      await waitFor(() => {
        expect(switcher("일상")).toBeInTheDocument();
      });

      await userEvent.click(switcher("일상"));

      for (const name of ["여행", "일상", "링크", "가계부"]) {
        expect(
          await screen.findByRole("menuitem", { name }),
        ).toBeInTheDocument();
      }
      expect(
        screen.getByRole("menuitem", { name: "선택 화면으로" }),
      ).toBeInTheDocument();
      // 열었을 때 어디에 있는지가 먼저 보여야 한다.
      expect(screen.getByRole("menuitem", { name: "일상" })).toHaveAttribute(
        "aria-current",
        "true",
      );
      expect(
        screen.getByRole("menuitem", { name: "가계부" }),
      ).not.toHaveAttribute("aria-current");
    });

    it("트리거는 접힘·펼침을 알린다 — 224px에 4칸을 넣지 않는다", async () => {
      renderSidebar("/home");
      await waitFor(() => {
        expect(switcher("일상")).toHaveAttribute("aria-expanded", "false");
      });
      expect(switcher("일상")).toHaveAttribute("aria-haspopup", "menu");

      await userEvent.click(switcher("일상"));

      await waitFor(() => {
        expect(switcher("일상")).toHaveAttribute("aria-expanded", "true");
      });
    });

    it("드롭다운에서 가계부를 고르면 가계부 메뉴로 바뀐다", async () => {
      renderSidebar("/home");
      await waitFor(() => {
        expect(switcher("일상")).toBeInTheDocument();
      });

      await userEvent.click(switcher("일상"));
      await userEvent.click(
        await screen.findByRole("menuitem", { name: "가계부" }),
      );

      await waitFor(() => {
        expect(switcher("가계부")).toBeInTheDocument();
      });
      expect(screen.getByRole("link", { name: /내역/ })).toBeInTheDocument();
      expect(screen.queryByRole("link", { name: /학습 자료/ })).toBeNull();
    });

    it("링크 경로에서는 링크 메뉴를 보여준다 — 일상 사이드바는 그대로다", async () => {
      renderSidebar("/links");
      await waitFor(() => {
        expect(
          screen.getByRole("link", { name: /링크 목록/ }),
        ).toBeInTheDocument();
      });
      expect(
        screen.getByRole("link", { name: /즐겨찾기/ }),
      ).toBeInTheDocument();
      expect(screen.queryByRole("link", { name: /학습 자료/ })).toBeNull();
      expect(screen.queryByRole("link", { name: /여행 목록/ })).toBeNull();
    });

    it("/links/{slug} 에서도 링크 워크스페이스로 판정한다", async () => {
      renderSidebar("/links/9dwqr");
      await waitFor(() => {
        expect(switcher("링크")).toBeInTheDocument();
      });
      // 상세는 「링크 목록」이 대표한다.
      expect(
        (await screen.findByRole("link", { name: /링크 목록/ })).className,
      ).toContain("text-primary");
    });

    it("링크 워크스페이스가 아니면 링크 API를 부르지 않는다", async () => {
      let called = false;
      server.use(
        http.get(`${API_BASE}/shortlinks`, () => {
          called = true;
          return HttpResponse.json({
            code: "OK",
            data: {
              counts: { all: 0, active: 0, inactive: 0 },
              favorites: [],
              recent: [],
            },
          });
        }),
      );

      renderSidebar("/home");
      await waitFor(() => {
        expect(
          screen.getByRole("link", { name: /학습 자료/ }),
        ).toBeInTheDocument();
      });

      expect(called).toBe(false);
    });

    it("링크 메뉴는 전체·즐겨찾기 개수를, 태그 섹션은 태그별 개수를 보여준다", async () => {
      server.use(
        http.get(`${API_BASE}/shortlinks`, () =>
          HttpResponse.json({
            code: "OK",
            data: {
              counts: { all: 34, active: 29, inactive: 5 },
              favorites: [linkCard("jeju", true)],
              recent: [linkCard("busan", false)],
            },
          }),
        ),
        http.get(`${API_BASE}/shortlinks/tags`, () =>
          HttpResponse.json({
            code: "OK",
            data: [
              { name: "가족", count: 9 },
              { name: "여행", count: 7 },
            ],
          }),
        ),
      );

      renderSidebar("/links");

      await waitFor(() => {
        expect(
          screen.getByRole("link", { name: /링크 목록/ }),
        ).toHaveTextContent("34");
      });
      expect(screen.getByRole("link", { name: /즐겨찾기/ })).toHaveTextContent(
        "1",
      );
      const tag = await screen.findByRole("link", { name: /가족/ });
      expect(tag).toHaveAttribute("href", "/links?tag=%EA%B0%80%EC%A1%B1");
      expect(tag).toHaveTextContent("9");
    });

    it("즐겨찾기는 목록의 필터다 — ?favorite=1 에서만 활성이다", async () => {
      renderSidebar("/links?favorite=1");

      const favorite = await screen.findByRole("link", { name: /즐겨찾기/ });
      expect(favorite).toHaveAttribute("href", "/links?favorite=1");
      expect(favorite.className).toContain("text-primary");
      expect(
        screen.getByRole("link", { name: /링크 목록/ }).className,
      ).not.toContain("text-primary");
    });
  });

  describe("가계부 메뉴", () => {
    const LABELS = [
      "홈",
      "내역",
      "예정",
      "자산",
      "카드 청구서",
      "정기 항목",
      "예산",
      "통계",
      "가져오기",
      "설정",
    ];

    it("가계부 경로에서는 가계부 메뉴 10개를 보여준다 — 일상·링크 메뉴는 사라진다", async () => {
      renderSidebar("/ledger");

      await waitFor(() => {
        expect(screen.getByRole("link", { name: /내역/ })).toBeInTheDocument();
      });
      for (const label of LABELS) {
        expect(
          screen.getByRole("link", { name: new RegExp(label) }),
        ).toBeInTheDocument();
      }
      expect(screen.queryByRole("link", { name: /학습 자료/ })).toBeNull();
      expect(screen.queryByRole("link", { name: /링크 목록/ })).toBeNull();
    });

    it("「홈」은 /ledger에서만 활성이다 — 하위 경로까지 잡지 않는다", async () => {
      const { unmount } = renderSidebar("/ledger");
      let home = await screen.findByRole("link", { name: /홈/ });
      expect(home.className).toContain("text-primary");
      unmount();

      renderSidebar("/ledger/transactions");
      home = await screen.findByRole("link", { name: /홈/ });
      expect(home.className).not.toContain("text-primary");
      expect(screen.getByRole("link", { name: /내역/ }).className).toContain(
        "text-primary",
      );
    });

    it("자산 상세에서도 「자산」이 활성이다", async () => {
      renderSidebar("/ledger/assets/3");

      const assets = await screen.findByRole("link", { name: /자산/ });
      expect(assets.className).toContain("text-primary");
      expect(switcher("가계부")).toBeInTheDocument();
    });

    it("청구서 경로에서도 「카드 청구서」가 활성이다", async () => {
      renderSidebar("/ledger/cards/12/statements");

      const cards = await screen.findByRole("link", { name: /카드 청구서/ });
      expect(cards.className).toContain("text-primary");
    });

    it("가계부에서는 링크 API를 부르지 않는다", async () => {
      let called = false;
      server.use(
        http.get(`${API_BASE}/shortlinks`, () => {
          called = true;
          return HttpResponse.json({
            code: "OK",
            data: {
              counts: { all: 0, active: 0, inactive: 0 },
              favorites: [],
              recent: [],
            },
          });
        }),
      );

      renderSidebar("/ledger");
      await waitFor(() => {
        expect(screen.getByRole("link", { name: /내역/ })).toBeInTheDocument();
      });

      expect(called).toBe(false);
    });
  });
});

/** 스위처 트리거. 접근성 이름이 현재 워크스페이스를 담는다. */
function switcher(workspace: string) {
  return screen.getByRole("button", {
    name: `워크스페이스 전환 — 현재 ${workspace}`,
  });
}

/** 사이드바 개수만 보는 테스트라 카드 내용은 최소로 채운다. */
function linkCard(slug: string, favorite: boolean) {
  return {
    slug,
    shortUrl: `https://s.orino.dev/${slug}`,
    targetUrl: "https://img.orino.dev/a.jpg",
    memo: null,
    tags: [],
    custom: true,
    favorite,
    state: "ACTIVE",
    hasPassword: false,
    visitCount: 0,
    lastVisitedAt: null,
  };
}
