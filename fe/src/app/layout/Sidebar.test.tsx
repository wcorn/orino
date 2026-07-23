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

  it("/planner/settings 에서는 플래너가 아니라 연동 설정이 활성화된다", async () => {
    renderSidebar("/planner/settings");
    const planner = await screen.findByRole("link", { name: /플래너/ });
    expect(planner.className).not.toContain("text-primary");
    expect(screen.getByRole("link", { name: /연동 설정/ }).className).toContain(
      "text-primary",
    );
  });
});
