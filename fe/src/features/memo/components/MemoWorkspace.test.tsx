import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { MemoWorkspace } from "./MemoWorkspace";

const API_BASE = "https://api.orino.dev/api";

function renderWorkspace(initialEntries = ["/memo"]) {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route path="/memo" element={<MemoWorkspace />} />
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

function mockTree(memos: TreeNode[]) {
  server.use(
    http.get(`${API_BASE}/memos`, () =>
      HttpResponse.json({ code: "OK", data: { memos } }),
    ),
  );
}

function mockMemoDetail(
  id: number,
  content: unknown = { type: "doc", content: [] },
  title = "메모",
) {
  server.use(
    http.get(`${API_BASE}/memos/${id}`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          id,
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

describe("MemoWorkspace", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
  });

  it("메모가 없으면 빈 상태와 [첫 메모 만들기]를 표시한다", async () => {
    mockTree([]);
    renderWorkspace();

    await waitFor(() => {
      expect(screen.getByText("아직 메모가 없습니다.")).toBeInTheDocument();
    });
    expect(
      screen.getByRole("button", { name: /첫 메모 만들기/ }),
    ).toBeInTheDocument();
  });

  it("[첫 메모 만들기] → POST 후 새 메모가 선택되어 에디터가 열린다", async () => {
    mockTree([]);
    server.use(
      http.post(`${API_BASE}/memos`, () =>
        HttpResponse.json(
          {
            code: "OK",
            data: {
              id: 50,
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
    mockMemoDetail(50, { type: "doc", content: [] }, "제목 없음");

    const user = userEvent.setup();
    renderWorkspace();

    await waitFor(() => {
      expect(screen.getByText("아직 메모가 없습니다.")).toBeInTheDocument();
    });
    await user.click(screen.getByRole("button", { name: /첫 메모 만들기/ }));

    await waitFor(() => {
      expect(screen.getByLabelText("메모 제목")).toBeInTheDocument();
    });
  });

  it("트리의 메모를 클릭하면 해당 메모 에디터가 열린다 (첫 루트는 자동 선택)", async () => {
    mockTree([
      { id: 1, title: "메모1", parentId: null, sortOrder: 0, children: [] },
      { id: 2, title: "메모2", parentId: null, sortOrder: 1, children: [] },
    ]);
    mockMemoDetail(1, { type: "doc", content: [] }, "메모1");
    mockMemoDetail(2, { type: "doc", content: [] }, "메모2");

    const user = userEvent.setup();
    renderWorkspace();

    await waitFor(() => {
      expect(screen.getByLabelText("메모 제목")).toHaveValue("메모1");
    });

    await user.click(screen.getByRole("button", { name: "메모2" }));

    await waitFor(() => {
      expect(screen.getByLabelText("메모 제목")).toHaveValue("메모2");
    });
  });

  it("노드 메뉴의 [하위 추가]로 parentId를 담아 POST하고 새 하위 메모가 선택된다", async () => {
    mockTree([
      { id: 1, title: "부모", parentId: null, sortOrder: 0, children: [] },
    ]);
    mockMemoDetail(1, { type: "doc", content: [] }, "부모");
    let postedParentId: number | null | undefined;
    server.use(
      http.post(`${API_BASE}/memos`, async ({ request }) => {
        const body = (await request.json()) as { parentId: number | null };
        postedParentId = body.parentId;
        return HttpResponse.json(
          {
            code: "OK",
            data: {
              id: 77,
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
    mockMemoDetail(77, { type: "doc", content: [] }, "제목 없음");

    const user = userEvent.setup();
    renderWorkspace();

    await waitFor(() => {
      expect(screen.getByLabelText("메모 제목")).toHaveValue("부모");
    });

    await user.click(screen.getByRole("button", { name: "부모 메뉴" }));
    await user.click(
      await screen.findByRole("menuitem", { name: /하위 추가/ }),
    );

    await waitFor(() => {
      expect(postedParentId).toBe(1);
    });
  });

  it("메모 삭제는 확인 후 DELETE를 호출한다", async () => {
    mockTree([
      { id: 7, title: "지울 메모", parentId: null, sortOrder: 0, children: [] },
    ]);
    mockMemoDetail(7, { type: "doc", content: [] }, "지울 메모");
    let deleted = false;
    server.use(
      http.delete(`${API_BASE}/memos/7`, () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    const user = userEvent.setup();
    renderWorkspace();

    await waitFor(() => {
      expect(screen.getByLabelText("메모 제목")).toHaveValue("지울 메모");
    });

    await user.click(screen.getByRole("button", { name: "지울 메모 메뉴" }));
    await user.click(await screen.findByRole("menuitem", { name: /삭제/ }));
    // 확인 다이얼로그의 삭제 버튼
    await user.click(await screen.findByRole("button", { name: "삭제" }));

    await waitFor(() => expect(deleted).toBe(true));
  });
});
