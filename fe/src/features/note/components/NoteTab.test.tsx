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

interface DatasetColumn {
  key: string;
  label: string;
}

/** 표 삽입/가져오기가 만드는 dataset의 생성·조회 엔드포인트를 목킹한다. */
function mockDataset(id: number, columns: DatasetColumn[], rows: string[][]) {
  server.use(
    http.post(`${API_BASE}/datasets`, () =>
      HttpResponse.json({ code: "OK", data: { id, columns, rowCount: 0 } }),
    ),
    // 파일을 읽는 쪽은 서버다(#1310) — 화면은 시트 요약만 받는다.
    http.post(`${API_BASE}/datasets/import/analyze`, () =>
      HttpResponse.json({
        code: "OK",
        data: [
          {
            name: "Sheet1",
            rowCount: rows.length + 1,
            columnCount: columns.length,
            preview: [columns.map((c) => c.label), ...rows],
          },
        ],
      }),
    ),
    http.post(`${API_BASE}/datasets/import`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          datasetId: id,
          rowCount: rows.length,
          columnCount: columns.length,
          formulasImported: 0,
          formulasAsValue: 0,
        },
      }),
    ),
    http.post(`${API_BASE}/datasets/${id}/rows/bulk`, async ({ request }) => {
      const body = (await request.json()) as { rows: string[][] };
      return HttpResponse.json({
        code: "OK",
        data: { id, columns, rowCount: body.rows.length },
      });
    }),
    http.get(`${API_BASE}/datasets/${id}`, () =>
      HttpResponse.json({
        code: "OK",
        data: { id, columns, rowCount: rows.length },
      }),
    ),
    http.get(`${API_BASE}/datasets/${id}/rows`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          rows: rows.map((cells, rowIndex) => ({
            id: 100 + rowIndex,
            rowIndex,
            cells,
          })),
          offset: 0,
          limit: 100,
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
      http.post(`${API_BASE}/notes`, () =>
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
      http.post(`${API_BASE}/notes`, async ({ request }) => {
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
      }),
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
      http.delete(`${API_BASE}/notes/1`, () => {
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

  it("툴바 [표 삽입] 클릭 시 빈 dataset을 만들어 데이터 그리드가 삽입된다", async () => {
    mockTree([
      { id: 1, title: "노트", parentId: null, sortOrder: 0, children: [] },
    ]);
    mockNoteDetail(1, { type: "doc", content: [] }, "노트");
    mockDataset(
      7,
      [
        { key: "c0", label: "열 1" },
        { key: "c1", label: "열 2" },
        { key: "c2", label: "열 3" },
      ],
      [
        ["", "", ""],
        ["", "", ""],
        ["", "", ""],
      ],
    );

    const user = userEvent.setup();
    renderTab();

    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toHaveValue("노트");
    });

    await user.click(screen.getByRole("button", { name: "표 삽입" }));

    // dataset 생성 → datasetTable 노드 → NodeView → DatasetGrid 렌더
    // 제목 행이 없는 값-중심 표라 열 라벨 대신 그리드 컨트롤(행 추가)로 확인한다.
    expect(await screen.findByTestId("dataset-grid")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "행 추가" })).toBeInTheDocument();
  });

  it("툴바 [이미지 추가]로 파일 선택 시 presign→PUT 후 본문에 이미지가 삽입된다", async () => {
    mockTree([
      { id: 1, title: "노트", parentId: null, sortOrder: 0, children: [] },
    ]);
    mockNoteDetail(1, { type: "doc", content: [] }, "노트");

    let presignCalled = false;
    let putCalled = false;
    const publicUrl = "https://img.orino.dev/note-images/1/abc.png";
    server.use(
      http.post(`${API_BASE}/planner/images/upload-url`, () => {
        presignCalled = true;
        return HttpResponse.json({
          code: "OK",
          data: {
            uploadUrl:
              "https://img.orino.dev/note-images/1/abc.png?X-Amz-Signature=x",
            publicUrl,
          },
        });
      }),
      http.put("https://img.orino.dev/note-images/:bucket/*", () => {
        putCalled = true;
        return new HttpResponse(null, { status: 200 });
      }),
    );

    const user = userEvent.setup();
    const { container } = renderTab();

    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toHaveValue("노트");
    });
    expect(container.querySelector("img")).toBeNull();

    const file = new File(["fake"], "shot.png", { type: "image/png" });
    const input = container.querySelector(
      'input[type="file"]',
    ) as HTMLInputElement;
    await user.upload(input, file);

    // 이미지가 즉시 삽입된다 (즉시 미리보기 → 업로드 후 공개 URL로 교체)
    await waitFor(() => {
      expect(container.querySelector("img")).not.toBeNull();
    });
    await waitFor(() => expect(presignCalled).toBe(true));
    await waitFor(() => expect(putCalled).toBe(true));
    // 업로드 완료 후 최종 src는 실제 공개 URL
    await waitFor(() => {
      expect(container.querySelector("img")?.getAttribute("src")).toBe(
        publicUrl,
      );
    });
  });

  it("[가져오기]로 엑셀을 업로드하면 dataset을 만들어 데이터 그리드가 삽입된다", async () => {
    mockTree([
      { id: 1, title: "노트", parentId: null, sortOrder: 0, children: [] },
    ]);
    mockNoteDetail(1, { type: "doc", content: [] }, "노트");
    mockDataset(
      7,
      [
        { key: "c0", label: "항목" },
        { key: "c1", label: "값" },
      ],
      [["A", "1"]],
    );

    const user = userEvent.setup();
    renderTab();

    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toHaveValue("노트");
    });

    await user.click(screen.getByRole("button", { name: "가져오기" }));

    // 파일 내용은 서버가 읽으므로 여기선 자리만 채운다.
    const file = new File([new Uint8Array([1, 2, 3])], "data.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    await user.upload(screen.getByLabelText("가져올 파일"), file);

    await screen.findByText("총 1행 × 2열");
    await user.click(screen.getByRole("button", { name: "표로 가져오기" }));

    // 본문에 datasetTable → DatasetGrid가 렌더되고 가져온 값 셀이 보인다
    // (제목 행이 없어 헤더 라벨 대신 데이터 값 "A"로 확인 — 셀은 지연 로드라 findByText로 기다린다)
    expect(await screen.findByTestId("dataset-grid")).toBeInTheDocument();
    expect(await screen.findByText("A")).toBeInTheDocument();
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

  it("datasetTable 노드가 있는 노트를 열면 데이터 그리드가 렌더된다", async () => {
    mockTree([
      { id: 1, title: "표노트", parentId: null, sortOrder: 0, children: [] },
    ]);
    mockNoteDetail(
      1,
      {
        type: "doc",
        content: [{ type: "datasetTable", attrs: { datasetId: 7 } }],
      },
      "표노트",
    );
    server.use(
      http.get(`${API_BASE}/datasets/7`, () =>
        HttpResponse.json({
          code: "OK",
          data: { id: 7, columns: [{ key: "c0", label: "과목" }], rowCount: 0 },
        }),
      ),
      http.get(`${API_BASE}/datasets/7/rows`, () =>
        HttpResponse.json({
          code: "OK",
          data: { rows: [], offset: 0, limit: 100 },
        }),
      ),
    );

    renderTab();

    await waitFor(() => {
      expect(screen.getByLabelText("노트 제목")).toHaveValue("표노트");
    });
    // 노드 → NodeView → DatasetGrid 렌더 (제목 행 없는 값-중심 표 —
    // rowCount 0이라 값도 없어 그리드 컨트롤(행 추가)로 확인한다)
    expect(await screen.findByTestId("dataset-grid")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "행 추가" })).toBeInTheDocument();
  });
});
