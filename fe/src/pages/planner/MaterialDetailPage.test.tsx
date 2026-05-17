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
    http.get(`${API_BASE}/planner/materials/1/note`, () => {
      return HttpResponse.json({
        code: "OK",
        data: {
          id: 10,
          materialId: 1,
          content: { type: "doc", content: [] },
          updatedAt: "2026-05-18T00:00:00",
        },
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
