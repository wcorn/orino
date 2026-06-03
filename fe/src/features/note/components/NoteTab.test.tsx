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

    await user.click(screen.getByRole("button", { name: "2장" }));

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

  it("childPage 블록은 attrs 캐시가 아니라 트리의 최신 제목을 표시한다", async () => {
    // 부모 본문의 childPage는 삽입 시점 캐시("제목 없음")를 갖지만,
    // 트리에서 자식 제목이 "test"로 바뀐 상태 → 블록도 "test"로 보여야 한다.
    mockTree([
      {
        id: 1,
        title: "부모",
        parentId: null,
        sortOrder: 0,
        children: [
          { id: 2, title: "test", parentId: 1, sortOrder: 0, children: [] },
        ],
      },
    ]);
    mockNoteDetail(
      1,
      {
        type: "doc",
        content: [
          { type: "childPage", attrs: { noteId: 2, title: "제목 없음" } },
        ],
      },
      "부모",
    );

    renderTab();

    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toHaveValue("부모");
    });

    expect(
      await screen.findByRole("button", { name: "하위 페이지 test 열기" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /하위 페이지 제목 없음 열기/ }),
    ).not.toBeInTheDocument();
  });

  it("트리 [⋯] → 삭제 → 확인 시 DELETE 호출 (루트 노트)", async () => {
    mockTree([
      {
        id: 1,
        title: "삭제할 루트",
        parentId: null,
        sortOrder: 0,
        children: [],
      },
    ]);
    mockNoteDetail(1, { type: "doc", content: [] }, "삭제할 루트");
    let deletedId: number | null = null;
    server.use(
      http.delete(`${API_BASE}/planner/notes/1`, () => {
        deletedId = 1;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    const user = userEvent.setup();
    renderTab();

    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toHaveValue("삭제할 루트");
    });

    await user.click(screen.getByRole("button", { name: /삭제할 루트 메뉴/ }));
    await user.click(await screen.findByRole("menuitem", { name: /삭제/ }));
    await user.click(await screen.findByRole("button", { name: "삭제" }));

    await waitFor(() => expect(deletedId).toBe(1));
  });

  it("툴바 [표 삽입] 클릭 시 표가 본문에 삽입되고 표 편집 컨트롤이 나타난다", async () => {
    mockTree([
      { id: 1, title: "노트", parentId: null, sortOrder: 0, children: [] },
    ]);
    mockNoteDetail(1, { type: "doc", content: [] }, "노트");

    const user = userEvent.setup();
    const { container } = renderTab();

    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toHaveValue("노트");
    });

    // 표 삽입 전에는 표가 없다
    expect(container.querySelector("table")).toBeNull();

    await user.click(screen.getByRole("button", { name: "표 삽입" }));

    // 표가 렌더되고 (헤더행 없이 3x3 = td 9개), 제목 행은 기본값이 아니다
    await waitFor(() => {
      expect(container.querySelector("table")).not.toBeNull();
    });
    expect(container.querySelectorAll("table td")).toHaveLength(9);
    expect(container.querySelectorAll("table th")).toHaveLength(0);

    // 커서가 표 안에 있으므로 표 편집 컨트롤이 노출된다
    expect(screen.getByRole("button", { name: "열 추가" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "행 추가" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "표 삭제" })).toBeInTheDocument();
  });

  it("표 삽입 후 [헤더] 버튼으로 제목 행을 선택적으로 켤 수 있다", async () => {
    mockTree([
      { id: 1, title: "노트", parentId: null, sortOrder: 0, children: [] },
    ]);
    mockNoteDetail(1, { type: "doc", content: [] }, "노트");

    const user = userEvent.setup();
    const { container } = renderTab();

    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toHaveValue("노트");
    });

    await user.click(screen.getByRole("button", { name: "표 삽입" }));
    await waitFor(() => {
      expect(container.querySelector("table")).not.toBeNull();
    });
    // 기본은 헤더 없음
    expect(container.querySelectorAll("table th")).toHaveLength(0);

    // 헤더 전환 → 첫 행이 헤더(th)가 된다
    await user.click(screen.getByRole("button", { name: "헤더 행 전환" }));
    await waitFor(() => {
      expect(container.querySelectorAll("table th")).toHaveLength(3);
    });
  });

  it("표 삽입 후 [행 추가]로 행이 늘고 [표 삭제]로 표가 제거된다", async () => {
    mockTree([
      { id: 1, title: "노트", parentId: null, sortOrder: 0, children: [] },
    ]);
    mockNoteDetail(1, { type: "doc", content: [] }, "노트");

    const user = userEvent.setup();
    const { container } = renderTab();

    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toHaveValue("노트");
    });

    await user.click(screen.getByRole("button", { name: "표 삽입" }));
    await waitFor(() => {
      expect(container.querySelector("table")).not.toBeNull();
    });
    const rowsBefore = container.querySelectorAll("table tr").length;

    await user.click(screen.getByRole("button", { name: "행 추가" }));
    await waitFor(() => {
      expect(container.querySelectorAll("table tr").length).toBe(
        rowsBefore + 1,
      );
    });

    await user.click(screen.getByRole("button", { name: "표 삭제" }));
    await waitFor(() => {
      expect(container.querySelector("table")).toBeNull();
    });
  });

  it("자손이 있는 노트 삭제 시 확인 문구에 하위 개수를 표시한다", async () => {
    mockTree([
      {
        id: 1,
        title: "부모",
        parentId: null,
        sortOrder: 0,
        children: [
          {
            id: 2,
            title: "자식",
            parentId: 1,
            sortOrder: 0,
            children: [
              { id: 3, title: "손자", parentId: 2, sortOrder: 0, children: [] },
            ],
          },
        ],
      },
    ]);
    mockNoteDetail(1, { type: "doc", content: [] }, "부모");

    const user = userEvent.setup();
    renderTab();

    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toHaveValue("부모");
    });

    await user.click(screen.getByRole("button", { name: /부모 메뉴/ }));
    await user.click(await screen.findByRole("menuitem", { name: /삭제/ }));

    expect(
      await screen.findByText(/하위 노트 2개도 함께 삭제됩니다/),
    ).toBeInTheDocument();
  });
});
