import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { Toaster } from "@/components/Toaster";
import { useAuthStore } from "@/features/auth/store/authStore";
import { useToastStore } from "@/shared/lib/toast";
import { triggerIntersection } from "@/test/io";
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
      return HttpResponse.json({
        code: "OK",
        data: { flashcards: [], totalCount: 0, hasNext: false },
      });
    }),
  );
}

interface MockCard {
  id: number;
  front: string;
  back: string;
  siblingGroupId?: number;
  nextReview?: { sequence: number; scheduledAt: string } | null;
}

function toCard(f: MockCard) {
  return {
    id: f.id,
    materialId: 1,
    type: "BASIC",
    front: f.front,
    back: f.back,
    items: null,
    siblingGroupId: f.siblingGroupId ?? null,
    nextReview:
      f.nextReview === null
        ? null
        : {
            id: 100,
            sequence: f.nextReview?.sequence ?? 1,
            scheduledAt: f.nextReview?.scheduledAt ?? "2026-05-20T04:00:00",
            intervalDays: 1,
            easeFactor: 2.5,
          },
    createdAt: "2026-05-18T00:00:00",
  };
}

function mockListWith(flashcards: MockCard[]) {
  server.use(
    http.get(`${API_BASE}/planner/materials/1/flashcards`, () => {
      return HttpResponse.json({
        code: "OK",
        data: {
          flashcards: flashcards.map(toCard),
          totalCount: flashcards.length,
          hasNext: false,
        },
      });
    }),
  );
}

/**
 * 서버가 필터·페이징의 SSOT임을 검증하기 위해, 매 요청의 쿼리 파라미터를 기록한다.
 * 반환한 배열의 마지막 항목이 가장 최근 요청이다.
 */
function recordListRequests(
  respond: (params: URLSearchParams) => {
    flashcards: MockCard[];
    totalCount?: number;
    hasNext?: boolean;
    nextCursor?: string;
  },
) {
  const requests: URLSearchParams[] = [];
  server.use(
    http.get(`${API_BASE}/planner/materials/1/flashcards`, ({ request }) => {
      const params = new URL(request.url).searchParams;
      requests.push(params);
      const result = respond(params);
      return HttpResponse.json({
        code: "OK",
        data: {
          flashcards: result.flashcards.map(toCard),
          totalCount: result.totalCount ?? result.flashcards.length,
          hasNext: result.hasNext ?? false,
          ...(result.nextCursor ? { nextCursor: result.nextCursor } : {}),
        },
      });
    }),
  );
  return requests;
}

