import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { Route, Routes, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { useAuthStore } from "@/features/auth/store/authStore";
import { triggerIntersection } from "@/test/io";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { ReviewHubPage } from "./ReviewHubPage";

const API_BASE = "https://api.orino.dev/api";

const SUMMARY = {
  today: "2026-05-18",
  counts: { now: 2, overdue: 1, upcoming: 3, doneToday: 1 },
  estimatedMinutes: 1,
  materials: [
    { id: 1, name: "이펙티브 자바", due: 2, overdue: 1, nextLabel: "지금" },
    { id: 2, name: "모던 자바", due: 0, overdue: 0, nextLabel: "07/14" },
  ],
};

function upcomingItem(
  id: number,
  overrides: Partial<{
    front: string;
    cardType: "BASIC" | "ORDERING" | "PAIR";
    whenKind: "now" | "today" | "future";
    overdue: boolean;
    materialId: number;
  }> = {},
) {
  return {
    id,
    scheduledAt: "2026-05-18T04:00:00",
    whenKind: overrides.whenKind ?? "now",
    overdue: overrides.overdue ?? false,
    cardType: overrides.cardType ?? "BASIC",
    flashcard: {
      id: id * 10,
      type: overrides.cardType === "ORDERING" ? "ORDERING" : "BASIC",
      front: overrides.front ?? `질문 ${id}`,
      siblingGroupId: overrides.cardType === "PAIR" ? 700 : null,
      material: {
        id: overrides.materialId ?? 1,
        title: "이펙티브 자바",
        type: "BOOK",
      },
    },
  };
}

function completedItem(id: number, rating: string) {
  return {
    id,
    completedAt: "2026-05-18T09:12:00",
    rating,
    sequence: 2,
    cardType: "BASIC",
    flashcard: {
      id: id * 10,
      type: "BASIC",
      front: `완료 ${id}`,
      siblingGroupId: null,
      material: { id: 1, title: "이펙티브 자바", type: "BOOK" },
    },
  };
}

function mockSummary(data = SUMMARY) {
  server.use(
    http.get(`${API_BASE}/planner/reviews/summary`, () =>
      HttpResponse.json({ code: "OK", data }),
    ),
  );
}

function SessionMarker() {
  const location = useLocation();
  return <div>세션{location.search}</div>;
}

function renderHub() {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route path="/planner/reviews" element={<ReviewHubPage />} />
        <Route path="/planner/reviews/session" element={<SessionMarker />} />
      </Routes>
    </Providers>,
    { initialEntries: ["/planner/reviews"] },
  );
}

