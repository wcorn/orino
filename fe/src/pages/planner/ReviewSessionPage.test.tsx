import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { Providers } from "@/app/providers";
import { Toaster } from "@/components/Toaster";
import { useAuthStore } from "@/features/auth/store/authStore";
import { useToastStore } from "@/shared/lib/toast";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { ReviewSessionPage } from "./ReviewSessionPage";

const API_BASE = "https://api.orino.dev/api";

function renderPage(entry = "/planner/reviews/session") {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route
          path="/planner/reviews/session"
          element={
            <>
              <ReviewSessionPage />
              <Toaster />
            </>
          }
        />
        <Route path="/home" element={<div>홈 페이지</div>} />
        <Route path="/planner/materials" element={<div>목록 페이지</div>} />
      </Routes>
    </Providers>,
    { initialEntries: [entry] },
  );
}

interface ReviewSpec {
  id: number;
  delayDays?: number;
  materialId?: number;
  ordering?: boolean;
}

function makeReview({
  id,
  delayDays = 0,
  materialId = 1,
  ordering = false,
}: ReviewSpec) {
  return {
    id,
    scheduledAt: "2026-05-18T04:00:00",
    delayDays,
    sequence: 2,
    intervalDays: 6,
    easeFactor: 2.5,
    flashcard: {
      id: id * 10,
      type: ordering ? "ORDERING" : "BASIC",
      front: `질문 ${id}`,
      back: ordering ? null : `답 ${id}`,
      items: ordering
        ? [
            { id: `${id}-a`, text: `항목 A${id}` },
            { id: `${id}-b`, text: `항목 B${id}` },
          ]
        : null,
      siblingGroupId: null,
      material: { id: materialId, title: `자료 ${materialId}`, type: "BOOK" },
    },
    // BE가 등급별로 다른 간격을 준다(#1001) — 직전 6일·ease 2.50 카드를 제때 볼 때의 실제 값
    preview: { again: 1, hard: 7, good: 15, easy: 20 },
  };
}

type User = ReturnType<typeof userEvent.setup>;

/**
 * 답을 여는 단축키. 기본 흐름에선 답 입력칸에 포커스가 있어 Space가 그리로 들어가므로,
 * 입력칸 안에서도 통하는 ⌘/Ctrl+Enter를 쓴다(Enter는 줄바꿈이라 못 쓴다).
 */
async function revealAnswer(user: User) {
  await user.keyboard("{Control>}{Enter}{/Control}");
}

function mockToday(specs: ReviewSpec[]) {
  server.use(
    http.get(`${API_BASE}/planner/reviews/today`, () =>
      HttpResponse.json({
        code: "OK",
        data: { today: "2026-05-18", reviews: specs.map(makeReview) },
      }),
    ),
  );
}

function mockComplete(
  captureRatings: Array<{ id: number; rating: string }>,
  buriedReviewIds: number[] = [],
) {
  server.use(
    http.post(
      `${API_BASE}/planner/reviews/:id/complete`,
      async ({ params, request }) => {
        const body = (await request.json()) as { rating: string };
        captureRatings.push({ id: Number(params.id), rating: body.rating });
        return HttpResponse.json({
          code: "OK",
          data: {
            completed: {
              id: Number(params.id),
              status: "COMPLETED",
              rating: body.rating,
              elapsedDays: 0,
              completedAt: "2026-05-18T10:00:00",
            },
            nextReview: {
              id: 999,
              flashcardId: 1,
              sequence: 3,
              scheduledAt: "2026-05-24T04:00:00",
              intervalDays: 6,
              easeFactor: 2.5,
              status: "PENDING",
            },
            buriedReviewIds,
          },
        });
      },
    ),
  );
}

