import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { MaterialDetailPage } from "./MaterialDetailPage";

const API_BASE = "https://api.orino.dev/api";

function renderPage(
  initialEntries: string[] = ["/planner/materials/1?tab=note"],
) {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route path="/planner/materials/:id" element={<MaterialDetailPage />} />
        <Route path="/planner/materials" element={<div>목록 페이지</div>} />
      </Routes>
    </Providers>,
    { initialEntries },
  );
}

function mockMaterial(
  overrides: Partial<{
    title: string;
    flashcardCount: number;
    type: string;
  }> = {},
) {
  server.use(
    http.get(`${API_BASE}/planner/materials/1`, () => {
      return HttpResponse.json({
        code: "OK",
        data: {
          id: 1,
          title: overrides.title ?? "이펙티브 자바",
          type: overrides.type ?? "BOOK",
          status: "ACTIVE",
          flashcardCount: overrides.flashcardCount ?? 0,
          dueReviewCount: 0,
          createdAt: "2026-05-18T00:00:00",
          updatedAt: "2026-05-18T00:00:00",
        },
      });
    }),
    http.get(`${API_BASE}/planner/materials/1/notes`, () => {
      return HttpResponse.json({
        code: "OK",
        data: { notes: [] },
      });
    }),
  );
}

describe("MaterialDetailPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
  });

  it("자료 헤더와 노트/카드 탭, 카드 수를 표시한다", async () => {
    mockMaterial({ flashcardCount: 12 });
    renderPage();

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "이펙티브 자바" }),
      ).toBeInTheDocument();
    });
    expect(screen.getByRole("tab", { name: /📝 노트/ })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /카드 12/ })).toBeInTheDocument();
  });

  it("노트가 없으면 노트 탭에 빈 상태를 표시한다", async () => {
    mockMaterial();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("아직 노트가 없습니다.")).toBeInTheDocument();
    });
    expect(
      screen.getByRole("button", { name: /첫 노트 만들기/ }),
    ).toBeInTheDocument();
  });

  it("카드 탭 클릭 시 카드 목록이 보인다", async () => {
    mockMaterial();
    server.use(
      http.get(`${API_BASE}/planner/materials/1/flashcards`, () => {
        return HttpResponse.json({ code: "OK", data: { flashcards: [] } });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "이펙티브 자바" }),
      ).toBeInTheDocument();
    });

    await user.click(screen.getByRole("tab", { name: /카드/ }));

    await waitFor(() => {
      expect(screen.getByText("아직 카드가 없습니다.")).toBeInTheDocument();
    });
  });

  it("노트가 있어도 카드 탭으로 전환되고 유지된다 (자동선택이 탭을 되돌리지 않음)", async () => {
    mockMaterial({ flashcardCount: 1 });
    server.use(
      // 노트가 존재 → 노트 탭에서 첫 노트가 자동선택됨
      http.get(`${API_BASE}/planner/materials/1/notes`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            notes: [
              {
                id: 7,
                title: "노트1",
                parentId: null,
                sortOrder: 0,
                children: [],
              },
            ],
          },
        }),
      ),
      http.get(`${API_BASE}/planner/notes/7`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            id: 7,
            materialId: 1,
            parentId: null,
            title: "노트1",
            sortOrder: 0,
            content: { type: "doc", content: [] },
            updatedAt: "2026-05-31T10:00:00",
          },
        }),
      ),
      http.get(`${API_BASE}/planner/materials/1/flashcards`, () =>
        HttpResponse.json({ code: "OK", data: { flashcards: [] } }),
      ),
    );
    const user = userEvent.setup();
    renderPage();

    // 노트 탭: 자동선택으로 노트1 에디터가 열린다
    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toHaveValue("노트1");
    });

    await user.click(screen.getByRole("tab", { name: /카드/ }));

    // 카드 탭이 유지되고 (노트 에디터가 사라지고) 카드 목록이 보인다
    await waitFor(() => {
      expect(screen.getByText("아직 카드가 없습니다.")).toBeInTheDocument();
    });
    expect(screen.queryByLabelText("노트 제목")).not.toBeInTheDocument();
  });

  it("자료 메뉴 → 삭제 → 확인 시 DELETE 호출 후 목록으로 이동한다", async () => {
    mockMaterial();
    let deleted = false;
    server.use(
      http.delete(`${API_BASE}/planner/materials/1`, () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "이펙티브 자바" }),
      ).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /자료 메뉴/ }));
    await user.click(await screen.findByRole("menuitem", { name: /삭제/ }));
    await user.click(await screen.findByRole("button", { name: "삭제" }));

    await waitFor(() => {
      expect(deleted).toBe(true);
      expect(screen.getByText("목록 페이지")).toBeInTheDocument();
    });
  });

  it("자료 편집 다이얼로그에서 제목 변경 시 PATCH 호출된다", async () => {
    mockMaterial();
    const patches: Array<{ title?: string; status?: string }> = [];
    server.use(
      http.patch(`${API_BASE}/planner/materials/1`, async ({ request }) => {
        const body = (await request.json()) as {
          title?: string;
          status?: string;
        };
        patches.push(body);
        return HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            title: body.title ?? "이펙티브 자바",
            type: "BOOK",
            status: body.status ?? "ACTIVE",
            flashcardCount: 0,
            dueReviewCount: 0,
            createdAt: "2026-05-18T00:00:00",
            updatedAt: "2026-05-18T00:00:00",
          },
        });
      }),
    );

    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "이펙티브 자바" }),
      ).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /자료 메뉴/ }));
    await user.click(await screen.findByRole("menuitem", { name: /편집/ }));

    const titleInput = await screen.findByLabelText("제목");
    await user.clear(titleInput);
    await user.type(titleInput, "수정된 제목");
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(patches).toEqual([{ title: "수정된 제목" }]);
    });
  });
});
