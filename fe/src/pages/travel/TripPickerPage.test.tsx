import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

const API_BASE = "https://api.orino.dev/api";

function trip(id: number, title: string, over: Record<string, unknown> = {}) {
  return {
    id,
    title,
    status: "UPCOMING",
    startDate: "2026-10-24",
    endDate: "2026-10-29",
    dDay: 49,
    dayNumber: null,
    prep: { total: 24, done: 18, overdueCount: 0 },
    expense: { budget: 800000, spent: 412000 },
    ...over,
  };
}

function mockSummary(data: Record<string, unknown>) {
  server.use(
    http.get(`${API_BASE}/travel/summary`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          ongoing: null,
          next: null,
          recentCompleted: null,
          trips: [],
          completedCount: 0,
          ...data,
        },
      }),
    ),
  );
}

/** 준비 화면이 열렸는지. 폴백에서 실제 여행으로 넘어갔는지를 이걸로 본다. */
function mockPrepScreen(tripId: number) {
  server.use(
    http.get(`${API_BASE}/travel/trips/${tripId}/prep`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          tripId,
          startDate: "2026-10-24",
          dday: 49,
          total: 0,
          done: 0,
          overdueCount: 0,
          groups: [],
        },
      }),
    ),
  );
}

/** 지금 주소. `MemoryRouter`라 `window.location`은 움직이지 않는다. */
function LocationProbe() {
  return <span data-testid="pathname">{useLocation().pathname}</span>;
}

function renderAt(path: string) {
  return renderWithRouter(
    <Providers>
      <AppRouter />
      <LocationProbe />
    </Providers>,
    { initialEntries: [path] },
  );
}

function pathname(): string {
  return screen.getByTestId("pathname").textContent ?? "";
}

/** 사이드바에도 같은 이름의 링크가 있다(#1346) — 여기서 보는 것은 화면 쪽이다. */
function main() {
  return within(screen.getByRole("main"));
}

/**
 * 여행이 정해지지 않은 진입(#1347 · 프레임 `2b`).
 *
 * <p>여기서 지키는 것은 하나다 — <b>여행 목록으로 튕기지 않는다.</b> 정할 수 있으면 그
 * 여행을 열고, 못 정하면 고르게 한다.
 */
