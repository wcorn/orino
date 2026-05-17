import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { MaterialListPage } from "./MaterialListPage";

const API_BASE = "https://api.orino.dev/api";

function renderPage(initialEntries: string[] = ["/planner/materials"]) {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route path="/planner/materials" element={<MaterialListPage />} />
        <Route
          path="/planner/materials/:id"
          element={<div>자료 상세 페이지</div>}
        />
      </Routes>
    </Providers>,
    { initialEntries },
  );
}

function mockListEmpty() {
  server.use(
    http.get(`${API_BASE}/planner/materials`, () => {
      return HttpResponse.json({
        code: "OK",
        data: { materials: [] },
      });
    }),
  );
}

function mockListWith(
  ...materials: Array<{
    id: number;
    title: string;
    type?: string;
    flashcardCount?: number;
    dueReviewCount?: number;
  }>
) {
  server.use(
    http.get(`${API_BASE}/planner/materials`, () => {
      return HttpResponse.json({
        code: "OK",
        data: {
          materials: materials.map((m) => ({
            id: m.id,
            title: m.title,
            type: m.type ?? "BOOK",
            status: "ACTIVE",
            flashcardCount: m.flashcardCount ?? 0,
            dueReviewCount: m.dueReviewCount ?? 0,
            createdAt: "2026-05-18T00:00:00",
            updatedAt: "2026-05-18T00:00:00",
          })),
        },
      });
    }),
  );
}

describe("MaterialListPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
  });

  it("빈 목록일 때 빈 상태 메시지와 [첫 자료 추가] 버튼을 표시한다", async () => {
    mockListEmpty();
    renderPage();

    await waitFor(() => {
      expect(
        screen.getByText("아직 등록된 학습 자료가 없습니다."),
      ).toBeInTheDocument();
    });
    expect(
      screen.getByRole("button", { name: /첫 자료 추가/ }),
    ).toBeInTheDocument();
  });

  it("자료가 있으면 카드 목록을 표시하고 카드 수/오늘 복습 수를 보여준다", async () => {
    mockListWith(
      { id: 1, title: "이펙티브 자바", flashcardCount: 24, dueReviewCount: 3 },
      {
        id: 2,
        title: "스프링 강의",
        type: "LECTURE",
        flashcardCount: 0,
        dueReviewCount: 0,
      },
    );
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("이펙티브 자바")).toBeInTheDocument();
    });
    expect(screen.getByText("스프링 강의")).toBeInTheDocument();
    expect(screen.getByText("카드 24장")).toBeInTheDocument();
    expect(screen.getByText("3건")).toBeInTheDocument();
  });

  it("자료 카드 클릭 시 /planner/materials/:id?tab=note로 이동한다", async () => {
    mockListWith({ id: 42, title: "테스트 자료" });
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("테스트 자료")).toBeInTheDocument();
    });

    await user.click(
      screen.getByRole("link", { name: /테스트 자료 상세 열기/ }),
    );

    await waitFor(() => {
      expect(screen.getByText("자료 상세 페이지")).toBeInTheDocument();
    });
  });

  it("[자료 추가] 다이얼로그를 열어 자료 생성 후 상세로 이동한다", async () => {
    mockListEmpty();
    server.use(
      http.post(`${API_BASE}/planner/materials`, async ({ request }) => {
        const body = (await request.json()) as { title: string; type: string };
        expect(body.title).toBe("새 자료");
        expect(body.type).toBe("BOOK");
        return HttpResponse.json(
          {
            code: "OK",
            data: {
              material: {
                id: 99,
                title: body.title,
                type: body.type,
                status: "ACTIVE",
                flashcardCount: 0,
                dueReviewCount: 0,
                createdAt: "2026-05-18T00:00:00",
                updatedAt: "2026-05-18T00:00:00",
              },
              note: {
                id: 99,
                materialId: 99,
                content: { type: "doc", content: [] },
                updatedAt: "2026-05-18T00:00:00",
              },
            },
          },
          { status: 201 },
        );
      }),
    );

    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(
        screen.getByText("아직 등록된 학습 자료가 없습니다."),
      ).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /첫 자료 추가/ }));
    expect(
      await screen.findByRole("dialog", { name: /학습 자료 추가/ }),
    ).toBeInTheDocument();

    await user.type(screen.getByLabelText("제목"), "새 자료");
    await user.click(screen.getByRole("button", { name: "추가" }));

    await waitFor(() => {
      expect(screen.getByText("자료 상세 페이지")).toBeInTheDocument();
    });
  });

  it("제목이 비어 있으면 추가 버튼이 비활성화된다", async () => {
    mockListEmpty();
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /첫 자료 추가/ }),
      ).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /첫 자료 추가/ }));
    expect(
      await screen.findByRole("dialog", { name: /학습 자료 추가/ }),
    ).toBeInTheDocument();

    const submit = screen.getByRole("button", { name: "추가" });
    expect(submit).toBeDisabled();
  });
});