describe("FlashcardListTab", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
    // 전역 toast 스토어는 3.5s 유지되어 테스트 간 누적되므로 초기화
    useToastStore.setState({ toasts: [] });
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

  it("카드 목록은 앞면·다음 복습만 보이고 뒷면은 접혀 있다", async () => {
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
    expect(screen.getByText(/다음 복습: 5\/24/)).toBeInTheDocument();
    expect(screen.getByText("Q2")).toBeInTheDocument();
    expect(screen.getByText("총 2장")).toBeInTheDocument();
    // 접힌 상태에서는 뒷면이 렌더되지 않는다 — 행 높이를 낮추는 게 접기의 목적
    expect(screen.queryByText("뒤: A1")).not.toBeInTheDocument();
  });

  it("행을 클릭하면 뒷면이 펼쳐지고, 다시 누르면 접힌다", async () => {
    mockListWith([{ id: 1, front: "Q1", back: "A1", nextReview: null }]);
    const user = userEvent.setup();
    renderTab();

    await waitFor(() => {
      expect(screen.getByText("Q1")).toBeInTheDocument();
    });

    const toggle = screen.getByRole("button", { expanded: false });
    await user.click(toggle);
    expect(await screen.findByText("뒤: A1")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { expanded: true }));
    await waitFor(() => {
      expect(screen.queryByText("뒤: A1")).not.toBeInTheDocument();
    });
  });

  it("양방향 짝 2장은 ⇄ 배지가 붙은 한 행으로 묶인다", async () => {
    mockListWith([
      { id: 1, front: "정의", back: "설명", siblingGroupId: 1 },
      { id: 2, front: "설명", back: "정의", siblingGroupId: 1 },
      { id: 3, front: "단독", back: "혼자" },
    ]);
    const user = userEvent.setup();
    renderTab();

    await waitFor(() => {
      expect(screen.getByText("단독")).toBeInTheDocument();
    });

    // 카드는 3장이지만 행은 2개(짝 1 + 단독 1)
    expect(screen.getAllByRole("listitem")).toHaveLength(2);
    expect(screen.getByText("⇄ 양방향")).toBeInTheDocument();

    // 펼치면 두 방향이 각각 편집 가능하게 나온다
    await user.click(screen.getAllByRole("button", { expanded: false })[0]);
    expect(await screen.findByText("정의 → 설명")).toBeInTheDocument();
    expect(screen.getByText("설명 → 정의")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "정의 카드 편집" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "설명 카드 편집" }),
    ).toBeInTheDocument();
  });

  it("[카드 추가] 다이얼로그를 열어 카드 생성 후 토스트가 표시된다", async () => {
    mockListEmpty();
    let posted: Record<string, unknown> | null = null;
    server.use(
      http.post(
        `${API_BASE}/planner/materials/1/flashcards`,
        async ({ request }) => {
          posted = (await request.json()) as Record<string, unknown>;
          return HttpResponse.json(
            {
              code: "OK",
              data: {
                flashcard: {
                  id: 10,
                  materialId: 1,
                  type: "BASIC",
                  front: posted.front,
                  back: posted.back,
                  items: null,
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
      expect(posted).toEqual({
        type: "BASIC",
        front: "front-text",
        back: "back-text",
      });
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

    await user.click(screen.getByRole("button", { name: "Q 카드 편집" }));
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

  it("순서 카드를 생성하면 type/items 페이로드가 화면 순서대로 전송된다", async () => {
    mockListEmpty();
    let posted: Record<string, unknown> | null = null;
    server.use(
      http.post(
        `${API_BASE}/planner/materials/1/flashcards`,
        async ({ request }) => {
          posted = (await request.json()) as Record<string, unknown>;
          return HttpResponse.json(
            {
              code: "OK",
              data: {
                flashcard: {
                  id: 20,
                  materialId: 1,
                  type: "ORDERING",
                  front: "순서대로",
                  back: null,
                  items: (posted as { items: unknown }).items,
                  nextReview: null,
                  createdAt: "2026-05-18T00:00:00",
                },
                firstReview: {
                  id: 200,
                  flashcardId: 20,
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

    // 순서 모드로 전환하면 최소 3개의 빈 항목 행이 나타난다
    await user.click(await screen.findByRole("radio", { name: "순서" }));
    await user.type(screen.getByLabelText("지시문"), "순서대로");
    await user.type(screen.getByLabelText("항목 1"), "첫째");
    await user.type(screen.getByLabelText("항목 2"), "둘째");
    await user.type(screen.getByLabelText("항목 3"), "셋째");

    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(posted).not.toBeNull());
    const payload = posted as unknown as {
      type: string;
      front: string;
      items: Array<{ id: string; text: string }>;
    };
    expect(payload.type).toBe("ORDERING");
    expect(payload.front).toBe("순서대로");
    expect(payload.items.map((i) => i.text)).toEqual(["첫째", "둘째", "셋째"]);
    expect(payload.items.every((i) => typeof i.id === "string" && i.id)).toBe(
      true,
    );
    expect(await screen.findByText(/카드가 추가되었어요/)).toBeInTheDocument();
  });

  it("순서 모드: 항목이 비어 있으면 저장 비활성, 모두 채우면 활성", async () => {
    mockListEmpty();
    const user = userEvent.setup();
    renderTab();

    await waitFor(() => {
      expect(screen.getByText("아직 카드가 없습니다.")).toBeInTheDocument();
    });
    await user.click(screen.getByRole("button", { name: /첫 카드 추가/ }));
    await user.click(await screen.findByRole("radio", { name: "순서" }));

    await user.type(screen.getByLabelText("지시문"), "지시");
    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();

    await user.type(screen.getByLabelText("항목 1"), "a");
    await user.type(screen.getByLabelText("항목 2"), "b");
    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();

    await user.type(screen.getByLabelText("항목 3"), "c");
    expect(screen.getByRole("button", { name: "저장" })).toBeEnabled();
  });

  it("순서 모드: 7개 도달 시 [항목 추가]가 비활성된다", async () => {
    mockListEmpty();
    const user = userEvent.setup();
    renderTab();

    await waitFor(() => {
      expect(screen.getByText("아직 카드가 없습니다.")).toBeInTheDocument();
    });
    await user.click(screen.getByRole("button", { name: /첫 카드 추가/ }));
    await user.click(await screen.findByRole("radio", { name: "순서" }));

    const addButton = screen.getByRole("button", { name: "항목 추가" });
    // 3개에서 시작 → 4번 추가하면 7개
    for (let i = 0; i < 4; i++) {
      await user.click(addButton);
    }
    expect(screen.getByText("7 / 7")).toBeInTheDocument();
    expect(addButton).toBeDisabled();
  });

  it("순서 카드 목록은 항목을 번호 목록으로 표시한다", async () => {
    server.use(
      http.get(`${API_BASE}/planner/materials/1/flashcards`, () => {
        return HttpResponse.json({
          code: "OK",
          data: {
            flashcards: [
              {
                id: 30,
                materialId: 1,
                type: "ORDERING",
                front: "kubectl 순서",
                back: null,
                items: [
                  { id: "a", text: "인증" },
                  { id: "b", text: "저장" },
                  { id: "c", text: "생성" },
                ],
                nextReview: null,
                createdAt: "2026-05-18T00:00:00",
              },
            ],
            totalCount: 1,
            hasNext: false,
          },
        });
      }),
    );
    const user = userEvent.setup();
    renderTab();

    await waitFor(() => {
      expect(screen.getByText("kubectl 순서")).toBeInTheDocument();
    });
    expect(screen.getByText("순서")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { expanded: false }));

    expect(await screen.findByText("인증")).toBeInTheDocument();
    expect(screen.getByText("저장")).toBeInTheDocument();
    expect(screen.getByText("생성")).toBeInTheDocument();
    // 기본 카드의 "뒤:" 프리픽스는 나타나지 않는다
    expect(screen.queryByText(/^뒤:/)).not.toBeInTheDocument();
  });

  it("양방향 체크 후 생성하면 bidirectional 요청 + 2장 토스트", async () => {
    mockListEmpty();
    let posted: Record<string, unknown> | null = null;
    server.use(
      http.post(
        `${API_BASE}/planner/materials/1/flashcards`,
        async ({ request }) => {
          posted = (await request.json()) as Record<string, unknown>;
          return HttpResponse.json(
            {
              code: "OK",
              data: {
                flashcard: {
                  id: 40,
                  materialId: 1,
                  type: "BASIC",
                  front: "정의",
                  back: "설명",
                  items: null,
                  siblingGroupId: 40,
                  nextReview: null,
                  createdAt: "2026-05-18T00:00:00",
                },
                firstReview: {
                  id: 400,
                  flashcardId: 40,
                  sequence: 1,
                  scheduledAt: "2026-05-19T04:00:00",
                  intervalDays: 1,
                  easeFactor: 2.5,
                  status: "PENDING",
                },
                sibling: {
                  flashcard: {
                    id: 41,
                    materialId: 1,
                    type: "BASIC",
                    front: "설명",
                    back: "정의",
                    items: null,
                    siblingGroupId: 40,
                    nextReview: null,
                    createdAt: "2026-05-18T00:00:00",
                  },
                  firstReview: {
                    id: 401,
                    flashcardId: 41,
                    sequence: 1,
                    scheduledAt: "2026-05-20T04:00:00",
                    intervalDays: 1,
                    easeFactor: 2.5,
                    status: "PENDING",
                  },
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

    const checkbox = await screen.findByRole("checkbox", { name: /양방향/ });
    // 앞·뒤 채우기 전에는 비활성
    expect(checkbox).toBeDisabled();

    await user.type(await screen.findByLabelText("앞면 (질문)"), "정의");
    await user.type(screen.getByLabelText("뒷면 (답)"), "설명");
    expect(checkbox).toBeEnabled();

    await user.click(checkbox);
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(posted).toEqual({
        type: "BASIC",
        front: "정의",
        back: "설명",
        bidirectional: true,
      });
    });
    expect(
      await screen.findByText(/양방향 카드 2장이 추가되었어요/),
    ).toBeInTheDocument();
  });

  it("양방향 체크박스는 기본(BASIC) 추가 모드에서만 노출된다", async () => {
    mockListEmpty();
    const user = userEvent.setup();
    renderTab();

    await waitFor(() => {
      expect(screen.getByText("아직 카드가 없습니다.")).toBeInTheDocument();
    });
    await user.click(screen.getByRole("button", { name: /첫 카드 추가/ }));

    // BASIC 추가 모드: 노출
    expect(
      await screen.findByRole("checkbox", { name: /양방향/ }),
    ).toBeInTheDocument();

    // 순서 모드로 전환하면 사라진다
    await user.click(screen.getByRole("radio", { name: "순서" }));
    expect(
      screen.queryByRole("checkbox", { name: /양방향/ }),
    ).not.toBeInTheDocument();
  });

  // ===== 탐색: 검색 · 필터 · 무한 스크롤 (#992) =====

  it("검색어는 q 파라미터로 서버에 전달된다 (FE 재필터링 아님)", async () => {
    const requests = recordListRequests((params) =>
      params.get("q") === "pod"
        ? { flashcards: [{ id: 1, front: "Pod 란?", back: "최소 배포 단위" }] }
        : {
            flashcards: [
              { id: 1, front: "Pod 란?", back: "최소 배포 단위" },
              { id: 2, front: "관계없는 카드", back: "무관" },
            ],
          },
    );
    const user = userEvent.setup();
    renderTab();

    await waitFor(() => {
      expect(screen.getByText("관계없는 카드")).toBeInTheDocument();
    });

    await user.type(screen.getByLabelText("카드 검색"), "pod");

    await waitFor(() => {
      expect(requests.at(-1)?.get("q")).toBe("pod");
    });
    await waitFor(() => {
      expect(screen.queryByText("관계없는 카드")).not.toBeInTheDocument();
    });
    expect(screen.getByText("1장 찾음")).toBeInTheDocument();
  });

  it("검색은 디바운스되어 타이핑마다 요청하지 않는다", async () => {
    const requests = recordListRequests(() => ({ flashcards: [] }));
    const user = userEvent.setup();
    renderTab();

    await waitFor(() => expect(requests).toHaveLength(1));

    await user.type(screen.getByLabelText("카드 검색"), "kubernetes");

    await waitFor(() => {
      expect(requests.at(-1)?.get("q")).toBe("kubernetes");
    });
    // 10글자를 쳤지만 최초 1회 + 디바운스된 소수의 요청만 나간다
    expect(requests.length).toBeLessThan(5);
  });

  it("기본 요청에는 종류·복습·정렬 필터가 함께 실린다", async () => {
    const requests = recordListRequests(() => ({ flashcards: [] }));
    renderTab();

    await waitFor(() => expect(requests).toHaveLength(1));
    const params = requests[0];
    expect(params.get("type")).toBe("all");
    expect(params.get("review")).toBe("all");
    expect(params.get("sort")).toBe("created_asc");
    expect(params.get("q")).toBeNull();
  });

  it("검색 결과가 없으면 빈 상태 대신 [필터 초기화]를 제공한다", async () => {
    recordListRequests((params) =>
      params.get("q")
        ? { flashcards: [] }
        : { flashcards: [{ id: 1, front: "Q", back: "A" }] },
    );
    const user = userEvent.setup();
    renderTab();

    await waitFor(() => {
      expect(screen.getByText("Q")).toBeInTheDocument();
    });

    await user.type(screen.getByLabelText("카드 검색"), "없는말");

    expect(
      await screen.findByText("조건에 맞는 카드가 없어요."),
    ).toBeInTheDocument();
    // 카드가 아예 없는 상황("아직 카드가 없습니다.")과 구분한다
    expect(screen.queryByText("아직 카드가 없습니다.")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "필터 초기화" }));
    expect(await screen.findByText("Q")).toBeInTheDocument();
  });

  it("sentinel이 보이면 커서로 다음 페이지를 이어 붙인다", async () => {
    const requests = recordListRequests((params) =>
      params.get("cursor") === "cursor-1"
        ? {
            flashcards: [{ id: 2, front: "두번째", back: "A2" }],
            totalCount: 2,
            hasNext: false,
          }
        : {
            flashcards: [{ id: 1, front: "첫번째", back: "A1" }],
            totalCount: 2,
            hasNext: true,
            nextCursor: "cursor-1",
          },
    );
    renderTab();

    await waitFor(() => {
      expect(screen.getByText("첫번째")).toBeInTheDocument();
    });
    expect(screen.getByText("총 2장")).toBeInTheDocument();
    expect(screen.queryByText("두번째")).not.toBeInTheDocument();

    triggerIntersection();

    expect(await screen.findByText("두번째")).toBeInTheDocument();
    // 첫 페이지도 그대로 남아 누적된다
    expect(screen.getByText("첫번째")).toBeInTheDocument();
    expect(requests.at(-1)?.get("cursor")).toBe("cursor-1");
  });

  it("페이지 경계에 걸린 양방향 짝은 다음 페이지가 오면 한 행으로 합쳐진다", async () => {
    recordListRequests((params) =>
      params.get("cursor") === "cursor-1"
        ? {
            flashcards: [
              { id: 2, front: "설명", back: "정의", siblingGroupId: 1 },
            ],
            totalCount: 2,
            hasNext: false,
          }
        : {
            flashcards: [
              { id: 1, front: "정의", back: "설명", siblingGroupId: 1 },
            ],
            totalCount: 2,
            hasNext: true,
            nextCursor: "cursor-1",
          },
    );
    renderTab();

    // 첫 페이지엔 짝의 절반만 있어 단독 행으로 보인다
    await waitFor(() => {
      expect(screen.getByText("정의")).toBeInTheDocument();
    });
    expect(screen.queryByText("⇄ 양방향")).not.toBeInTheDocument();

    triggerIntersection();

    // 짝이 도착하면 두 행이 아니라 한 행으로 합쳐진다
    expect(await screen.findByText("⇄ 양방향")).toBeInTheDocument();
    expect(screen.getAllByRole("listitem")).toHaveLength(1);
  });
});
