import { screen, waitFor } from "@testing-library/react";
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

    it("현재 워크스페이스 버튼이 활성으로 표시된다", async () => {
      const { unmount } = renderSidebar("/home");
      await waitFor(() => {
        expect(screen.getByRole("button", { name: "일상" })).toHaveAttribute(
          "aria-current",
          "true",
        );
      });
      expect(screen.getByRole("button", { name: "여행" })).not.toHaveAttribute(
        "aria-current",
      );
      unmount();

      renderSidebar("/travel/trips");
      await waitFor(() => {
        expect(screen.getByRole("button", { name: "여행" })).toHaveAttribute(
          "aria-current",
          "true",
        );
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

    it("전환이 3분할이다 — 여행 · 일상 · 링크", async () => {
      renderSidebar("/home");
      await waitFor(() => {
        expect(
          screen.getByRole("button", { name: "여행" }),
        ).toBeInTheDocument();
      });
      expect(screen.getByRole("button", { name: "일상" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "링크" })).toBeInTheDocument();
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
        expect(screen.getByRole("button", { name: "링크" })).toHaveAttribute(
          "aria-current",
          "true",
        );
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
});

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
