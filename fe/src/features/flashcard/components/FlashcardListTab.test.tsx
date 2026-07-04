import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { Toaster } from "@/components/Toaster";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { FlashcardListTab } from "./FlashcardListTab";

const API_BASE = "https://api.orino.dev/api";

function renderTab() {
  return renderWithRouter(
    <Providers>
      <>
        <FlashcardListTab materialId={1} />
        <Toaster />
      </>
    </Providers>,
  );
}

function mockListEmpty() {
  server.use(
    http.get(`${API_BASE}/planner/materials/1/flashcards`, () => {
      return HttpResponse.json({ code: "OK", data: { flashcards: [] } });
    }),
  );
}

function mockListWith(
  flashcards: Array<{
    id: number;
    front: string;
    back: string;
    nextReview?: { sequence: number; scheduledAt: string } | null;
  }>,
) {
  server.use(
    http.get(`${API_BASE}/planner/materials/1/flashcards`, () => {
      return HttpResponse.json({
        code: "OK",
        data: {
          flashcards: flashcards.map((f) => ({
            id: f.id,
            materialId: 1,
            front: f.front,
            back: f.back,
            nextReview:
              f.nextReview === null
                ? null
                : {
                    id: 100,
                    sequence: f.nextReview?.sequence ?? 1,
                    scheduledAt:
                      f.nextReview?.scheduledAt ?? "2026-05-20T04:00:00",
                    intervalDays: 1,
                    easeFactor: 2.5,
                  },
            createdAt: "2026-05-18T00:00:00",
          })),
        },
      });
    }),
  );
}

describe("FlashcardListTab", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
  });

  it("빈 목록이면 빈 상태와 [첫 카드 추가] 버튼을 표시한다", async () => {
    mockListEmpty();
    renderTab();

    await waitFor(() => {
      expect(screen.getByText("아직 카드가 없습니다.")).toBeInTheDocument();
    });
    expect(
      screen.getByRole("button", { name: /첫 카드 추가/ }),
    ).toBeInTheDocument();
  });

  it("카드 목록과 다음 복습 텍스트를 표시한다", async () => {
    mockListWith([
      {
        id: 1,
        front: "Q1",
        back: "A1",
        nextReview: { sequence: 2, scheduledAt: "2026-05-24T04:00:00" },
      },
      { id: 2, front: "Q2", back: "A2", nextReview: null },
    ]);
    renderTab();

    await waitFor(() => {
      expect(screen.getByText("Q1")).toBeInTheDocument();
    });
    expect(screen.getByText("뒤: A1")).toBeInTheDocument();
    expect(screen.getByText(/다음 복습: 5\/24/)).toBeInTheDocument();
    expect(screen.getByText("Q2")).toBeInTheDocument();
    expect(screen.getByText("총 2장")).toBeInTheDocument();
  });

  it("[카드 추가] 다이얼로그를 열어 카드 생성 후 토스트가 표시된다", async () => {
    mockListEmpty();
    let posted: { front: string; back: string } | null = null;
    server.use(
      http.post(
        `${API_BASE}/planner/materials/1/flashcards`,
        async ({ request }) => {
          posted = (await request.json()) as { front: string; back: string };
          return HttpResponse.json(
            {
              code: "OK",
              data: {
                flashcard: {
                  id: 10,
                  materialId: 1,
                  front: posted.front,
                  back: posted.back,
                  nextReview: null,
                  createdAt: "2026-05-18T00:00:00",
                },
                firstReview: {
                  id: 100,
                  flashcardId: 10,
                  sequence: 1,
                  scheduledAt: "2026-05-19T04:00:00",
                  intervalDays: 1,
                  easeFactor: 2.5,
                  status: "PENDING",
                },
              },
            },
            { status: 201 },
          );
        },
      ),
    );

    const user = userEvent.setup();
    renderTab();

    await waitFor(() => {
      expect(screen.getByText("아직 카드가 없습니다.")).toBeInTheDocument();
    });
    await user.click(screen.getByRole("button", { name: /첫 카드 추가/ }));

    await user.type(await screen.findByLabelText("앞면 (질문)"), "front-text");
    await user.type(screen.getByLabelText("뒷면 (답)"), "back-text");
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(posted).toEqual({ front: "front-text", back: "back-text" });
    });
    expect(await screen.findByText(/카드가 추가되었어요/)).toBeInTheDocument();
  });

  it("카드 생성 실패 시 인라인 에러 대신 토스트로 알린다", async () => {
    mockListEmpty();
    server.use(
      http.post(`${API_BASE}/planner/materials/1/flashcards`, () =>
        HttpResponse.json(
          { code: "ERR", message: "server error" },
          { status: 500 },
        ),
      ),
    );

    const user = userEvent.setup();
    renderTab();

    await waitFor(() => {
      expect(screen.getByText("아직 카드가 없습니다.")).toBeInTheDocument();
    });
    await user.click(screen.getByRole("button", { name: /첫 카드 추가/ }));
    await user.type(await screen.findByLabelText("앞면 (질문)"), "Q");
    await user.type(screen.getByLabelText("뒷면 (답)"), "A");
    await user.click(screen.getByRole("button", { name: "저장" }));

    // 다이얼로그 내부에 밀림을 유발하는 인라인 에러 없이 토스트로만 알린다
    expect(
      await screen.findByText(/카드 추가에 실패했어요/),
    ).toBeInTheDocument();
  });

  it("[편집] 후 [삭제] 클릭 시 확인 → DELETE 호출", async () => {
    mockListWith([
      {
        id: 7,
        front: "Q",
        back: "A",
        nextReview: { sequence: 1, scheduledAt: "2026-05-20T04:00:00" },
      },
    ]);
    let deleted = false;
    server.use(
      http.delete(`${API_BASE}/planner/flashcards/7`, () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    const user = userEvent.setup();
    renderTab();

    await waitFor(() => {
      expect(screen.getByText("Q")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /카드 1 편집/ }));
    await user.click(await screen.findByRole("button", { name: "삭제" }));
    await user.click(await screen.findByRole("button", { name: "삭제" }));

    await waitFor(() => expect(deleted).toBe(true));
  });

  it("앞면 또는 뒷면이 비어 있으면 [저장] 비활성", async () => {
    mockListEmpty();
    const user = userEvent.setup();
    renderTab();

    await waitFor(() => {
      expect(screen.getByText("아직 카드가 없습니다.")).toBeInTheDocument();
    });
    await user.click(screen.getByRole("button", { name: /첫 카드 추가/ }));

    expect(await screen.findByRole("button", { name: "저장" })).toBeDisabled();

    await user.type(await screen.findByLabelText("앞면 (질문)"), "Q");
    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();

    await user.type(screen.getByLabelText("뒷면 (답)"), "A");
    expect(screen.getByRole("button", { name: "저장" })).toBeEnabled();
  });
});
