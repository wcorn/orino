import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { Route, Routes, useSearchParams } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { NoteWorkspace } from "./NoteWorkspace";

const API_BASE = "https://api.orino.dev/api";

// 현재 선택된 노트(URL ?note=)를 노출한다. jsdom은 CSS(md:hidden 등)를 적용하지 않아 "목록 vs
// 에디터" 표시를 DOM 존재로 판별할 수 없으므로, 자동 선택 여부는 이 파라미터로 결정적으로 확인한다.
function NoteParamProbe() {
  const [sp] = useSearchParams();
  return <div data-testid="note-param">{sp.get("note") ?? ""}</div>;
}

function renderWorkspace(initialEntries = ["/notes"]) {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route
          path="/notes"
          element={
            <>
              <NoteWorkspace />
              <NoteParamProbe />
            </>
          }
        />
      </Routes>
    </Providers>,
    { initialEntries },
  );
}

interface TreeNode {
  id: number;
  title: string;
  parentId: number | null;
  sortOrder: number;
  children: TreeNode[];
}

function mockTree(notes: TreeNode[]) {
  server.use(
    http.get(`${API_BASE}/notes`, () =>
      HttpResponse.json({ code: "OK", data: { notes } }),
    ),
  );
}

function mockNoteDetail(
  id: number,
  content: unknown = { type: "doc", content: [] },
  title = "노트",
) {
  server.use(
    http.get(`${API_BASE}/notes/${id}`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          id,
          materialId: null,
          parentId: null,
          title,
          sortOrder: 0,
          content,
          updatedAt: "2026-07-04T10:00:00",
        },
      }),
    ),
  );
}

