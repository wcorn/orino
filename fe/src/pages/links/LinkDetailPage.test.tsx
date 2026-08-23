import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

const API_BASE = "https://api.orino.dev/api";

const AUG = "https://img.orino.dev/note-images/2026/aug.jpg";
const SEP = "https://img.orino.dev/note-images/2026/sep.jpg";

function detail(overrides: Record<string, unknown> = {}) {
  return {
    slug: "jeju",
    shortUrl: "https://s.orino.dev/jeju",
    targetUrl: AUG,
    memo: "부모님께 보낸 8월 흐름",
    tags: ["가족"],
    custom: true,
    favorite: false,
    state: "ACTIVE",
    hasPassword: false,
    visitCount: 76,
    lastVisitedAt: "2026-08-20T11:02:00Z",
    createdAt: "2026-08-03T09:12:00Z",
    expiresAt: null,
    og: null,
    targetHistory: [
      {
        targetUrl: AUG,
        reason: "최초 발급",
        changedAt: "2026-08-03T09:12:00Z",
      },
    ],
    ...overrides,
  };
}

function stats(overrides: Record<string, unknown> = {}) {
  return {
    totalVisits: 76,
    botVisits: 18,
    last7Days: 23,
    lastVisitedAt: "2026-08-20T11:02:00Z",
    daily: [
      { date: "2026-08-22", count: 0 },
      { date: "2026-08-23", count: 3 },
      { date: "2026-08-24", count: 5 },
    ],
    referrers: [{ domain: "mail.google.com", count: 40 }],
    devices: [{ device: "MOBILE", ratio: 0.78 }],
    countries: [],
    ...overrides,
  };
}

function mockDetail(data: Record<string, unknown>) {
  server.use(
    http.get(`${API_BASE}/shortlinks/jeju`, () =>
      HttpResponse.json({ code: "OK", data }),
    ),
  );
}

function mockStats(data: Record<string, unknown>) {
  server.use(
    http.get(`${API_BASE}/shortlinks/jeju/stats`, () =>
      HttpResponse.json({ code: "OK", data }),
    ),
  );
}

function renderDetail() {
  return renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: ["/links/jeju"] },
  );
}

/**
 * 링크 상세(#1242). UC-01(죽은 링크 되살리기)이 실제로 끝나는 화면이다.
 */
describe("LinkDetailPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      configurable: true,
    });
    mockDetail(detail());
    mockStats(stats());
  });

  it("목적지를 갈아끼우면 이력이 한 줄 늘고 짧은 주소는 그대로다", async () => {
    renderDetail();
    await userEvent.click(
      await screen.findByRole("button", { name: /목적지 수정/ }),
    );

    server.use(
      http.patch(`${API_BASE}/shortlinks/jeju`, () =>
        HttpResponse.json({
          code: "OK",
          data: detail({
            targetUrl: SEP,
            targetHistory: [
              {
                targetUrl: SEP,
                reason: "서명 만료로 재발급",
                changedAt: "2026-08-24T01:00:00Z",
              },
              {
                targetUrl: AUG,
                reason: "최초 발급",
                changedAt: "2026-08-03T09:12:00Z",
              },
            ],
          }),
        }),
      ),
    );

    const modal = within(screen.getByRole("dialog"));
    await userEvent.clear(modal.getByLabelText("새 목적지 URL"));
    await userEvent.type(modal.getByLabelText("새 목적지 URL"), SEP);
    await userEvent.type(
      modal.getByLabelText(/교체 사유/),
      "서명 만료로 재발급",
    );
    await userEvent.click(modal.getByRole("button", { name: "바꾸기" }));

    const history = await screen.findByRole("list", {
      name: "목적지 교체 이력",
    });
    await waitFor(() => {
      expect(within(history).getAllByRole("listitem")).toHaveLength(2);
    });
    expect(within(history).getByText(/서명 만료로 재발급/)).toBeInTheDocument();
    // 이 화면의 존재 이유 — 주소는 그대로다.
    expect(screen.getByText("jeju")).toBeInTheDocument();
  });

  it("봇 수를 총 방문에 합산하지 않고 따로 보여준다", async () => {
    renderDetail();

    expect(await screen.findByText("총 방문")).toBeInTheDocument();
    expect(screen.getByText("76")).toBeInTheDocument();
    expect(
      screen.getByText(/봇·프리뷰 18건은 따로 셉니다/),
    ).toBeInTheDocument();
    // 76 + 18 = 94를 어디에도 쓰지 않는다.
    expect(screen.queryByText("94")).toBeNull();
  });

  it("방문이 0인 링크에서도 그래프와 유입이 깨지지 않는다", async () => {
    mockStats(
      stats({
        totalVisits: 0,
        botVisits: 0,
        last7Days: 0,
        lastVisitedAt: null,
        daily: [
          { date: "2026-08-23", count: 0 },
          { date: "2026-08-24", count: 0 },
        ],
        referrers: [],
        devices: [],
      }),
    );

    renderDetail();

    expect(await screen.findByText("총 방문")).toBeInTheDocument();
    // 마지막 방문은 값이 없을 때 —로 보인다(90일이 지나도 같은 자리다).
    expect(screen.getByText("—")).toBeInTheDocument();
    expect(screen.queryByText("유입 경로")).toBeNull();
  });

  it("이력은 최신 순이고 현재 목적지에만 취소선이 없다", async () => {
    mockDetail(
      detail({
        targetUrl: SEP,
        targetHistory: [
          {
            targetUrl: SEP,
            reason: "서명 만료로 재발급",
            changedAt: "2026-08-24T01:00:00Z",
          },
          {
            targetUrl: AUG,
            reason: "최초 발급",
            changedAt: "2026-08-03T09:12:00Z",
          },
        ],
      }),
    );

    renderDetail();

    const history = await screen.findByRole("list", {
      name: "목적지 교체 이력",
    });
    const items = within(history).getAllByRole("listitem");
    expect(within(items[0]).getByText(SEP).className).not.toContain(
      "line-through",
    );
    expect(within(items[1]).getByText(AUG).className).toContain("line-through");
  });

  it("통계를 못 받아도 주소·목적지·이력은 뜬다", async () => {
    server.use(
      http.get(`${API_BASE}/shortlinks/jeju/stats`, () =>
        HttpResponse.json(
          { code: "GLB-ERR-003", message: "내부 서버 오류입니다." },
          { status: 500 },
        ),
      ),
    );

    renderDetail();

    // 헤더 카드와 이력에 같은 URL이 한 번씩 나온다 — 둘 다 살아 있어야 한다.
    expect(await screen.findAllByText(AUG)).toHaveLength(2);
    expect(screen.getByText("목적지 교체 이력")).toBeInTheDocument();
    expect(screen.queryByText("총 방문")).toBeNull();
  });
});
