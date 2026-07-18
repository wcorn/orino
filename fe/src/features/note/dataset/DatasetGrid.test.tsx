import { fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useAuthStore } from "@/features/auth/store/authStore";
import { useToastStore } from "@/shared/lib/toast";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { DatasetGrid } from "./DatasetGrid";

const API_BASE = "https://api.orino.dev/api";

function mockDataset(rows: string[][]) {
  server.use(
    http.get(`${API_BASE}/datasets/1`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          id: 1,
          columns: [
            { key: "c0", label: "과목" },
            { key: "c1", label: "점수" },
          ],
          rowCount: rows.length,
        },
      }),
    ),
    http.get(`${API_BASE}/datasets/1/rows`, ({ request }) => {
      const url = new URL(request.url);
      const offset = Number(url.searchParams.get("offset") ?? "0");
      const limit = Number(url.searchParams.get("limit") ?? "100");
      const slice = rows.slice(offset, offset + limit).map((cells, i) => ({
        id: 100 + offset + i,
        rowIndex: offset + i,
        cells,
      }));
      return HttpResponse.json({
        code: "OK",
        data: { rows: slice, offset, limit },
      });
    }),
    http.get(`${API_BASE}/datasets/1/merges`, () =>
      HttpResponse.json({ code: "OK", data: { merges: [] } }),
    ),
  );
}

/** 그 dataset의 병합 리스트를 목킹한다(GET /merges). 병합은 dataset 단위로 통째 온다. */
function mockMerges(merges: unknown[]) {
  server.use(
    http.get(`${API_BASE}/datasets/1/merges`, () =>
      HttpResponse.json({ code: "OK", data: { merges } }),
    ),
  );
}

// jsdom엔 레이아웃이 없어 스크롤 요소 크기가 0이라 TanStack Virtual이 행을 안 그린다.
// getBoundingClientRect를 고정 크기로 목킹해 뷰포트를 만들어 준다.
const FAKE_RECT = {
  x: 0,
  y: 0,
  width: 800,
  height: 600,
  top: 0,
  right: 800,
  bottom: 600,
  left: 0,
  toJSON: () => ({}),
} as DOMRect;

