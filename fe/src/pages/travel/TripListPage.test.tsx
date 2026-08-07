import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

const API_BASE = "https://api.orino.dev/api";

const COUNTS = { upcoming: 2, ongoing: 0, completed: 1 };

function trip(overrides: Record<string, unknown> = {}) {
  return {
    id: 3,
    title: "도쿄 3박 4일",
    destinationName: "도쿄",
    startDate: "2026-10-24",
    endDate: "2026-10-27",
    status: "UPCOMING",
    dDay: 78,
    activityCount: 13,
    ...overrides,
  };
}

/** status 쿼리별로 다른 목록을 주는 핸들러. 탭 전환이 실제로 재조회하는지 본다. */
function mockTripsByStatus(byStatus: Record<string, unknown[]>) {
  server.use(
    http.get(`${API_BASE}/travel/trips`, ({ request }) => {
      const status = new URL(request.url).searchParams.get("status") ?? "all";
      return HttpResponse.json({
        code: "OK",
        data: { counts: COUNTS, trips: byStatus[status] ?? [] },
      });
    }),
  );
}

function renderApp() {
  return renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: ["/travel/trips"] },
  );
}

describe("TripListPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  it("탭 라벨 뒤에 전체 기준 건수를 보여준다", async () => {
    mockTripsByStatus({ UPCOMING: [trip()] });

    renderApp();

    await waitFor(() => {
      expect(screen.getByRole("tab", { name: /예정/ })).toHaveTextContent(
        "예정2",
      );
    });
    expect(screen.getByRole("tab", { name: /진행 중/ })).toHaveTextContent(
      "진행 중0",
    );
    expect(screen.getByRole("tab", { name: /완료/ })).toHaveTextContent(
      "완료1",
    );
  });

  it("메타를 한 문자열로 그려 숫자와 단위가 갈라지지 않는다", async () => {
    mockTripsByStatus({ UPCOMING: [trip({ activityCount: 6 })] });

    renderApp();

    // "도쿄 · 10.24 – 10.27 · 일정 6개"가 통째로 한 노드여야 한다.
    expect(
      await screen.findByText("도쿄 · 10.24 – 10.27 · 일정 6개"),
    ).toBeInTheDocument();
  });

  it("카드를 누르면 그 여행의 보드로 간다", async () => {
    mockTripsByStatus({ UPCOMING: [trip()] });

    renderApp();

    const row = await screen.findByRole("link", { name: /도쿄 3박 4일/ });
    expect(row).toHaveAttribute("href", "/travel/trips/3/board");
  });

  it("기본 탭은 예정이고 정렬 안내를 함께 보여준다", async () => {
    mockTripsByStatus({ UPCOMING: [trip()] });

    renderApp();

    await waitFor(() => {
      expect(screen.getByRole("tab", { name: /예정/ })).toHaveAttribute(
        "aria-selected",
        "true",
      );
    });
    expect(screen.getByText("시작일 오름차순")).toBeInTheDocument();
  });

  it("완료 탭으로 옮기면 그 목록을 다시 받아오고 정렬 안내가 바뀐다", async () => {
    mockTripsByStatus({
      UPCOMING: [trip()],
      COMPLETED: [
        trip({
          id: 2,
          title: "오사카 2박 3일",
          destinationName: "오사카",
          startDate: "2026-05-09",
          endDate: "2026-05-11",
          status: "COMPLETED",
          activityCount: 9,
        }),
      ],
    });

    renderApp();
    await screen.findByRole("link", { name: /도쿄 3박 4일/ });

    await userEvent.click(screen.getByRole("tab", { name: /완료/ }));

    expect(
      await screen.findByRole("link", { name: /오사카 2박 3일/ }),
    ).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText("종료일 내림차순")).toBeInTheDocument();
    });
    expect(screen.queryByRole("link", { name: /도쿄 3박 4일/ })).toBeNull();
  });

  it("빈 탭은 만들기 버튼이 있는 빈 상태를 보여준다", async () => {
    mockTripsByStatus({ UPCOMING: [] });

    renderApp();

    await waitFor(() => {
      expect(screen.getByText("예정 여행이 없어요.")).toBeInTheDocument();
    });
    const panel = screen.getByRole("tabpanel");
    expect(
      within(panel).getByRole("link", { name: /여행 만들기/ }),
    ).toHaveAttribute("href", "/travel/trips/new");
  });

  it("헤더의 여행 만들기 버튼은 항상 있다", async () => {
    mockTripsByStatus({ UPCOMING: [trip()] });

    renderApp();

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "여행 목록" }),
      ).toBeInTheDocument();
    });
    expect(
      screen.getAllByRole("link", { name: /여행 만들기/ })[0],
    ).toHaveAttribute("href", "/travel/trips/new");
  });
});