describe("ReviewHubPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
  });

  it("요약 수치(상태 4행)와 자료별 목록을 표시한다", async () => {
    mockSummary();
    server.use(
      http.get(`${API_BASE}/planner/reviews/upcoming`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            today: "2026-05-18",
            items: [upcomingItem(1)],
            hasNext: false,
          },
        }),
      ),
    );
    renderHub();

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "복습", level: 1 }),
      ).toBeInTheDocument();
    });
    // 상태 행 + CTA 수치
    expect(screen.getByText("지금 복습 2장")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /지금 할 것/ }),
    ).toBeInTheDocument();
    // 자료별 행
    expect(
      screen.getByRole("button", { name: /이펙티브 자바/ }),
    ).toBeInTheDocument();
  });

  it("앞으로 목록을 렌더하고, 완료 탭으로 전환하면 완료 목록이 보인다", async () => {
    mockSummary();
    server.use(
      http.get(`${API_BASE}/planner/reviews/upcoming`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            today: "2026-05-18",
            items: [upcomingItem(1, { front: "앞으로 질문" })],
            hasNext: false,
          },
        }),
      ),
      http.get(`${API_BASE}/planner/reviews/completed`, () =>
        HttpResponse.json({
          code: "OK",
          data: { items: [completedItem(5, "GOOD")], hasNext: false },
        }),
      ),
    );
    const user = userEvent.setup();
    renderHub();

    expect(await screen.findByText("앞으로 질문")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: /완료/ }));

    expect(await screen.findByText("완료 5")).toBeInTheDocument();
    expect(screen.getByText("양호")).toBeInTheDocument();
  });

  it("'밀림' 상태 행 클릭 시 scope=overdue로 조회하고 스코프 칩을 보여준다", async () => {
    mockSummary();
    let lastScope: string | null = null;
    server.use(
      http.get(`${API_BASE}/planner/reviews/upcoming`, ({ request }) => {
        lastScope = new URL(request.url).searchParams.get("scope");
        return HttpResponse.json({
          code: "OK",
          data: {
            today: "2026-05-18",
            items: [upcomingItem(1, { overdue: true })],
            hasNext: false,
          },
        });
      }),
    );
    const user = userEvent.setup();
    renderHub();

    await waitFor(() => expect(lastScope).toBe("all"));

    await user.click(screen.getByRole("button", { name: /밀림/ }));

    await waitFor(() => expect(lastScope).toBe("overdue"));
    expect(screen.getByText("밀린 카드만 보는 중")).toBeInTheDocument();

    // 칩의 ✕로 스코프 해제 → 칩이 사라진다
    await user.click(screen.getByRole("button", { name: "스코프 해제" }));
    await waitFor(() =>
      expect(screen.queryByText("밀린 카드만 보는 중")).not.toBeInTheDocument(),
    );
  });

  it("자료 행 클릭 시 materialId로 필터하고 시작 배너를 띄운다", async () => {
    mockSummary();
    let lastMaterialId: string | null = null;
    server.use(
      http.get(`${API_BASE}/planner/reviews/upcoming`, ({ request }) => {
        lastMaterialId = new URL(request.url).searchParams.get("materialId");
        return HttpResponse.json({
          code: "OK",
          data: {
            today: "2026-05-18",
            items: [upcomingItem(1)],
            hasNext: false,
          },
        });
      }),
    );
    const user = userEvent.setup();
    renderHub();

    await screen.findByText("질문 1");
    await user.click(screen.getByRole("button", { name: /이펙티브 자바/ }));

    await waitFor(() => expect(lastMaterialId).toBe("1"));
    expect(
      screen.getByText(/이펙티브 자바 · 오늘 2장 복습을 시작합니다/),
    ).toBeInTheDocument();
  });

  it("무한 스크롤: sentinel 교차 시 다음 페이지를 이어붙인다", async () => {
    mockSummary();
    server.use(
      http.get(`${API_BASE}/planner/reviews/upcoming`, ({ request }) => {
        const cursor = new URL(request.url).searchParams.get("cursor");
        if (!cursor) {
          return HttpResponse.json({
            code: "OK",
            data: {
              today: "2026-05-18",
              items: [upcomingItem(1), upcomingItem(2)],
              nextCursor: "c2",
              hasNext: true,
            },
          });
        }
        return HttpResponse.json({
          code: "OK",
          data: {
            today: "2026-05-18",
            items: [upcomingItem(3)],
            hasNext: false,
          },
        });
      }),
    );
    renderHub();

    expect(await screen.findByText("질문 1")).toBeInTheDocument();
    expect(screen.getByText("질문 2")).toBeInTheDocument();
    expect(screen.queryByText("질문 3")).not.toBeInTheDocument();

    triggerIntersection();

    expect(await screen.findByText("질문 3")).toBeInTheDocument();
  });

  it("결과가 없으면 빈 상태와 [필터 초기화]를 보여준다", async () => {
    mockSummary();
    server.use(
      http.get(`${API_BASE}/planner/reviews/upcoming`, () =>
        HttpResponse.json({
          code: "OK",
          data: { today: "2026-05-18", items: [], hasNext: false },
        }),
      ),
    );
    renderHub();

    expect(
      await screen.findByText("조건에 맞는 복습이 없어요."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "필터 초기화" }),
    ).toBeInTheDocument();
  });

  it("[전체 복습 시작]은 scope=all 세션으로 이동한다", async () => {
    mockSummary();
    server.use(
      http.get(`${API_BASE}/planner/reviews/upcoming`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            today: "2026-05-18",
            items: [upcomingItem(1)],
            hasNext: false,
          },
        }),
      ),
    );
    const user = userEvent.setup();
    renderHub();

    await user.click(
      await screen.findByRole("button", { name: "전체 복습 시작" }),
    );

    expect(await screen.findByText("세션?scope=all")).toBeInTheDocument();
  });
});
