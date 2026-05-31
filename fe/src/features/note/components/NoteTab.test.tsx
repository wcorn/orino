import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { NoteTab } from "./NoteTab";

const API_BASE = "https://api.orino.dev/api";

function renderTab(initialEntries = ["/m?tab=note"]) {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route path="/m" element={<NoteTab materialId={1} />} />
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
    http.get(`${API_BASE}/planner/materials/1/notes`, () =>
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
    http.get(`${API_BASE}/planner/notes/${id}`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          id,
          materialId: 1,
          parentId: null,
          title,
          sortOrder: 0,
          content,
          updatedAt: "2026-05-31T10:00:00",
        },
      }),
    ),
  );
}

describe("NoteTab", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
  });

  it("노트가 없으면 빈 상태와 [첫 노트 만들기]를 표시한다", async () => {
    mockTree([]);
    renderTab();

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
      http.post(`${API_BASE}/planner/materials/1/notes`, () =>
        HttpResponse.json(
          {
            code: "OK",
            data: {
              id: 50,
              materialId: 1,
              parentId: null,
              title: "제목 없음",
              sortOrder: 0,
              content: { type: "doc", content: [] },
              updatedAt: "2026-05-31T10:00:00",
            },
          },
          { status: 201 },
        ),
      ),
    );
    mockNoteDetail(50, { type: "doc", content: [] }, "제목 없음");

    const user = userEvent.setup();
    renderTab();

    await waitFor(() => {
      expect(screen.getByText("아직 노트가 없습니다.")).toBeInTheDocument();
    });
    await user.click(screen.getByRole("button", { name: /첫 노트 만들기/ }));

    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toBeInTheDocument();
    });
  });

  it("트리의 노트를 클릭하면 해당 노트 에디터가 열린다", async () => {
    mockTree([
      { id: 1, title: "1장", parentId: null, sortOrder: 0, children: [] },
      { id: 2, title: "2장", parentId: null, sortOrder: 1, children: [] },
    ]);
    mockNoteDetail(1, { type: "doc", content: [] }, "1장");
    mockNoteDetail(2, { type: "doc", content: [] }, "2장");

    const user = userEvent.setup();
    renderTab();

    // 첫 루트가 자동 선택됨
    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toHaveValue("1장");
    });

    await user.click(screen.getByRole("button", { name: /2장/ }));

    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toHaveValue("2장");
    });
  });

  it("[+ 페이지] 클릭 시 POST로 하위 노트 생성 후 본문에 childPage 블록이 박힌다", async () => {
    mockTree([
      { id: 1, title: "부모", parentId: null, sortOrder: 0, children: [] },
    ]);
    mockNoteDetail(1, { type: "doc", content: [] }, "부모");
    let postedParentId: number | null = null;
    server.use(
      http.post(
        `${API_BASE}/planner/materials/1/notes`,
        async ({ request }) => {
          const body = (await request.json()) as { parentId: number | null };
          postedParentId = body.parentId;
          return HttpResponse.json(
            {
              code: "OK",
              data: {
                id: 99,
                materialId: 1,
                parentId: body.parentId,
                title: "제목 없음",
                sortOrder: 0,
                content: { type: "doc", content: [] },
                updatedAt: "2026-05-31T10:00:00",
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
      expect(screen.getByLabelText("노트 제목")).toHaveValue("부모");
    });

    await user.click(screen.getByRole("button", { name: "하위 페이지 추가" }));

    await waitFor(() => {
      expect(postedParentId).toBe(1);
    });
    // 본문에 childPage 블록 렌더
    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /하위 페이지 .* 열기/ }),
      ).toBeInTheDocument();
    });
  });

  it("본문 childPage 블록 클릭 시 그 자식 노트로 전환된다", async () => {
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
    renderTab();

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
});