describe("TripPickerPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
    localStorage.clear();
  });

  it("진행 중 여행이 있어도 대신 고르지 않는다 — 조용한 리다이렉트가 아니다", async () => {
    // 지운 여행 링크로 들어온 사람에게 다른 여행의 준비를 열어 주면, 그 화면을 자기가
    // 찾던 것으로 읽는다. 기본 여행 판정은 사이드바가 링크를 만들 때 쓴다(D-38).
    mockSummary({
      ongoing: {
        id: 3,
        title: "일본 가을",
        boardPath: "/travel/trips/3/board",
      },
      trips: [trip(3, "일본 가을", { status: "ONGOING" }), trip(7, "도쿄")],
    });

    renderAt("/travel/prep");

    expect(
      await screen.findByText("어느 여행의 준비인가요?"),
    ).toBeInTheDocument();
    expect(pathname()).toBe("/travel/prep");
  });

  it("고를 여행을 기간·준비·경비와 함께 보여준다 — 고르기 전에 무엇을 고르는지 안다", async () => {
    mockSummary({
      trips: [
        trip(3, "일본 가을", {
          status: "ONGOING",
          dDay: null,
          dayNumber: 4,
          prep: { total: 24, done: 18, overdueCount: 1 },
        }),
        trip(7, "도쿄 3박 4일", {
          prep: { total: 0, done: 0, overdueCount: 0 },
          expense: { budget: null, spent: 0 },
        }),
      ],
    });

    renderAt("/travel/prep");

    const ongoing = await main().findByRole("link", { name: /일본 가을/ });
    expect(ongoing).toHaveTextContent("진행 중");
    expect(ongoing).toHaveTextContent(
      "10.24 – 10.29 · 준비 18/24 · 경비 41.2만",
    );
    expect(ongoing).toHaveTextContent("기한 지난 것 1개");
    expect(ongoing).toHaveTextContent("4일차");

    // 준비도 경비도 없는 여행은 그 조각을 아예 빼고 기간만 남긴다.
    const upcoming = main().getByRole("link", { name: /도쿄 3박 4일/ });
    expect(upcoming).toHaveTextContent("예정");
    expect(upcoming).toHaveTextContent("10.24 – 10.29");
    expect(upcoming).not.toHaveTextContent("준비");
    expect(upcoming).toHaveTextContent("D-49");

    expect(
      screen.getByText("한 번 고르면 그 여행을 기억합니다."),
    ).toBeVisible();
  });

  it("고르면 그 여행이 열리고, 그 여행을 기억한다", async () => {
    mockSummary({ trips: [trip(3, "일본 가을"), trip(7, "도쿄")] });
    mockPrepScreen(7);
    const user = userEvent.setup();

    renderAt("/travel/prep");

    await user.click(await main().findByRole("link", { name: /도쿄/ }));
    await waitFor(() => {
      expect(pathname()).toBe("/travel/trips/7/prep");
    });
    // 「한 번 고르면 그 여행을 기억합니다」가 참말이 되는 자리다. 다음에 여행 id 없이
    // 들어오면 사이드바가 이 값으로 그 여행을 편다(Sidebar.test).
    await waitFor(() => {
      expect(localStorage.getItem("travel.lastTripId")).toBe("7");
    });
  });

  it("여행이 하나도 없으면 왜 비어 있는지 말해 준다 — 빈 화면이 아니다", async () => {
    mockSummary({ trips: [] });

    renderAt("/travel/prep");

    expect(
      await screen.findByText(
        "준비 목록은 여행마다 따로 있어요. 여행을 먼저 만들면 여기에 목록이 생깁니다.",
      ),
    ).toBeVisible();
    expect(main().getByRole("link", { name: /여행 만들기/ })).toHaveAttribute(
      "href",
      "/travel/trips/new",
    );
  });

  it("경비도 같은 화면을 쓴다 — 묻는 말과 가는 곳만 다르다", async () => {
    mockSummary({ trips: [trip(3, "일본 가을")] });

    renderAt("/travel/expenses");

    expect(
      await screen.findByText("어느 여행의 경비인가요?"),
    ).toBeInTheDocument();
    expect(main().getByRole("link", { name: /일본 가을/ })).toHaveAttribute(
      "href",
      "/travel/trips/3/expenses",
    );
  });

  it("없는 여행의 준비로 들어오면 목록이 아니라 고르는 화면으로 보낸다", async () => {
    mockSummary({ trips: [trip(3, "일본 가을"), trip(7, "도쿄")] });
    server.use(
      http.get(`${API_BASE}/travel/trips/99/prep`, () =>
        HttpResponse.json(
          { code: "TRAVEL-ERR-001", message: "존재하지 않는 여행입니다." },
          { status: 404 },
        ),
      ),
    );

    renderAt("/travel/trips/99/prep");

    expect(
      await screen.findByText("어느 여행의 준비인가요?"),
    ).toBeInTheDocument();
    expect(pathname()).toBe("/travel/prep");
  });

  it("없는 여행의 경비도 마찬가지다", async () => {
    mockSummary({ trips: [trip(3, "일본 가을"), trip(7, "도쿄")] });
    server.use(
      http.get(`${API_BASE}/travel/trips/99/expenses`, () =>
        HttpResponse.json(
          { code: "TRAVEL-ERR-001", message: "존재하지 않는 여행입니다." },
          { status: 404 },
        ),
      ),
    );

    renderAt("/travel/trips/99/expenses");

    expect(
      await screen.findByText("어느 여행의 경비인가요?"),
    ).toBeInTheDocument();
    expect(pathname()).toBe("/travel/expenses");
  });
});