describe("ReviewSessionPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
    useToastStore.setState({ toasts: [] });
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 4, 18, 9, 0, 0));
    // 큐 셔플(Fisher–Yates)을 결정적으로 만든다 — rng=0.99면 항상 항등 순열이라 서버 순서가
    // 그대로 유지돼 순서 의존 검증이 안정적이다. 셔플이 실제로 적용되는지는 별도 테스트에서 확인.
    vi.spyOn(Math, "random").mockReturnValue(0.99);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("0건이면 빈 상태와 [홈으로] 버튼을 표시한다", async () => {
    mockToday([]);
    renderPage();

    await waitFor(() => {
      expect(
        screen.getByText("오늘은 복습할 카드가 없어요! 🌱"),
      ).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: "홈으로" })).toBeInTheDocument();
  });

  it("진입 시 첫 카드 앞면 + [답 보기] 버튼만 보인다", async () => {
    mockToday([{ id: 1 }, { id: 2 }]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("질문 1")).toBeInTheDocument();
    });
    expect(screen.getByText("1 / 2")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /답 보기/ })).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Again/ }),
    ).not.toBeInTheDocument();
  });

  it("세션 진입 시 카드 순서를 섞는다(서버 순서와 다르게 나올 수 있다)", async () => {
    // rng=0이면 2장 덱 [1,2]가 [2,1]로 뒤집힌다 — 서버가 준 순서를 그대로 쓰지 않음을 확인.
    vi.spyOn(Math, "random").mockReturnValue(0);
    mockToday([{ id: 1 }, { id: 2 }]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("질문 2")).toBeInTheDocument();
    });
    expect(screen.getByText("1 / 2")).toBeInTheDocument();
    expect(screen.queryByText("질문 1")).not.toBeInTheDocument();
  });

  it("⌘/Ctrl+Enter로 답이 공개되고 1/2/3/4 키로 평가가 전송된다", async () => {
    mockToday([{ id: 1 }]);
    const ratings: Array<{ id: number; rating: string }> = [];
    mockComplete(ratings);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("질문 1")).toBeInTheDocument();
    });

    await revealAnswer(user);
    expect(await screen.findByText("답 1")).toBeInTheDocument();

    // 공개 시 입력칸에서 포커스가 빠져야 숫자 키가 입력이 아닌 채점으로 간다
    await user.keyboard("3");
    await waitFor(() => {
      expect(ratings).toEqual([{ id: 1, rating: "GOOD" }]);
    });
  });

  it("평가 후 다음 카드로 넘어가고, 마지막 후 완료 화면이 보인다", async () => {
    mockToday([{ id: 1 }, { id: 2 }]);
    mockComplete([]);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("질문 1")).toBeInTheDocument();
    });

    await revealAnswer(user);
    await user.click(await screen.findByRole("button", { name: /Good/ }));

    await waitFor(() => {
      expect(screen.getByText("질문 2")).toBeInTheDocument();
    });

    await revealAnswer(user);
    await user.click(await screen.findByRole("button", { name: /Good/ }));

    await waitFor(() => {
      expect(screen.getByText(/모두 완료/)).toBeInTheDocument();
    });
  });

  it("scope=overdue면 밀린 카드(delayDays>0)만 큐에 담긴다", async () => {
    mockToday([
      { id: 1, delayDays: 0 },
      { id: 2, delayDays: 3 },
    ]);
    renderPage("/planner/reviews/session?scope=overdue");

    await waitFor(() => {
      expect(screen.getByText("질문 2")).toBeInTheDocument();
    });
    expect(screen.getByText("1 / 1")).toBeInTheDocument();
    expect(screen.queryByText("질문 1")).not.toBeInTheDocument();
  });

  it("materialId로 그 자료 카드만 큐에 담긴다", async () => {
    mockToday([
      { id: 1, materialId: 1 },
      { id: 2, materialId: 2 },
    ]);
    renderPage("/planner/reviews/session?materialId=2");

    await waitFor(() => {
      expect(screen.getByText("질문 2")).toBeInTheDocument();
    });
    expect(screen.getByText("1 / 1")).toBeInTheDocument();
    expect(screen.queryByText("질문 1")).not.toBeInTheDocument();
  });

  it("필터 결과가 0건이면 빈 상태를 보여준다", async () => {
    mockToday([{ id: 1, delayDays: 0 }]);
    renderPage("/planner/reviews/session?scope=overdue");

    await waitFor(() => {
      expect(
        screen.getByText("오늘은 복습할 카드가 없어요! 🌱"),
      ).toBeInTheDocument();
    });
  });

  it("짝 카드가 밀리면 큐에서 제거되고 카운터 분모가 감소한다", async () => {
    mockToday([{ id: 1 }, { id: 2 }, { id: 3 }]);
    mockComplete([], [2]);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("질문 1")).toBeInTheDocument();
    });
    expect(screen.getByText("1 / 3")).toBeInTheDocument();

    await revealAnswer(user);
    await user.click(await screen.findByRole("button", { name: /Good/ }));

    await waitFor(() => {
      expect(screen.getByText("질문 3")).toBeInTheDocument();
    });
    expect(screen.getByText("2 / 2")).toBeInTheDocument();
    expect(screen.queryByText("질문 2")).not.toBeInTheDocument();
    expect(
      await screen.findByText(/짝 카드는 다른 날 복습해요/),
    ).toBeInTheDocument();
  });

  describe("답 적기", () => {
    it("적은 답이 공개 후에도 정답과 함께 남는다", async () => {
      mockToday([{ id: 1 }]);
      const user = userEvent.setup();
      renderPage();

      const note = await screen.findByLabelText("내 답");
      await user.type(note, "내가 떠올린 답");
      await revealAnswer(user);

      // 입력칸은 사라지고 적은 내용이 읽기 전용으로 남아 정답과 나란히 보인다
      expect(screen.queryByLabelText("내 답")).not.toBeInTheDocument();
      expect(screen.getByText("내가 떠올린 답")).toBeInTheDocument();
      expect(screen.getByText("답 1")).toBeInTheDocument();
    });

    it("채점하면 다음 카드의 입력칸이 비워진다", async () => {
      mockToday([{ id: 1 }, { id: 2 }]);
      mockComplete([]);
      const user = userEvent.setup();
      renderPage();

      await user.type(
        await screen.findByLabelText("내 답"),
        "1번 카드에 쓴 답",
      );
      await revealAnswer(user);
      await user.click(await screen.findByRole("button", { name: /Good/ }));

      await waitFor(() => {
        expect(screen.getByText("질문 2")).toBeInTheDocument();
      });
      expect(await screen.findByLabelText("내 답")).toHaveValue("");
      expect(screen.queryByText("1번 카드에 쓴 답")).not.toBeInTheDocument();
    });

    it("빈칸이어도 답을 볼 수 있고, 이때 '내 답'은 남지 않는다", async () => {
      mockToday([{ id: 1 }]);
      const user = userEvent.setup();
      renderPage();

      await waitFor(() => {
        expect(screen.getByText("질문 1")).toBeInTheDocument();
      });
      await user.click(screen.getByRole("button", { name: /답 보기/ }));

      expect(await screen.findByText("답 1")).toBeInTheDocument();
      expect(screen.queryByText("내 답")).not.toBeInTheDocument();
    });

    it("순서 카드에도 입력칸이 있다", async () => {
      mockToday([{ id: 1, ordering: true }]);
      const user = userEvent.setup();
      renderPage();

      const note = await screen.findByLabelText("내 답");
      await user.type(note, "A가 먼저다");
      await user.click(screen.getByRole("button", { name: /정답 확인/ }));

      expect(await screen.findByText("정답 순서")).toBeInTheDocument();
      expect(screen.getByText("A가 먼저다")).toBeInTheDocument();
    });

    it("입력칸 밖에 포커스가 있으면 Space로도 답이 열린다", async () => {
      mockToday([{ id: 1 }]);
      const user = userEvent.setup();
      renderPage();

      await waitFor(() => {
        expect(screen.getByText("질문 1")).toBeInTheDocument();
      });
      // 자동 포커스된 입력칸에서 포커스를 뺀다(기존 Space 단축키가 그대로 사는지)
      (document.activeElement as HTMLElement | null)?.blur();

      await user.keyboard(" ");
      expect(await screen.findByText("답 1")).toBeInTheDocument();
    });

    it("터치 기기에서는 입력칸에 자동 포커스하지 않는다", async () => {
      const originalMatchMedia = window.matchMedia;
      window.matchMedia = ((query: string) => ({
        matches: query.includes("pointer: coarse"),
        media: query,
        addEventListener: () => {},
        removeEventListener: () => {},
        addListener: () => {},
        removeListener: () => {},
        onchange: null,
        dispatchEvent: () => false,
      })) as unknown as typeof window.matchMedia;

      try {
        mockToday([{ id: 1 }]);
        renderPage();

        const note = await screen.findByLabelText("내 답");
        // 가상 키보드가 질문을 덮지 않도록 포커스를 주지 않는다
        expect(note).not.toHaveFocus();
      } finally {
        window.matchMedia = originalMatchMedia;
      }
    });
  });
});