describe("DatasetGrid", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
    vi.spyOn(Element.prototype, "getBoundingClientRect").mockReturnValue(
      FAKE_RECT,
    );
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("헤더와 행을 지연 로드해 렌더한다", async () => {
    mockDataset([
      ["네트워크", "92"],
      ["운영체제", "78"],
    ]);
    renderWithRouter(<DatasetGrid datasetId={1} />);

    expect(await screen.findByText("과목")).toBeInTheDocument();
    expect(screen.getByText("점수")).toBeInTheDocument();
    expect(await screen.findByText("네트워크")).toBeInTheDocument();
    expect(screen.getByText("78")).toBeInTheDocument();
  });

  it("하단 핸들을 드래그하면 표 높이가 바뀌고 localStorage에 유지된다", async () => {
    localStorage.clear();
    mockDataset([["네트워크", "92"]]);
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("네트워크");

    const scroll = document.querySelector(".overflow-auto") as HTMLElement;
    // 기본 높이 420px.
    expect(scroll.style.maxHeight).toBe("420px");

    const handle = screen.getByLabelText("표 높이 조절");
    fireEvent.pointerDown(handle, { clientY: 100 });
    fireEvent.pointerMove(handle, { clientY: 200 }); // +100px
    fireEvent.pointerUp(handle, { clientY: 200 });

    // 높이가 늘고, localStorage에 datasetId별로 저장된다.
    expect(scroll.style.maxHeight).toBe("520px");
    expect(localStorage.getItem("orino:dataset-grid-height:1")).toBe("520");
  });

  it("저장된 표 높이를 새로고침에 복원한다", async () => {
    localStorage.setItem("orino:dataset-grid-height:1", "600");
    mockDataset([["네트워크", "92"]]);
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("네트워크");

    const scroll = document.querySelector(".overflow-auto") as HTMLElement;
    expect(scroll.style.maxHeight).toBe("600px");
    localStorage.clear();
  });

  it("셀을 클릭해 편집하면 PATCH로 저장한다", async () => {
    mockDataset([["네트워크", "92"]]);
    let patched: { index: number; cells: string[] } | null = null;
    server.use(
      http.patch(
        `${API_BASE}/datasets/1/rows/:i`,
        async ({ params, request }) => {
          const body = (await request.json()) as { cells: string[] };
          patched = { index: Number(params.i), cells: body.cells };
          return HttpResponse.json({
            code: "OK",
            data: { id: 100, rowIndex: Number(params.i), cells: body.cells },
          });
        },
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    await user.click(await screen.findByText("92"));
    const input = await screen.findByLabelText("셀 1행 2열");
    // 컨트롤드 input 값을 통째로 교체 후 Enter로 커밋
    fireEvent.change(input, { target: { value: "100" } });
    fireEvent.keyDown(input, { key: "Enter" });

    await waitFor(() => {
      expect(patched).toEqual({ index: 0, cells: ["네트워크", "100"] });
    });
  });

  it("[행 추가]로 POST를 호출한다", async () => {
    mockDataset([["네트워크", "92"]]);
    let inserted = false;
    server.use(
      http.post(`${API_BASE}/datasets/1/rows`, () => {
        inserted = true;
        return HttpResponse.json({ code: "OK", data: { rowIndex: 1 } });
      }),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    await screen.findByText("네트워크");
    await user.click(screen.getByRole("button", { name: "행 추가" }));

    await waitFor(() => expect(inserted).toBe(true));
  });

  it("열 삭제는 확인 후 DELETE를 호출하고, 남은 값이 밀리지 않게 행을 다시 받는다", async () => {
    // 3열 중 가운데(c1)를 지운다. 캐시된 배열을 그대로 쓰면 "92"(삭제된 c1 값)가
    // 두 번째 칸에 남는다 — 다시 받아 와야 "재수강"이 와야 한다.
    server.use(
      http.get(`${API_BASE}/datasets/1`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            columns: [
              { key: "c0", label: "과목" },
              { key: "c1", label: "점수" },
              { key: "c2", label: "비고" },
            ],
            rowCount: 1,
          },
        }),
      ),
      http.get(`${API_BASE}/datasets/1/rows`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            rows: [
              { id: 100, rowIndex: 0, cells: ["네트워크", "92", "재수강"] },
            ],
            offset: 0,
            limit: 100,
          },
        }),
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("92");

    let deletedKey: string | null = null;
    server.use(
      http.delete(`${API_BASE}/datasets/1/columns/:key`, ({ params }) => {
        deletedKey = String(params.key);
        return HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            columns: [
              { key: "c0", label: "과목" },
              { key: "c2", label: "비고" },
            ],
            rowCount: 1,
          },
        });
      }),
      // 삭제 후 서버는 남은 열 기준으로 투영해 준다.
      http.get(`${API_BASE}/datasets/1/rows`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            rows: [{ id: 100, rowIndex: 0, cells: ["네트워크", "재수강"] }],
            offset: 0,
            limit: 100,
          },
        }),
      ),
    );

    await user.click(screen.getByRole("button", { name: "점수 열 삭제" }));
    // 확인 전엔 요청이 나가지 않는다.
    expect(deletedKey).toBeNull();
    await user.click(screen.getByRole("button", { name: "삭제" }));

    await waitFor(() => expect(deletedKey).toBe("c1"));
    await waitFor(() => {
      expect(screen.queryByText("점수")).not.toBeInTheDocument();
    });
    // 재조회가 안 되면 여기서 "92"가 남아 실패한다.
    expect(await screen.findByText("재수강")).toBeInTheDocument();
    expect(screen.queryByText("92")).not.toBeInTheDocument();
  });

  it("헤더를 드래그해 순서를 바꾸면 PATCH하고 값이 열을 따라간다", async () => {
    // 3열 과목/점수/비고 — 비고(c2)를 맨 앞으로 끈다.
    server.use(
      http.get(`${API_BASE}/datasets/1`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            columns: [
              { key: "c0", label: "과목" },
              { key: "c1", label: "점수" },
              { key: "c2", label: "비고" },
            ],
            rowCount: 1,
          },
        }),
      ),
      http.get(`${API_BASE}/datasets/1/rows`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            rows: [
              { id: 100, rowIndex: 0, cells: ["네트워크", "92", "재수강"] },
            ],
            offset: 0,
            limit: 100,
          },
        }),
      ),
    );
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("네트워크");

    let sentKeys: string[] | null = null;
    server.use(
      http.patch(
        `${API_BASE}/datasets/1/columns/order`,
        async ({ request }) => {
          const body = (await request.json()) as { keys: string[] };
          sentKeys = body.keys;
          return HttpResponse.json({
            code: "OK",
            data: {
              id: 1,
              columns: [
                { key: "c2", label: "비고" },
                { key: "c0", label: "과목" },
                { key: "c1", label: "점수" },
              ],
              rowCount: 1,
            },
          });
        },
      ),
      // 서버는 새 열 순서로 투영해 준다.
      http.get(`${API_BASE}/datasets/1/rows`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            rows: [
              { id: 100, rowIndex: 0, cells: ["재수강", "네트워크", "92"] },
            ],
            offset: 0,
            limit: 100,
          },
        }),
      ),
    );

    const headers = document.querySelectorAll(
      '[data-testid="dataset-grid"] .sticky > div',
    );
    fireEvent.dragStart(headers[2]); // 비고
    fireEvent.dragOver(headers[0]);
    fireEvent.drop(headers[0]); // 과목 자리에 놓기

    await waitFor(() => expect(sentKeys).toEqual(["c2", "c0", "c1"]));
    expect(await screen.findByText("비고")).toBeInTheDocument();
    // 재조회가 안 되면 여기서 옛 순서(네트워크가 첫 칸)가 남아 실패한다.
    await waitFor(() => {
      const row = document.querySelector(
        '[data-testid="dataset-grid"] div[style*="translateY(0px)"]',
      );
      const cells = Array.from(row!.querySelectorAll(":scope > div")).map((c) =>
        c.textContent?.trim(),
      );
      expect(cells).toEqual(["재수강", "네트워크", "92"]);
    });
  });

  it("같은 자리에 놓으면 PATCH하지 않는다", async () => {
    mockDataset([["네트워크", "92"]]);
    let called = false;
    server.use(
      http.patch(`${API_BASE}/datasets/1/columns/order`, () => {
        called = true;
        return new HttpResponse(null, { status: 400 });
      }),
    );
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("네트워크");

    const headers = document.querySelectorAll(
      '[data-testid="dataset-grid"] .sticky > div',
    );
    fireEvent.dragStart(headers[0]);
    fireEvent.drop(headers[0]);

    expect(called).toBe(false);
  });

  it("마지막 한 열만 남으면 삭제 버튼을 내보내지 않는다", async () => {
    server.use(
      http.get(`${API_BASE}/datasets/1`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            columns: [{ key: "c0", label: "과목" }],
            rowCount: 1,
          },
        }),
      ),
      http.get(`${API_BASE}/datasets/1/rows`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            rows: [{ id: 100, rowIndex: 0, cells: ["네트워크"] }],
            offset: 0,
            limit: 100,
          },
        }),
      ),
    );
    renderWithRouter(<DatasetGrid datasetId={1} />);

    expect(await screen.findByText("과목")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "과목 열 삭제" }),
    ).not.toBeInTheDocument();
  });

  it("다른 셀을 고칠 때 수식 셀엔 원본 수식을 돌려준다 — 값을 돌려주면 수식이 지워진다", async () => {
    server.use(
      http.get(`${API_BASE}/datasets/1`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            columns: [
              { key: "c0", label: "단가" },
              { key: "c1", label: "합계" },
            ],
            rowCount: 1,
          },
        }),
      ),
      // c1은 수식 셀 — 화면엔 계산값 30, 원본은 formulas에.
      http.get(`${API_BASE}/datasets/1/rows`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            rows: [
              {
                id: 100,
                rowIndex: 0,
                cells: ["10", "30"],
                formulas: { c1: "=({단가} * 3)" },
              },
            ],
            offset: 0,
            limit: 100,
          },
        }),
      ),
    );
    let sent: string[] | null = null;
    server.use(
      http.patch(`${API_BASE}/datasets/1/rows/:i`, async ({ request }) => {
        const body = (await request.json()) as { cells: string[] };
        sent = body.cells;
        return HttpResponse.json({
          code: "OK",
          data: {
            id: 100,
            rowIndex: 0,
            cells: ["7", "21"],
            formulas: { c1: "=({단가} * 3)" },
          },
        });
      }),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    // 단가만 고친다.
    await user.click(await screen.findByText("10"));
    const input = await screen.findByLabelText("셀 1행 1열");
    fireEvent.change(input, { target: { value: "7" } });
    fireEvent.keyDown(input, { key: "Enter" });

    await waitFor(() => {
      // 합계 자리에 계산값 "30"이 아니라 원본 수식이 실려야 한다.
      expect(sent).toEqual(["7", "=({단가} * 3)"]);
    });
    // 서버가 계산한 값으로 화면이 맞춰진다.
    expect(await screen.findByText("21")).toBeInTheDocument();
  });

  it("수식을 입력하면 화면이 서버가 계산한 값으로 바뀐다", async () => {
    mockDataset([["10", "3"]]);
    server.use(
      http.patch(`${API_BASE}/datasets/1/rows/:i`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            id: 100,
            rowIndex: 0,
            cells: ["10", "30"],
            formulas: { c1: "=({과목} * 3)" },
          },
        }),
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    await user.click(await screen.findByText("3"));
    const input = await screen.findByLabelText("셀 1행 2열");
    fireEvent.change(input, { target: { value: "={과목} * 3" } });
    fireEvent.keyDown(input, { key: "Enter" });

    // 입력한 수식이 아니라 계산 결과가 보인다.
    expect(await screen.findByText("30")).toBeInTheDocument();
    expect(screen.queryByText("={과목} * 3")).not.toBeInTheDocument();
  });

  /** c1이 수식 셀인 표 — 화면엔 30, 원본은 formulas에. */
  function mockFormulaRow() {
    server.use(
      http.get(`${API_BASE}/datasets/1`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            columns: [
              { key: "c0", label: "단가" },
              { key: "c1", label: "합계" },
            ],
            rowCount: 1,
          },
        }),
      ),
      http.get(`${API_BASE}/datasets/1/rows`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            rows: [
              {
                id: 100,
                rowIndex: 0,
                cells: ["10", "30"],
                formulas: { c1: "=({단가} * 3)" },
              },
            ],
            offset: 0,
            limit: 100,
          },
        }),
      ),
    );
  }

  it("수식 셀을 고르면 계산값이 아니라 수식이 보인다 — 안 그러면 자기 수식을 다시 볼 수 없다", async () => {
    mockFormulaRow();
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    await user.click(await screen.findByText("30"));

    const input = await screen.findByLabelText("셀 1행 2열");
    expect(input).toHaveValue("=({단가} * 3)");
  });

  it("수식 없는 셀은 값 그대로 편집한다", async () => {
    mockFormulaRow();
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    await user.click(await screen.findByText("10"));

    expect(await screen.findByLabelText("셀 1행 1열")).toHaveValue("10");
  });

  it("[수식 보기]를 켜면 계산값 대신 수식이 보인다", async () => {
    mockFormulaRow();
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("30");

    await user.click(screen.getByRole("button", { name: "수식 보기" }));

    expect(await screen.findByText("=({단가} * 3)")).toBeInTheDocument();
    expect(screen.queryByText("30")).not.toBeInTheDocument();
    // 수식 없는 셀은 그대로 값.
    expect(screen.getByText("10")).toBeInTheDocument();

    // 다시 끄면 값으로 돌아온다.
    await user.click(screen.getByRole("button", { name: "수식 보기" }));
    expect(await screen.findByText("30")).toBeInTheDocument();
  });

  it("서버가 알려준 수식 오류를 그대로 보여준다", async () => {
    mockDataset([["10", "3"]]);
    server.use(
      http.patch(`${API_BASE}/datasets/1/rows/:i`, () =>
        HttpResponse.json(
          {
            code: "SP-ERR-005",
            message: "수식을 이해할 수 없습니다. - 없는 열: 무엇",
          },
          { status: 400 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    await user.click(await screen.findByText("3"));
    const input = await screen.findByLabelText("셀 1행 2열");
    fireEvent.change(input, { target: { value: "={무엇}" } });
    fireEvent.keyDown(input, { key: "Enter" });

    // Toaster는 앱 레이아웃에 있어 이 렌더 트리엔 없다. 스토어에 실렸는지로 본다.
    await waitFor(() => {
      expect(useToastStore.getState().toasts.map((t) => t.message)).toContain(
        "수식을 이해할 수 없습니다. - 없는 열: 무엇",
      );
    });
  });

  it("[열 추가]로 POST를 호출하고 새 열이 빈 칸으로 붙는다", async () => {
    mockDataset([["네트워크", "92"]]);
    let sentBody: Record<string, unknown> | null = null;
    server.use(
      http.post(`${API_BASE}/datasets/1/columns`, async ({ request }) => {
        sentBody = (await request.json()) as Record<string, unknown>;
        // 이름은 서버가 붙인다.
        return HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            columns: [
              { key: "c0", label: "과목" },
              { key: "c1", label: "점수" },
              { key: "c2", label: "열 3" },
            ],
            rowCount: 1,
          },
        });
      }),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    await screen.findByText("네트워크");
    await user.click(screen.getByRole("button", { name: "열 추가" }));

    expect(await screen.findByText("열 3")).toBeInTheDocument();
    // 클라이언트는 이름을 짓지 않는다 — 열 개수 기반 규칙이 삭제 후 중복을 만들었다.
    expect(sentBody).toEqual({});
    // 행을 다시 받지 않으므로 기존 값이 깜빡임 없이 그대로 남는다.
    expect(screen.getByText("네트워크")).toBeInTheDocument();
    expect(screen.getByText("92")).toBeInTheDocument();
  });

  it("추가한 열의 셀을 편집하면 짧은 cells가 채워져 전송된다", async () => {
    // 캐시된 행은 2칸인데 열은 3개 — 편집 시 빈 값으로 패딩돼야 한다.
    mockDataset([["네트워크", "92"]]);
    let patched: string[] | null = null;
    server.use(
      http.post(`${API_BASE}/datasets/1/columns`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            columns: [
              { key: "c0", label: "과목" },
              { key: "c1", label: "점수" },
              { key: "c2", label: "열 3" },
            ],
            rowCount: 1,
          },
        }),
      ),
      http.patch(`${API_BASE}/datasets/1/rows/:i`, async ({ request }) => {
        const body = (await request.json()) as { cells: string[] };
        patched = body.cells;
        return HttpResponse.json({
          code: "OK",
          data: { id: 100, rowIndex: 0, cells: body.cells },
        });
      }),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    await screen.findByText("네트워크");
    await user.click(screen.getByRole("button", { name: "열 추가" }));
    await screen.findByText("열 3");

    // 편집 전엔 input이 없어 라벨로 못 찾는다 — 행의 3번째 셀 div를 눌러 편집을 연다.
    const thirdCell = document.querySelector(
      '[data-testid="dataset-grid"] div[style*="translateY(0px)"] > div:nth-child(3)',
    );
    fireEvent.click(thirdCell!);
    const input = await screen.findByLabelText("셀 1행 3열");
    fireEvent.change(input, { target: { value: "재수강" } });
    fireEvent.keyDown(input, { key: "Enter" });

    await waitFor(() => {
      expect(patched).toEqual(["네트워크", "92", "재수강"]);
    });
  });

  it("열 헤더를 더블클릭해 편집하면 PATCH로 저장하고 새 이름을 보여준다", async () => {
    mockDataset([["네트워크", "92"]]);
    let renamed: { key: string; label: string } | null = null;
    server.use(
      http.patch(
        `${API_BASE}/datasets/1/columns/:key`,
        async ({ params, request }) => {
          const body = (await request.json()) as { label: string };
          renamed = { key: String(params.key), label: body.label };
          return HttpResponse.json({
            code: "OK",
            data: {
              id: 1,
              columns: [
                { key: "c0", label: "과목" },
                { key: "c1", label: body.label },
              ],
              rowCount: 1,
            },
          });
        },
      ),
    );
    renderWithRouter(<DatasetGrid datasetId={1} />);

    fireEvent.doubleClick(await screen.findByText("점수"));
    const input = await screen.findByLabelText("열 이름 c1");
    fireEvent.change(input, { target: { value: "최종점수" } });
    fireEvent.keyDown(input, { key: "Enter" });

    await waitFor(() => {
      expect(renamed).toEqual({ key: "c1", label: "최종점수" });
    });
    expect(await screen.findByText("최종점수")).toBeInTheDocument();
  });

  it("열 이름을 비우면 PATCH를 보내지 않는다", async () => {
    mockDataset([["네트워크", "92"]]);
    let called = false;
    server.use(
      http.patch(`${API_BASE}/datasets/1/columns/:key`, () => {
        called = true;
        return new HttpResponse(null, { status: 400 });
      }),
    );
    renderWithRouter(<DatasetGrid datasetId={1} />);

    fireEvent.doubleClick(await screen.findByText("점수"));
    const input = await screen.findByLabelText("열 이름 c1");
    fireEvent.change(input, { target: { value: "   " } });
    fireEvent.keyDown(input, { key: "Enter" });

    // 편집이 닫히고 원래 이름이 남는다
    expect(await screen.findByText("점수")).toBeInTheDocument();
    expect(called).toBe(false);
  });

  it("행 삭제 버튼으로 DELETE를 호출한다", async () => {
    mockDataset([["네트워크", "92"]]);
    let deleted: number | null = null;
    server.use(
      http.delete(`${API_BASE}/datasets/1/rows/:i`, ({ params }) => {
        deleted = Number(params.i);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    await screen.findByText("네트워크");
    await user.click(screen.getByRole("button", { name: "1행 삭제" }));

    await waitFor(() => expect(deleted).toBe(0));
  });

  // ---------- 열 너비(resize) ----------

  it("헤더 경계를 드래그하면 너비를 PATCH하고 그 폭으로 그린다", async () => {
    mockDataset([["네트워크", "92"]]);
    let sent: unknown = null;
    server.use(
      http.patch(
        `${API_BASE}/datasets/1/columns/c0/width`,
        async ({ request }) => {
          sent = await request.json();
          return HttpResponse.json({
            code: "OK",
            data: {
              id: 1,
              columns: [
                { key: "c0", label: "과목", width: 700 },
                { key: "c1", label: "점수" },
              ],
              rowCount: 1,
            },
          });
        },
      ),
    );
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("과목");

    const handle = screen.getByLabelText("과목 열 너비 조절");
    // getBoundingClientRect가 800으로 목킹돼 있으므로 시작 폭은 800이다.
    fireEvent.pointerDown(handle, { clientX: 0, pointerId: 1 });
    fireEvent.pointerMove(handle, { clientX: -100, pointerId: 1 });
    fireEvent.pointerUp(handle, { clientX: -100, pointerId: 1 });

    await waitFor(() => expect(sent).toEqual({ width: 700 }));
  });

  it("너비를 지정한 열은 고정 폭, 나머지는 기본 폭으로 그린다", async () => {
    server.use(
      http.get(`${API_BASE}/datasets/1`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            columns: [
              { key: "c0", label: "과목", width: 300 },
              { key: "c1", label: "점수" },
            ],
            rowCount: 0,
          },
        }),
      ),
      http.get(`${API_BASE}/datasets/1/rows`, () =>
        HttpResponse.json({
          code: "OK",
          data: { rows: [], offset: 0, limit: 100 },
        }),
      ),
    );
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("과목");

    const header = screen
      .getByText("과목")
      .closest("div[style]") as HTMLElement;
    expect(header.style.gridTemplateColumns).toBe(
      "300px minmax(120px, 1fr) 44px",
    );
  });

  it("하한보다 좁게 끌어도 하한에서 멈춘다", async () => {
    mockDataset([["네트워크", "92"]]);
    let sent: unknown = null;
    server.use(
      http.patch(
        `${API_BASE}/datasets/1/columns/c0/width`,
        async ({ request }) => {
          sent = await request.json();
          return HttpResponse.json({
            code: "OK",
            data: {
              id: 1,
              columns: [
                { key: "c0", label: "과목", width: 60 },
                { key: "c1", label: "점수" },
              ],
              rowCount: 1,
            },
          });
        },
      ),
    );
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("과목");

    const handle = screen.getByLabelText("과목 열 너비 조절");
    fireEvent.pointerDown(handle, { clientX: 0, pointerId: 1 });
    fireEvent.pointerMove(handle, { clientX: -5000, pointerId: 1 });
    fireEvent.pointerUp(handle, { clientX: -5000, pointerId: 1 });

    // 서버가 400을 내기 전에 클라이언트가 먼저 하한으로 붙인다.
    await waitFor(() => expect(sent).toEqual({ width: 60 }));
  });

  it("폭이 그대로면 PATCH하지 않는다 - 핸들을 누르기만 한 경우", async () => {
    mockDataset([["네트워크", "92"]]);
    const patched = vi.fn();
    server.use(
      http.patch(`${API_BASE}/datasets/1/columns/c0/width`, () => {
        patched();
        return HttpResponse.json({ code: "OK", data: null });
      }),
    );
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("과목");

    const handle = screen.getByLabelText("과목 열 너비 조절");
    fireEvent.pointerDown(handle, { clientX: 0, pointerId: 1 });
    fireEvent.pointerUp(handle, { clientX: 0, pointerId: 1 });

    await new Promise((r) => setTimeout(r, 50));
    expect(patched).not.toHaveBeenCalled();
  });

  it("핸들을 더블클릭하면 너비를 지워 기본 폭으로 되돌린다", async () => {
    mockDataset([["네트워크", "92"]]);
    const deleted = vi.fn();
    server.use(
      http.delete(`${API_BASE}/datasets/1/columns/c0/width`, () => {
        deleted();
        return HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            columns: [
              { key: "c0", label: "과목" },
              { key: "c1", label: "점수" },
            ],
            rowCount: 1,
          },
        });
      }),
    );
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("과목");

    fireEvent.doubleClick(screen.getByLabelText("과목 열 너비 조절"));

    await waitFor(() => expect(deleted).toHaveBeenCalled());
  });

  it("너비 조절 드래그가 열 순서 변경으로 새지 않는다", async () => {
    mockDataset([["네트워크", "92"]]);
    const reordered = vi.fn();
    server.use(
      http.patch(`${API_BASE}/datasets/1/columns/order`, () => {
        reordered();
        return HttpResponse.json({ code: "OK", data: null });
      }),
      http.patch(`${API_BASE}/datasets/1/columns/c0/width`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            columns: [
              { key: "c0", label: "과목", width: 700 },
              { key: "c1", label: "점수" },
            ],
            rowCount: 1,
          },
        }),
      ),
    );
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("과목");

    const handle = screen.getByLabelText("과목 열 너비 조절");
    // 리사이즈 시작 전엔 헤더를 끌어 순서를 바꿀 수 있다.
    const header = screen.getByText("과목").closest("[draggable]");
    expect(header).toHaveAttribute("draggable", "true");

    fireEvent.pointerDown(handle, { clientX: 0, pointerId: 1 });
    // 리사이즈 중엔 draggable이 꺼져 HTML5 드래그가 시작되지 않는다.
    expect(header).toHaveAttribute("draggable", "false");

    fireEvent.pointerMove(handle, { clientX: -100, pointerId: 1 });
    fireEvent.pointerUp(handle, { clientX: -100, pointerId: 1 });

    await new Promise((r) => setTimeout(r, 50));
    expect(reordered).not.toHaveBeenCalled();
  });

  // ---------- 셀 배경색 ----------

  it("팔레트에서 색을 고르면 PUT하고 셀 배경이 칠해진다", async () => {
    mockDataset([["네트워크", "92"]]);
    let sent: unknown = null;
    server.use(
      http.put(
        `${API_BASE}/datasets/1/rows/0/cells/c0/style`,
        async ({ request }) => {
          sent = await request.json();
          return HttpResponse.json({
            code: "OK",
            data: {
              id: 100,
              rowIndex: 0,
              cells: ["네트워크", "92"],
              formulas: {},
              styles: { c0: { bg: "green" } },
            },
          });
        },
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("네트워크");

    // 셀 호버 버튼 → 팔레트 → green
    await user.click(screen.getByLabelText("1행 1열 서식"));
    await user.click(screen.getByLabelText("배경색 green"));

    await waitFor(() => expect(sent).toEqual({ bg: "green", align: null }));
    const cell = screen
      .getByText("네트워크")
      .closest("div[style]") as HTMLElement;
    await waitFor(() =>
      expect(cell.style.background).toContain("--cell-bg-green"),
    );
  });

  it("서버가 준 styles로 셀 배경을 그린다", async () => {
    server.use(
      http.get(`${API_BASE}/datasets/1`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            columns: [
              { key: "c0", label: "과목" },
              { key: "c1", label: "점수" },
            ],
            rowCount: 1,
          },
        }),
      ),
      http.get(`${API_BASE}/datasets/1/rows`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            rows: [
              {
                id: 100,
                rowIndex: 0,
                cells: ["네트워크", "92"],
                formulas: {},
                styles: { c1: { bg: "yellow" } },
              },
            ],
            offset: 0,
            limit: 100,
          },
        }),
      ),
    );
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("92");

    const colored = screen.getByText("92").closest("div[style]") as HTMLElement;
    expect(colored.style.background).toContain("--cell-bg-yellow");
    // 서식 없는 셀은 배경이 없다.
    const plain = screen
      .getByText("네트워크")
      .closest("div[style]") as HTMLElement;
    expect(plain.style.background).toBe("");
  });

  it("지우기를 누르면 bg를 비워 PUT한다", async () => {
    server.use(
      http.get(`${API_BASE}/datasets/1`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            columns: [
              { key: "c0", label: "과목" },
              { key: "c1", label: "점수" },
            ],
            rowCount: 1,
          },
        }),
      ),
      http.get(`${API_BASE}/datasets/1/rows`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            rows: [
              {
                id: 100,
                rowIndex: 0,
                cells: ["네트워크", "92"],
                formulas: {},
                styles: { c0: { bg: "green" } },
              },
            ],
            offset: 0,
            limit: 100,
          },
        }),
      ),
    );
    let sent: unknown = null;
    server.use(
      http.put(
        `${API_BASE}/datasets/1/rows/0/cells/c0/style`,
        async ({ request }) => {
          sent = await request.json();
          return HttpResponse.json({
            code: "OK",
            data: {
              id: 100,
              rowIndex: 0,
              cells: ["네트워크", "92"],
              formulas: {},
              styles: {},
            },
          });
        },
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("네트워크");

    await user.click(screen.getByLabelText("1행 1열 서식"));
    await user.click(screen.getByLabelText("배경색 지우기"));

    await waitFor(() => expect(sent).toEqual({ bg: null, align: null }));
  });

  it("열 정렬 버튼을 누르면 PATCH하고 셀 정렬이 바뀐다", async () => {
    mockDataset([["네트워크", "92"]]);
    let sent: unknown = null;
    server.use(
      http.patch(
        `${API_BASE}/datasets/1/columns/c1/align`,
        async ({ request }) => {
          sent = await request.json();
          return HttpResponse.json({
            code: "OK",
            data: {
              id: 1,
              columns: [
                { key: "c0", label: "과목" },
                { key: "c1", label: "점수", align: "center" },
              ],
              rowCount: 1,
            },
          });
        },
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("92");

    // 기본은 왼쪽 → 클릭하면 가운데.
    await user.click(screen.getByLabelText("점수 열 정렬 (현재 왼쪽)"));

    await waitFor(() => expect(sent).toEqual({ align: "center" }));
    // 열 기본 정렬이 그 열 셀에 반영된다.
    await waitFor(() =>
      expect(screen.getByText("92").className).toContain("text-center"),
    );
    // 다른 열은 그대로 기본(left).
    expect(screen.getByText("네트워크").className).toContain("text-left");
  });

  it("셀 정렬(override)이 열 기본 정렬을 덮는다", async () => {
    server.use(
      http.get(`${API_BASE}/datasets/1`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            columns: [
              { key: "c0", label: "과목" },
              { key: "c1", label: "점수", align: "right" },
            ],
            rowCount: 2,
          },
        }),
      ),
      http.get(`${API_BASE}/datasets/1/rows`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            rows: [
              {
                id: 100,
                rowIndex: 0,
                cells: ["네트워크", "92"],
                formulas: {},
                // 셀 override — 열 기본(right)을 덮는다.
                styles: { c1: { align: "left" } },
              },
              {
                id: 101,
                rowIndex: 1,
                cells: ["보안", "80"],
                formulas: {},
                styles: {},
              },
            ],
            offset: 0,
            limit: 100,
          },
        }),
      ),
    );
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("80");

    // override 있는 셀은 left, 없는 셀은 열 기본 right.
    expect(screen.getByText("92").className).toContain("text-left");
    expect(screen.getByText("80").className).toContain("text-right");
    // 정렬을 안 준 열은 기본 left.
    expect(screen.getByText("네트워크").className).toContain("text-left");
  });

  it("셀 팔레트에서 정렬을 고르면 배경색을 보존해 PUT한다", async () => {
    mockDataset([["네트워크", "92"]]);
    let sent: unknown = null;
    server.use(
      http.get(`${API_BASE}/datasets/1/rows`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            rows: [
              {
                id: 100,
                rowIndex: 0,
                cells: ["네트워크", "92"],
                formulas: {},
                styles: { c0: { bg: "green" } },
              },
            ],
            offset: 0,
            limit: 100,
          },
        }),
      ),
      http.put(
        `${API_BASE}/datasets/1/rows/0/cells/c0/style`,
        async ({ request }) => {
          sent = await request.json();
          return HttpResponse.json({
            code: "OK",
            data: {
              id: 100,
              rowIndex: 0,
              cells: ["네트워크", "92"],
              formulas: {},
              styles: { c0: { bg: "green", align: "center" } },
            },
          });
        },
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("네트워크");

    await user.click(screen.getByLabelText("1행 1열 서식"));
    await user.click(screen.getByLabelText("정렬 가운데"));

    // 배경색은 그대로 두고 정렬만 얹어 통째로 보낸다.
    await waitFor(() => expect(sent).toEqual({ bg: "green", align: "center" }));
  });

  it("가로 병합 앵커는 colSpan으로 넓게, 덮인 셀은 렌더하지 않는다", async () => {
    server.use(
      http.get(`${API_BASE}/datasets/1`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            columns: [
              { key: "c0", label: "과목" },
              { key: "c1", label: "점수" },
              { key: "c2", label: "비고" },
            ],
            rowCount: 1,
          },
        }),
      ),
      http.get(`${API_BASE}/datasets/1/rows`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            rows: [
              {
                id: 100,
                rowIndex: 0,
                cells: ["네트워크", "92", "재수강"],
                formulas: {},
                styles: {},
              },
            ],
            offset: 0,
            limit: 100,
          },
        }),
      ),
    );
    // c0가 c0..c1을 덮는다(가로 병합).
    mockMerges([{ rowIndex: 0, colKey: "c0", rowSpan: 1, colSpan: 2 }]);
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("네트워크");

    // 앵커는 2칸을 차지한다(in-grid span).
    const anchor = screen
      .getByText("네트워크")
      .closest("div[style]") as HTMLElement;
    expect(anchor.style.gridColumn).toContain("span 2");
    // 덮인 셀(c1="92")은 렌더되지 않는다. 값은 서버에 보존되지만 화면엔 앵커가 대신 그려진다.
    expect(screen.queryByText("92")).not.toBeInTheDocument();
    // 병합 밖의 셀은 그대로 보인다.
    expect(screen.getByText("재수강")).toBeInTheDocument();
  });

  it("세로 병합은 오버레이로 앵커 값을 한 번만 그리고 덮인 행 셀은 숨긴다", async () => {
    server.use(
      http.get(`${API_BASE}/datasets/1`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            columns: [
              { key: "c0", label: "과목" },
              { key: "c1", label: "점수" },
            ],
            rowCount: 2,
          },
        }),
      ),
      http.get(`${API_BASE}/datasets/1/rows`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            rows: [
              {
                id: 100,
                rowIndex: 0,
                cells: ["네트워크", "92"],
                formulas: {},
                styles: {},
              },
              {
                id: 101,
                rowIndex: 1,
                cells: ["보안", "80"],
                formulas: {},
                styles: {},
              },
            ],
            offset: 0,
            limit: 100,
          },
        }),
      ),
    );
    // c0가 0..1행을 덮는다(세로 병합).
    mockMerges([{ rowIndex: 0, colKey: "c0", rowSpan: 2, colSpan: 1 }]);
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("네트워크");

    // 앵커 값은 오버레이에 한 번만. 덮인 행의 c0("보안")은 숨는다.
    expect(screen.getAllByText("네트워크")).toHaveLength(1);
    expect(screen.queryByText("보안")).not.toBeInTheDocument();
    // 병합 밖의 셀은 그대로.
    expect(screen.getByText("92")).toBeInTheDocument();
    expect(screen.getByText("80")).toBeInTheDocument();
  });

  it("오른쪽과 병합을 누르면 colSpan 2로 PUT하고 리스트로 갱신한다", async () => {
    mockDataset([["네트워크", "92"]]);
    let sent: unknown = null;
    server.use(
      http.put(
        `${API_BASE}/datasets/1/rows/0/cells/c0/merge`,
        async ({ request }) => {
          sent = await request.json();
          return HttpResponse.json({
            code: "OK",
            data: {
              merges: [{ rowIndex: 0, colKey: "c0", rowSpan: 1, colSpan: 2 }],
            },
          });
        },
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("네트워크");

    fireEvent.contextMenu(screen.getByText("네트워크"));
    await user.click(screen.getByRole("menuitem", { name: "오른쪽과 병합" }));

    await waitFor(() => expect(sent).toEqual({ rowSpan: 1, colSpan: 2 }));
    // 응답 리스트로 갱신돼 덮인 c1("92")이 화면에서 사라진다.
    await waitFor(() =>
      expect(screen.queryByText("92")).not.toBeInTheDocument(),
    );
  });

  it("아래와 병합을 누르면 rowSpan 2로 PUT한다", async () => {
    mockDataset([
      ["네트워크", "92"],
      ["보안", "80"],
    ]);
    let sent: unknown = null;
    server.use(
      http.put(
        `${API_BASE}/datasets/1/rows/0/cells/c0/merge`,
        async ({ request }) => {
          sent = await request.json();
          return HttpResponse.json({
            code: "OK",
            data: {
              merges: [{ rowIndex: 0, colKey: "c0", rowSpan: 2, colSpan: 1 }],
            },
          });
        },
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("네트워크");

    fireEvent.contextMenu(screen.getByText("네트워크"));
    await user.click(screen.getByRole("menuitem", { name: "아래와 병합" }));

    await waitFor(() => expect(sent).toEqual({ rowSpan: 2, colSpan: 1 }));
  });

  it("병합 해제를 누르면 DELETE하고 덮였던 셀이 되살아난다", async () => {
    mockDataset([["네트워크", "92"]]);
    mockMerges([{ rowIndex: 0, colKey: "c0", rowSpan: 1, colSpan: 2 }]);
    let deleted = false;
    server.use(
      http.delete(`${API_BASE}/datasets/1/rows/0/cells/c0/merge`, () => {
        deleted = true;
        return HttpResponse.json({ code: "OK", data: { merges: [] } });
      }),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("네트워크");

    // 병합 상태라 덮인 "92"는 처음엔 안 보인다.
    expect(screen.queryByText("92")).not.toBeInTheDocument();

    fireEvent.contextMenu(screen.getByText("네트워크"));
    await user.click(screen.getByRole("menuitem", { name: "병합 해제" }));

    await waitFor(() => expect(deleted).toBe(true));
    // 해제되면 덮였던 셀이 되살아난다.
    await waitFor(() => expect(screen.getByText("92")).toBeInTheDocument());
  });

  it("우클릭 메뉴에서 '아래에 행 삽입'하면 atIndex로 POST한다", async () => {
    mockDataset([
      ["네트워크", "92"],
      ["보안", "80"],
    ]);
    let sent: { atIndex?: number } | null = null;
    server.use(
      http.post(`${API_BASE}/datasets/1/rows`, async ({ request }) => {
        sent = (await request.json()) as { atIndex?: number };
        return HttpResponse.json({ code: "OK", data: { rowIndex: 1 } });
      }),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("네트워크");

    fireEvent.contextMenu(screen.getByText("네트워크")); // 0행
    await user.click(screen.getByRole("menuitem", { name: "아래에 행 삽입" }));

    // 0행 아래 = atIndex 1.
    await waitFor(() => expect(sent?.atIndex).toBe(1));
  });

  it("우클릭 메뉴에서 '오른쪽에 열 삽입'하면 atIndex로 POST한다", async () => {
    mockDataset([["네트워크", "92"]]);
    let sent: unknown = null;
    server.use(
      http.post(`${API_BASE}/datasets/1/columns`, async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            columns: [
              { key: "c0", label: "과목" },
              { key: "c2", label: "새 열" },
              { key: "c1", label: "점수" },
            ],
            rowCount: 1,
          },
        });
      }),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("네트워크");

    fireEvent.contextMenu(screen.getByText("네트워크")); // c0
    await user.click(
      screen.getByRole("menuitem", { name: "오른쪽에 열 삽입" }),
    );

    // c0 오른쪽 = atIndex 1.
    await waitFor(() => expect(sent).toEqual({ atIndex: 1 }));
  });
});
