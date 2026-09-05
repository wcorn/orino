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

    it("진행 중 여행이 없으면 일정 보드 링크가 여행 목록을 가리킨다", async () => {
      renderSidebar("/travel");
      const link = await screen.findByRole("link", { name: /일정 보드/ });
      expect(link).toHaveAttribute("href", "/travel/trips");
    });

    it("진행 중 여행이 있으면 일정 보드 링크가 그 보드를 가리킨다", async () => {
      server.use(
        http.get(`${API_BASE}/travel/summary`, () =>
          HttpResponse.json({
            code: "OK",
            data: {
              ongoing: {
                id: 3,
                title: "도쿄",
                boardPath: "/travel/trips/3/board",
              },
              next: null,
              recentCompleted: null,
            },
          }),
        ),
      );

      renderSidebar("/travel");

      await waitFor(() => {
        expect(screen.getByRole("link", { name: /일정 보드/ })).toHaveAttribute(
          "href",
          "/travel/trips/3/board",
        );
      });
    });

    it("준비는 진행 중 여행이 없어도 다음 예정 여행으로 연다 — 출발 전에 쓰는 화면이다", async () => {
      mockTravelSummary({
        ongoing: null,
        next: {
          id: 7,
          title: "일본 가을",
          prepPath: "/travel/trips/7/prep",
          prep: { total: 24, done: 18, overdueCount: 0 },
        },
        recentCompleted: null,
      });

      renderSidebar("/travel");

      await waitFor(() => {
        expect(screen.getByRole("link", { name: /준비/ })).toHaveAttribute(
          "href",
          "/travel/trips/7/prep",
        );
      });
      // 보드는 다르다 — 시작하지 않은 여행의 보드는 목록으로 보낸다(기존 동작).
      expect(screen.getByRole("link", { name: /일정 보드/ })).toHaveAttribute(
        "href",
        "/travel/trips",
      );
    });

    it("진행 중 여행이 있으면 준비·경비가 그 여행을 가리킨다", async () => {
      mockTravelSummary({
        ongoing: {
          id: 3,
          title: "도쿄",
          boardPath: "/travel/trips/3/board",
          prepPath: "/travel/trips/3/prep",
          prep: { total: 5, done: 5, overdueCount: 0 },
        },
        next: {
          id: 9,
          title: "나중",
          prepPath: "/travel/trips/9/prep",
          prep: null,
        },
        recentCompleted: null,
      });

      renderSidebar("/travel");

      await waitFor(() => {
        expect(screen.getByRole("link", { name: /준비/ })).toHaveAttribute(
          "href",
          "/travel/trips/3/prep",
        );
      });
      expect(screen.getByRole("link", { name: /경비/ })).toHaveAttribute(
        "href",
        "/travel/trips/3/expenses",
      );
    });

    it("기한 지난 개수를 배지로 단다 — 서버가 센 값을 그대로 쓴다", async () => {
      mockTravelSummary({
        ongoing: null,
        next: {
          id: 7,
          title: "일본 가을",
          prepPath: "/travel/trips/7/prep",
          prep: { total: 24, done: 18, overdueCount: 2 },
        },
        recentCompleted: null,
      });

      renderSidebar("/travel");

      const badge = await screen.findByLabelText("기한 지난 것 2개");
      expect(badge).toHaveTextContent("2");
      // 「무시」가 없다 — 체크하거나 기한을 옮겨야 사라진다.
      expect(
        screen.queryByRole("button", { name: /무시/ }),
      ).not.toBeInTheDocument();
    });

    it("기한 지난 게 없으면 배지를 달지 않는다 — 0은 그리지 않는다", async () => {
      mockTravelSummary({
        ongoing: null,
        next: {
          id: 7,
          title: "일본 가을",
          prepPath: "/travel/trips/7/prep",
          prep: { total: 24, done: 24, overdueCount: 0 },
        },
        recentCompleted: null,
      });

      renderSidebar("/travel");

      await screen.findByRole("link", { name: /준비/ });
      expect(screen.queryByLabelText(/기한 지난 것/)).not.toBeInTheDocument();
    });

    it("준비 경로에서는 여행 목록이 아니라 준비가 활성화된다", async () => {
      renderSidebar("/travel/trips/3/prep");
      const prep = await screen.findByRole("link", { name: /준비/ });
      expect(prep.className).toContain("text-primary");
      expect(
        screen.getByRole("link", { name: /여행 목록/ }).className,
      ).not.toContain("text-primary");
    });

    it("보드 경로에서는 여행 목록이 아니라 일정 보드가 활성화된다", async () => {
      renderSidebar("/travel/trips/3/board");
      const board = await screen.findByRole("link", { name: /일정 보드/ });
      expect(board.className).toContain("text-primary");
      expect(
        screen.getByRole("link", { name: /여행 목록/ }).className,
      ).not.toContain("text-primary");
    });

    it("여행 목록 경로에서는 일정 보드가 아니라 여행 목록이 활성화된다", async () => {
      renderSidebar("/travel/trips");
      const list = await screen.findByRole("link", { name: /여행 목록/ });
      expect(list.className).toContain("text-primary");
      expect(
        screen.getByRole("link", { name: /일정 보드/ }).className,
      ).not.toContain("text-primary");
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