/** 좁은 화면(모바일, md 미만)으로 matchMedia를 목킹한다. */
function setNarrowViewport() {
  window.matchMedia = ((query: string) => ({
    matches: query.includes("767"),
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia;
}

describe("NoteWorkspace (독립 노트)", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
  });

  it("노트가 없으면 빈 상태와 [첫 노트 만들기]를 표시한다", async () => {
    mockTree([]);
    renderWorkspace();

    await waitFor(() => {
      expect(screen.getByText("아직 노트가 없습니다.")).toBeInTheDocument();
    });
    expect(
      screen.getByRole("button", { name: /첫 노트 만들기/ }),
    ).toBeInTheDocument();
  });

  it("[첫 노트 만들기] → POST 후 새 노트가 선택되어 에디터가 열린다", async () => {
    mockTree([]);
    server.use(
      http.post(`${API_BASE}/notes`, () =>
        HttpResponse.json(
          {
            code: "OK",
            data: {
              id: 50,
              materialId: null,
              parentId: null,
              title: "제목 없음",
              sortOrder: 0,
              content: { type: "doc", content: [] },
              updatedAt: "2026-07-04T10:00:00",
            },
          },
          { status: 201 },
        ),
      ),
    );
    mockNoteDetail(50, { type: "doc", content: [] }, "제목 없음");

    const user = userEvent.setup();
    renderWorkspace();

    await waitFor(() => {
      expect(screen.getByText("아직 노트가 없습니다.")).toBeInTheDocument();
    });
    await user.click(screen.getByRole("button", { name: /첫 노트 만들기/ }));

    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toBeInTheDocument();
    });
  });

  it("트리의 노트를 클릭하면 해당 노트 에디터가 열린다 (첫 루트는 자동 선택)", async () => {
    mockTree([
      { id: 1, title: "노트1", parentId: null, sortOrder: 0, children: [] },
      { id: 2, title: "노트2", parentId: null, sortOrder: 1, children: [] },
    ]);
    mockNoteDetail(1, { type: "doc", content: [] }, "노트1");
    mockNoteDetail(2, { type: "doc", content: [] }, "노트2");

    const user = userEvent.setup();
    renderWorkspace();

    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toHaveValue("노트1");
    });

    await user.click(screen.getByRole("button", { name: "노트2" }));

    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toHaveValue("노트2");
    });
  });

  it("노드 메뉴의 [하위 추가]로 parentId를 담아 POST하고 새 하위 노트가 선택된다", async () => {
    mockTree([
      { id: 1, title: "부모", parentId: null, sortOrder: 0, children: [] },
    ]);
    mockNoteDetail(1, { type: "doc", content: [] }, "부모");
    let postedParentId: number | null | undefined;
    server.use(
      http.post(`${API_BASE}/notes`, async ({ request }) => {
        const body = (await request.json()) as { parentId: number | null };
        postedParentId = body.parentId;
        return HttpResponse.json(
          {
            code: "OK",
            data: {
              id: 77,
              materialId: null,
              parentId: body.parentId,
              title: "제목 없음",
              sortOrder: 0,
              content: { type: "doc", content: [] },
              updatedAt: "2026-07-04T10:00:00",
            },
          },
          { status: 201 },
        );
      }),
    );
    mockNoteDetail(77, { type: "doc", content: [] }, "제목 없음");

    const user = userEvent.setup();
    renderWorkspace();

    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toHaveValue("부모");
    });

    await user.click(screen.getByRole("button", { name: "부모 메뉴" }));
    await user.click(
      await screen.findByRole("menuitem", { name: /하위 추가/ }),
    );

    await waitFor(() => {
      expect(postedParentId).toBe(1);
    });
  });

  it("노트 삭제는 확인 후 DELETE를 호출한다", async () => {
    mockTree([
      { id: 7, title: "지울 노트", parentId: null, sortOrder: 0, children: [] },
    ]);
    mockNoteDetail(7, { type: "doc", content: [] }, "지울 노트");
    let deleted = false;
    server.use(
      http.delete(`${API_BASE}/notes/7`, () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    const user = userEvent.setup();
    renderWorkspace();

    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toHaveValue("지울 노트");
    });

    await user.click(screen.getByRole("button", { name: "지울 노트 메뉴" }));
    await user.click(await screen.findByRole("menuitem", { name: /삭제/ }));
    // 확인 다이얼로그의 삭제 버튼
    await user.click(await screen.findByRole("button", { name: "삭제" }));

    await waitFor(() => expect(deleted).toBe(true));
  });

  it("[페이지] 버튼으로 하위 노트를 만들고 본문에 childPage 블록이 박힌다", async () => {
    mockTree([
      { id: 1, title: "부모", parentId: null, sortOrder: 0, children: [] },
    ]);
    mockNoteDetail(1, { type: "doc", content: [] }, "부모");
    let postedParentId: number | null | undefined;
    server.use(
      http.post(`${API_BASE}/notes`, async ({ request }) => {
        const body = (await request.json()) as { parentId: number | null };
        postedParentId = body.parentId;
        return HttpResponse.json(
          {
            code: "OK",
            data: {
              id: 99,
              materialId: null,
              parentId: body.parentId,
              title: "제목 없음",
              sortOrder: 0,
              content: { type: "doc", content: [] },
              updatedAt: "2026-07-05T10:00:00",
            },
          },
          { status: 201 },
        );
      }),
    );

    const user = userEvent.setup();
    renderWorkspace();

    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toHaveValue("부모");
    });

    await user.click(screen.getByRole("button", { name: "하위 페이지 추가" }));

    await waitFor(() => expect(postedParentId).toBe(1));
    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /하위 페이지 .* 열기/ }),
      ).toBeInTheDocument();
    });
  });

  it("본문 childPage 블록 클릭 시 자식 노트로 전환된다", async () => {
    mockTree([
      {
        id: 1,
        title: "부모",
        parentId: null,
        sortOrder: 0,
        children: [
          { id: 2, title: "자식", parentId: 1, sortOrder: 0, children: [] },
        ],
      },
    ]);
    mockNoteDetail(
      1,
      {
        type: "doc",
        content: [{ type: "childPage", attrs: { noteId: 2, title: "자식" } }],
      },
      "부모",
    );
    mockNoteDetail(2, { type: "doc", content: [] }, "자식");

    const user = userEvent.setup();
    renderWorkspace();

    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toHaveValue("부모");
    });

    await user.click(
      await screen.findByRole("button", { name: /하위 페이지 자식 열기/ }),
    );

    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toHaveValue("자식");
    });
  });

  // 모바일(좁은 화면)은 노트 선택 시 트리를 숨기는 드릴다운 레이아웃. 자동 선택하면 목록을 볼 수 없다.
  describe("모바일(좁은 화면)", () => {
    const originalMatchMedia = window.matchMedia;
    afterEach(() => {
      window.matchMedia = originalMatchMedia;
    });

    it("첫 노트를 자동 선택하지 않고 목록을 보여준다", async () => {
      setNarrowViewport();
      mockTree([
        { id: 1, title: "노트1", parentId: null, sortOrder: 0, children: [] },
        { id: 2, title: "노트2", parentId: null, sortOrder: 1, children: [] },
      ]);
      mockNoteDetail(1, { type: "doc", content: [] }, "노트1");

      renderWorkspace();

      // 목록의 노트들이 보인다.
      expect(
        await screen.findByRole("button", { name: "노트1" }),
      ).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "노트2" })).toBeInTheDocument();
      // 자동 선택되지 않는다 → ?note= 파라미터가 비어 있다(데스크탑이면 첫 노트로 채워진다).
      expect(screen.getByTestId("note-param").textContent).toBe("");
      expect(screen.queryByLabelText("노트 제목")).not.toBeInTheDocument();
    });

    it("노트를 열고 '노트 목록'으로 돌아오면 목록이 다시 보인다(자동 재선택 안 함)", async () => {
      setNarrowViewport();
      mockTree([
        { id: 1, title: "노트1", parentId: null, sortOrder: 0, children: [] },
      ]);
      mockNoteDetail(1, { type: "doc", content: [] }, "노트1");

      const user = userEvent.setup();
      renderWorkspace();

      await user.click(await screen.findByRole("button", { name: "노트1" }));
      await waitFor(() => {
        expect(screen.getByLabelText("노트 제목")).toHaveValue("노트1");
      });

      await user.click(screen.getByRole("button", { name: /노트 목록/ }));

      // 목록으로 돌아오고, effect가 즉시 다시 선택하지 않는다.
      await waitFor(() => {
        expect(screen.queryByLabelText("노트 제목")).not.toBeInTheDocument();
      });
      expect(screen.getByRole("button", { name: "노트1" })).toBeInTheDocument();
    });
  });
});
