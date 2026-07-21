import {
  createEvent,
  fireEvent,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay, http, HttpResponse } from "msw";
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

// jsdom엔 레이아웃이 없어 요소 크기·위치가 0이다. getBoundingClientRect를 고정값으로
// 목킹해 열 너비 리사이즈의 시작 폭과 윈도우 가상화의 scrollMargin(top=0) 계산을 받쳐 준다.
// (뷰포트 높이는 윈도우가 제공한다 — jsdom 기본 innerHeight.)
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

  it("제목 행 없이 값 셀만 지연 로드해 렌더한다", async () => {
    mockDataset([
      ["네트워크", "92"],
      ["운영체제", "78"],
    ]);
    renderWithRouter(<DatasetGrid datasetId={1} />);

    expect(await screen.findByText("네트워크")).toBeInTheDocument();
    expect(screen.getByText("92")).toBeInTheDocument();
    expect(screen.getByText("운영체제")).toBeInTheDocument();
    expect(screen.getByText("78")).toBeInTheDocument();
    // 열 제목(과목·점수)은 렌더되지 않는다 — 값만 있는 표.
    expect(screen.queryByText("과목")).not.toBeInTheDocument();
    expect(screen.queryByText("점수")).not.toBeInTheDocument();
  });

  it("세로 뷰포트 없이 행 수만큼 자란다 — 높이 상한이 없고 가로만 스크롤한다", async () => {
    mockDataset([
      ["네트워크", "92"],
      ["운영체제", "78"],
      ["자료구조", "88"],
    ]);
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("네트워크");

    const scroll = document.querySelector(".overflow-x-auto") as HTMLElement;
    expect(scroll).not.toBeNull();
    // 세로 높이 상한(maxHeight)이 없어 페이지 흐름대로 자란다.
    expect(scroll.style.maxHeight).toBe("");
    // 뷰 높이 조절 핸들은 사라졌다.
    expect(screen.queryByLabelText("표 높이 조절")).toBeNull();

    // 제목 행이 없어 본문 목록이 스크롤의 첫 자식이다.
    // 높이 = 행 수 × 고정 행 높이(36).
    const body = scroll.children[0] as HTMLElement;
    expect(body.style.height).toBe("108px");
  });

  it("행·열 추가 버튼은 평소 숨겨져 있고 그 자리(아래/오른쪽)에 호버하면 각각 보인다", async () => {
    mockDataset([["네트워크", "92"]]);
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("네트워크");

    // 두 버튼 다 표 밖 gutter에 절대 위치로 떠 있어 가장자리 셀을 가리지 않고,
    // 평소 opacity-0 → 표 전체가 아니라 각 버튼 자리에 호버할 때만 opacity-100으로 나타난다.
    const addRow = screen.getByRole("button", { name: "행 추가" });
    const addCol = screen.getByLabelText("열 추가");
    for (const btn of [addRow, addCol]) {
      expect(btn.className).toContain("absolute");
      expect(btn.className).toContain("opacity-0");
      expect(btn.className).toContain("hover:opacity-100");
      // 표 전체 호버(group-hover)가 아니라 자기 영역 호버로 바뀌었다.
      expect(btn.className).not.toContain("group-hover/grid:opacity-100");
    }
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
            data: {
              edited: {
                id: 100,
                rowIndex: Number(params.i),
                cells: body.cells,
              },
              affected: [],
            },
          });
        },
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    await user.dblClick(await screen.findByText("92"));
    const input = await screen.findByLabelText("셀 1행 2열");
    // 컨트롤드 input 값을 통째로 교체 후 Enter로 커밋
    fireEvent.change(input, { target: { value: "100" } });
    fireEvent.keyDown(input, { key: "Enter" });

    await waitFor(() => {
      expect(patched).toEqual({ index: 0, cells: ["네트워크", "100"] });
    });
  });

  it("엔터·blur 없이 타이핑만 해도 잠시 뒤 자동저장된다", async () => {
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
            data: {
              edited: {
                id: 100,
                rowIndex: Number(params.i),
                cells: body.cells,
              },
              affected: [],
            },
          });
        },
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    await user.dblClick(await screen.findByText("92"));
    const input = await screen.findByLabelText("셀 1행 2열");
    fireEvent.change(input, { target: { value: "125" } });

    // 엔터/blur를 하지 않아도 디바운스 뒤 자동저장된다.
    await waitFor(
      () => expect(patched).toEqual({ index: 0, cells: ["네트워크", "125"] }),
      { timeout: 2000 },
    );
  });

  it("선택 상태에서 타이핑 후 blur하면(엔터 없이) 그 값이 곧바로 저장된다", async () => {
    // 클릭(선택)→타이핑→blur 시 편집전환이 아직 반영 전이어도, 미저장 값을 ref로
    // flush해 blur 즉시 저장한다(옛 값이 잠깐 보였다가 바뀌던 깜빡임 방지).
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
            data: {
              edited: {
                id: 100,
                rowIndex: Number(params.i),
                cells: body.cells,
              },
              affected: [],
            },
          });
        },
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    // 더블클릭이 아니라 한 번 클릭(선택)한 뒤 타이핑하고 blur.
    await user.click(await screen.findByText("92"));
    const input = screen.getByLabelText("1행 2열 셀 (입력하면 편집)");
    fireEvent.change(input, { target: { value: "125" } });
    fireEvent.blur(input);

    await waitFor(() =>
      expect(patched).toEqual({ index: 0, cells: ["네트워크", "125"] }),
    );
  });

  it("셀을 한 번 클릭한 뒤 글자를 누르면 그 글자로 편집이 시작된다", async () => {
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
            data: {
              edited: {
                id: 100,
                rowIndex: Number(params.i),
                cells: body.cells,
              },
              affected: [],
            },
          });
        },
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    // 더블클릭이 아니라 한 번 클릭으로 선택만 한 상태에서 글자를 누른다.
    await user.click(await screen.findByText("92"));
    await user.keyboard("7");

    // 누른 글자로 편집창이 열린다(기존 값 92를 덮어씀).
    const input = await screen.findByLabelText("셀 1행 2열");
    expect(input).toHaveValue("7");

    fireEvent.change(input, { target: { value: "75" } });
    fireEvent.keyDown(input, { key: "Enter" });
    await waitFor(() => {
      expect(patched).toEqual({ index: 0, cells: ["네트워크", "75"] });
    });
  });

  it("셀을 선택하고 Enter를 누르면 기존 값을 이어서 편집한다", async () => {
    mockDataset([["네트워크", "92"]]);
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    await user.click(await screen.findByText("92"));
    await user.keyboard("{Enter}");

    // Enter는 덮어쓰지 않고 기존 값(92)을 그대로 연다.
    const input = await screen.findByLabelText("셀 1행 2열");
    expect(input).toHaveValue("92");
  });

  it("셀을 선택하고 Delete를 누르면 값이 비워져 저장된다", async () => {
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
            data: {
              edited: {
                id: 100,
                rowIndex: Number(params.i),
                cells: body.cells,
              },
              affected: [],
            },
          });
        },
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    await user.click(await screen.findByText("92"));
    await user.keyboard("{Delete}");

    // 편집창을 열지 않고 곧바로 해당 셀만 비워 저장한다.
    await waitFor(() => {
      expect(patched).toEqual({ index: 0, cells: ["네트워크", ""] });
    });
  });

  it("셀에서 드래그를 시작해도 글자가 끌려나가지 않는다(네이티브 드래그 차단)", async () => {
    mockDataset([["네트워크", "92"]]);
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("92");

    // 글자 있는 셀에서 드래그 시작(dragstart)은 preventDefault돼야 한다 — 안 그러면 활성
    // 입력창의 선택 텍스트가 네이티브로 끌려 셀 범위 선택 대신 글자가 복사된다.
    const grid = screen.getByTestId("dataset-grid");
    const dragStart = createEvent.dragStart(grid);
    fireEvent(grid, dragStart);
    expect(dragStart.defaultPrevented).toBe(true);
  });

  it("셀 범위를 복사하면 TSV(탭·줄바꿈)로 클립보드에 담는다", async () => {
    mockDataset([
      ["네트워크", "92"],
      ["운영체제", "78"],
    ]);
    renderWithRouter(<DatasetGrid datasetId={1} />);
    const a = (await screen.findByText("네트워크"))
      .parentElement as HTMLElement; // (0,0)
    fireEvent.pointerDown(a, { button: 0 });
    const b = screen.getByText("78").parentElement as HTMLElement; // (1,1)
    fireEvent.pointerDown(b, { button: 0, shiftKey: true });

    const setData = vi.fn();
    fireEvent.copy(screen.getByTestId("dataset-grid"), {
      clipboardData: { setData, getData: () => "" },
    });

    // 2×2 범위를 엑셀 호환 TSV(text/plain)로 담는다.
    expect(setData).toHaveBeenCalledWith(
      "text/plain",
      "네트워크\t92\n운영체제\t78",
    );
    // 본문 붙여넣기 시 새 표로 만들 수 있게, 커스텀 표식 타입으로도 담는다.
    expect(setData).toHaveBeenCalledWith(
      "text/x-orino-dataset-cells",
      "네트워크\t92\n운영체제\t78",
    );
  });

  it("셀을 선택하고 붙여넣으면 좌상단부터 값이 채워져 저장된다", async () => {
    mockDataset([
      ["네트워크", "92"],
      ["운영체제", "78"],
    ]);
    const patched: Record<number, string[]> = {};
    server.use(
      http.patch(
        `${API_BASE}/datasets/1/rows/:i`,
        async ({ params, request }) => {
          const body = (await request.json()) as { cells: string[] };
          patched[Number(params.i)] = body.cells;
          return HttpResponse.json({
            code: "OK",
            data: {
              edited: {
                id: 100,
                rowIndex: Number(params.i),
                cells: body.cells,
              },
              affected: [],
            },
          });
        },
      ),
    );
    renderWithRouter(<DatasetGrid datasetId={1} />);
    const a = (await screen.findByText("네트워크"))
      .parentElement as HTMLElement; // (0,0) 선택
    fireEvent.pointerDown(a, { button: 0 });

    // 2×2 TSV를 (0,0)부터 붙여넣는다.
    fireEvent.paste(screen.getByTestId("dataset-grid"), {
      clipboardData: { getData: () => "가\t나\n다\t라", setData: () => {} },
    });

    await waitFor(() => {
      expect(patched[0]).toEqual(["가", "나"]);
      expect(patched[1]).toEqual(["다", "라"]);
    });
  });

  it("붙여넣기가 표 경계를 넘으면 넘치는 부분은 잘라낸다(행 자동 추가 없음)", async () => {
    mockDataset([["네트워크", "92"]]); // 1행 2열뿐
    const patched: Record<number, string[]> = {};
    let rowCalls = 0;
    server.use(
      http.patch(
        `${API_BASE}/datasets/1/rows/:i`,
        async ({ params, request }) => {
          rowCalls++;
          const body = (await request.json()) as { cells: string[] };
          patched[Number(params.i)] = body.cells;
          return HttpResponse.json({
            code: "OK",
            data: {
              edited: {
                id: 100,
                rowIndex: Number(params.i),
                cells: body.cells,
              },
              affected: [],
            },
          });
        },
      ),
    );
    renderWithRouter(<DatasetGrid datasetId={1} />);
    const a = (await screen.findByText("92")).parentElement as HTMLElement; // (0,1)
    fireEvent.pointerDown(a, { button: 0 });

    // 3열·2행짜리를 (0,1)에 붙여도, 표는 1행 2열이라 (0,1) 한 칸만 채워진다.
    fireEvent.paste(screen.getByTestId("dataset-grid"), {
      clipboardData: { getData: () => "A\tB\tC\nD\tE\tF", setData: () => {} },
    });

    await waitFor(() => expect(patched[0]).toEqual(["네트워크", "A"]));
    // 두 번째 행은 존재하지 않아 요청조차 나가지 않는다.
    expect(rowCalls).toBe(1);
    expect(patched[1]).toBeUndefined();
  });

  it("선택 상태에서 글자를 누르면 이벤트가 상위(에디터)로 전파되지 않는다", async () => {
    mockDataset([["네트워크", "92"]]);
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await user.click(await screen.findByText("92"));

    // 표를 감싸는 에디터가 키를 받아 단축키(표 삭제 등)를 실행하지 못하도록,
    // 선택 상태의 키 입력은 document까지 버블링되지 않아야 한다.
    let bubbledKey: string | null = null;
    const spy = (e: KeyboardEvent) => {
      bubbledKey = e.key;
    };
    document.addEventListener("keydown", spy);
    await user.keyboard("d");
    document.removeEventListener("keydown", spy);

    expect(bubbledKey).toBeNull();
    // 대신 'd'로 편집이 시작된다.
    expect(await screen.findByLabelText("셀 1행 2열")).toHaveValue("d");
  });

  it("셀을 한 번 클릭하면 값이 든 입력창이 떠 바로 덮어쓸 수 있다(편집 라벨은 아직 없음)", async () => {
    mockDataset([["네트워크", "92"]]);
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    await user.click(await screen.findByText("92"));

    // 선택-만-한 활성 입력창: 값이 들어 있고 곧바로 타이핑하면 덮어써진다.
    const input = screen.getByLabelText("1행 2열 셀 (입력하면 편집)");
    expect(input).toHaveValue("92");
    // 아직 편집이 아니므로 편집용 라벨(셀 N행 M열)은 없다.
    expect(screen.queryByLabelText("셀 1행 2열")).not.toBeInTheDocument();
    // 어느 칸이 선택됐는지 보이도록 얇은 안쪽 테두리로 강조한다.
    expect(input.className).toContain("ring-1");
    expect(input.className).toContain("ring-primary");
  });

  it("셀 선택 후 한글을 조합해 입력하면 같은 입력창에서 편집돼 저장된다", async () => {
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
            data: {
              edited: {
                id: 100,
                rowIndex: Number(params.i),
                cells: body.cells,
              },
              affected: [],
            },
          });
        },
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    await user.click(await screen.findByText("92"));
    const input = screen.getByLabelText("1행 2열 셀 (입력하면 편집)");
    // 조합이 끊기지 않으려면 같은 <input>이 유지돼야 한다 — 식별용 표식을 단다.
    input.setAttribute("data-identity", "same");

    // 한글 IME 조합 시뮬레이션(전체선택된 '92'를 덮어쓴다).
    fireEvent.compositionStart(input);
    fireEvent.change(input, { target: { value: "ㄱ" } });
    fireEvent.change(input, { target: { value: "구" } });
    fireEvent.compositionEnd(input, { data: "구" });

    // 같은 입력창이 유지되고(조합 안 끊김), 편집 상태로 전환됐다.
    expect(input.getAttribute("data-identity")).toBe("same");
    expect(input).toHaveAttribute("aria-label", "셀 1행 2열");
    expect(input).toHaveValue("구");

    fireEvent.keyDown(input, { key: "Enter" });
    await waitFor(() => {
      expect(patched).toEqual({ index: 0, cells: ["네트워크", "구"] });
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

    // 헤더가 없어 열 삭제는 셀 우클릭 메뉴로 한다 — c1(점수) 값 "92" 셀을 우클릭.
    fireEvent.contextMenu(screen.getByText("92"));
    await user.click(await screen.findByRole("menuitem", { name: "열 삭제" }));
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
            edited: {
              id: 100,
              rowIndex: 0,
              cells: ["7", "21"],
              formulas: { c1: "=({단가} * 3)" },
            },
            affected: [],
          },
        });
      }),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    // 단가만 고친다.
    await user.dblClick(await screen.findByText("10"));
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
            edited: {
              id: 100,
              rowIndex: 0,
              cells: ["10", "30"],
              formulas: { c1: "=({과목} * 3)" },
            },
            affected: [],
          },
        }),
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    await user.dblClick(await screen.findByText("3"));
    const input = await screen.findByLabelText("셀 1행 2열");
    fireEvent.change(input, { target: { value: "={과목} * 3" } });
    fireEvent.keyDown(input, { key: "Enter" });

    // 입력한 수식이 아니라 계산 결과가 보인다.
    expect(await screen.findByText("30")).toBeInTheDocument();
    expect(screen.queryByText("={과목} * 3")).not.toBeInTheDocument();
  });

  it("수식을 입력하면 서버 응답 전에 낙관적으로 계산해 보여주고, 응답이 오면 서버 확정값으로 맞춘다", async () => {
    mockDataset([["10", "3"]]); // 과목(c0)=10, 점수(c1)=3
    server.use(
      http.patch(`${API_BASE}/datasets/1/rows/:i`, async () => {
        await delay(200);
        // 서버 확정값(999)은 프리뷰(20)와 다르게 둬, 언제 어느 쪽이 보이는지 구분한다.
        return HttpResponse.json({
          code: "OK",
          data: {
            edited: {
              id: 100,
              rowIndex: 0,
              cells: ["10", "999"],
              formulas: { c1: "=({과목} * 2)" },
            },
            affected: [],
          },
        });
      }),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    await user.dblClick(await screen.findByText("3"));
    const input = await screen.findByLabelText("셀 1행 2열");
    fireEvent.change(input, { target: { value: "={과목} * 2" } });
    fireEvent.keyDown(input, { key: "Enter" });

    // 서버 응답(200ms) 전에 FE가 계산한 20이 곧바로 보인다(원본 수식은 안 보인다).
    expect(await screen.findByText("20")).toBeInTheDocument();
    expect(screen.queryByText("={과목} * 2")).not.toBeInTheDocument();
    // 응답이 오면 서버 확정값(999)으로 바뀐다.
    expect(await screen.findByText("999")).toBeInTheDocument();
  });

  it("수정이 다른 행의 집계로 번지면(affected) 재조회 없이 그 행도 서버 확정값으로 갱신된다", async () => {
    // 1행 합계(c1)는 SUM(과목) 집계 — 아무 행의 과목이 바뀌면 다시 계산된다.
    let rowsFetches = 0;
    server.use(
      http.get(`${API_BASE}/datasets/1`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            columns: [
              { key: "c0", label: "과목" },
              { key: "c1", label: "합계" },
            ],
            rowCount: 2,
          },
        }),
      ),
      http.get(`${API_BASE}/datasets/1/rows`, () => {
        rowsFetches++;
        return HttpResponse.json({
          code: "OK",
          data: {
            rows: [
              {
                id: 100,
                rowIndex: 0,
                cells: ["10", "30"],
                formulas: { c1: "=SUM({과목})" },
              },
              { id: 101, rowIndex: 1, cells: ["20", ""] },
            ],
            offset: 0,
            limit: 100,
          },
        });
      }),
      http.get(`${API_BASE}/datasets/1/merges`, () =>
        HttpResponse.json({ code: "OK", data: { merges: [] } }),
      ),
      // 2행 과목을 20→5로 고치면 1행 집계가 30→15로 번진다.
      http.patch(`${API_BASE}/datasets/1/rows/:i`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            edited: { id: 101, rowIndex: 1, cells: ["5", ""] },
            affected: [
              {
                id: 100,
                rowIndex: 0,
                cells: ["10", "15"],
                formulas: { c1: "=SUM({과목})" },
              },
            ],
          },
        }),
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    expect(await screen.findByText("30")).toBeInTheDocument();
    const fetchesBefore = rowsFetches;

    await user.dblClick(await screen.findByText("20"));
    const input = await screen.findByLabelText("셀 2행 1열");
    fireEvent.change(input, { target: { value: "5" } });
    fireEvent.keyDown(input, { key: "Enter" });

    // 응답의 affected 행(1행 합계)이 재조회 없이 30→15로 갱신된다.
    expect(await screen.findByText("15")).toBeInTheDocument();
    expect(screen.queryByText("30")).not.toBeInTheDocument();
    expect(screen.getByText("5")).toBeInTheDocument();
    // 전체 행 재조회(GET /rows)가 다시 일어나지 않았다.
    expect(rowsFetches).toBe(fetchesBefore);
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

    await user.dblClick(await screen.findByText("30"));

    const input = await screen.findByLabelText("셀 1행 2열");
    expect(input).toHaveValue("=({단가} * 3)");
  });

  it("수식 없는 셀은 값 그대로 편집한다", async () => {
    mockFormulaRow();
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    await user.dblClick(await screen.findByText("10"));

    expect(await screen.findByLabelText("셀 1행 1열")).toHaveValue("10");
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

    await user.dblClick(await screen.findByText("3"));
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

  it("연속 편집 시 먼저 보낸 저장의 늦은 응답이 최신 편집을 덮지 않는다", async () => {
    mockDataset([["네트워크", "92"]]);
    let call = 0;
    server.use(
      http.patch(
        `${API_BASE}/datasets/1/rows/:i`,
        async ({ params, request }) => {
          const body = (await request.json()) as { cells: string[] };
          call++;
          // 첫 요청(먼저 보낸 편집)의 응답을 더 늦게 돌려준다.
          if (call === 1) await delay(80);
          return HttpResponse.json({
            code: "OK",
            data: {
              edited: {
                id: 100,
                rowIndex: Number(params.i),
                cells: body.cells,
              },
              affected: [],
            },
          });
        },
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    // 1) 첫째 열 편집(네트워크 → A)
    await user.dblClick(await screen.findByText("네트워크"));
    let input = screen.getByLabelText("셀 1행 1열");
    fireEvent.change(input, { target: { value: "A" } });
    fireEvent.keyDown(input, { key: "Enter" });

    // 2) 곧바로 둘째 열 편집(92 → B) — 첫 저장 응답이 아직 안 온 사이에.
    await user.dblClick(await screen.findByText("92"));
    input = screen.getByLabelText("셀 1행 2열");
    fireEvent.change(input, { target: { value: "B" } });
    fireEvent.keyDown(input, { key: "Enter" });

    await waitFor(() => expect(screen.getByText("B")).toBeInTheDocument());
    // 늦은 첫 응답이 도착할 시간을 줘도, 최신 편집 B가 92로 되돌아가지 않는다.
    await delay(140);
    expect(screen.getByText("A")).toBeInTheDocument();
    expect(screen.getByText("B")).toBeInTheDocument();
    expect(screen.queryByText("92")).not.toBeInTheDocument();
  });

  it("저장 실패 시 전체 리셋(재조회) 없이 그 행만 직전 값으로 되돌린다", async () => {
    mockDataset([
      ["네트워크", "92"],
      ["운영체제", "78"],
    ]);
    let rowsFetches = 0;
    server.use(
      http.get(`${API_BASE}/datasets/1/rows`, ({ request }) => {
        rowsFetches++;
        const url = new URL(request.url);
        const offset = Number(url.searchParams.get("offset") ?? "0");
        return HttpResponse.json({
          code: "OK",
          data: {
            rows: [
              { id: 100, rowIndex: 0, cells: ["네트워크", "92"] },
              { id: 101, rowIndex: 1, cells: ["운영체제", "78"] },
            ],
            offset,
            limit: 100,
          },
        });
      }),
      http.patch(`${API_BASE}/datasets/1/rows/:i`, () =>
        HttpResponse.json({ code: "ERR", message: "안돼요" }, { status: 400 }),
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("92");
    const fetchesBefore = rowsFetches;

    await user.dblClick(screen.getByText("92"));
    const input = screen.getByLabelText("셀 1행 2열");
    fireEvent.change(input, { target: { value: "X" } });
    fireEvent.keyDown(input, { key: "Enter" });

    // 실패하면 그 셀만 92로 되돌아온다.
    await waitFor(() => expect(screen.getByText("92")).toBeInTheDocument());
    expect(screen.queryByText("X")).not.toBeInTheDocument();
    // 다른 셀은 그대로 남는다.
    expect(screen.getByText("78")).toBeInTheDocument();
    // 전체 리셋(행 재조회)이 일어나지 않았다.
    expect(rowsFetches).toBe(fetchesBefore);
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

    // 클라이언트는 이름을 짓지 않는다(서버가 붙인다) — 빈 body로 POST한다.
    await waitFor(() => expect(sentBody).toEqual({}));
    // 새 열이 붙어 행에 빈 3번째 값 셀이 생긴다(제목이 없어 셀 개수로 확인).
    await waitFor(() => {
      expect(
        document.querySelector(
          '[data-testid="dataset-grid"] div[style*="translateY(0px)"] > div:nth-child(3)',
        ),
      ).not.toBeNull();
    });
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
          data: {
            edited: { id: 100, rowIndex: 0, cells: body.cells },
            affected: [],
          },
        });
      }),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);

    await screen.findByText("네트워크");
    await user.click(screen.getByRole("button", { name: "열 추가" }));
    // 새 3번째 셀이 붙을 때까지 기다린다(제목이 없어 셀 개수로 확인).
    await waitFor(() => {
      expect(
        document.querySelector(
          '[data-testid="dataset-grid"] div[style*="translateY(0px)"] > div:nth-child(3)',
        ),
      ).not.toBeNull();
    });

    // 편집 전엔 input이 없어 라벨로 못 찾는다 — 행의 3번째 셀 div를 눌러 편집을 연다.
    const thirdCell = document.querySelector(
      '[data-testid="dataset-grid"] div[style*="translateY(0px)"] > div:nth-child(3)',
    );
    fireEvent.doubleClick(thirdCell!);
    const input = await screen.findByLabelText("셀 1행 3열");
    fireEvent.change(input, { target: { value: "재수강" } });
    fireEvent.keyDown(input, { key: "Enter" });

    await waitFor(() => {
      expect(patched).toEqual(["네트워크", "92", "재수강"]);
    });
  });

  it("우클릭 메뉴 '행 삭제'로 DELETE를 호출한다", async () => {
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
    // 인라인 삭제 버튼을 없앴으므로 행 삭제는 셀 우클릭 메뉴로 한다.
    fireEvent.contextMenu(screen.getByText("네트워크"));
    await user.click(await screen.findByRole("menuitem", { name: "행 삭제" }));

    await waitFor(() => expect(deleted).toBe(0));
  });

  it("우클릭 메뉴 '표 삭제'는 블록 삭제 콜백을 호출한다", async () => {
    mockDataset([["네트워크", "92"]]);
    const onDeleteBlock = vi.fn();
    const user = userEvent.setup();
    renderWithRouter(
      <DatasetGrid datasetId={1} onDeleteBlock={onDeleteBlock} />,
    );

    fireEvent.contextMenu(await screen.findByText("네트워크"));
    await user.click(await screen.findByRole("menuitem", { name: "표 삭제" }));

    // 표 삭제는 키보드 단축키 대신 이 메뉴로만 — 블록 제거 콜백(노드 삭제)을 부른다.
    expect(onDeleteBlock).toHaveBeenCalledTimes(1);
  });

  it("onDeleteBlock이 없으면 '표 삭제' 항목을 보이지 않는다", async () => {
    mockDataset([["네트워크", "92"]]);
    renderWithRouter(<DatasetGrid datasetId={1} />);
    fireEvent.contextMenu(await screen.findByText("네트워크"));
    await screen.findByRole("menu", { name: "셀 메뉴" });
    expect(
      screen.queryByRole("menuitem", { name: "표 삭제" }),
    ).not.toBeInTheDocument();
  });

  it("표를 못 불러오면 '다시 시도'와 '표 블록 제거'로 대응할 수 있다", async () => {
    // 이미 삭제된 dataset을 가리키는 고아 블록 등 — 표 로드 실패 상태.
    server.use(
      http.get(
        `${API_BASE}/datasets/1`,
        () => new HttpResponse(null, { status: 404 }),
      ),
    );
    const onDeleteBlock = vi.fn();
    const user = userEvent.setup();
    renderWithRouter(
      <DatasetGrid datasetId={1} onDeleteBlock={onDeleteBlock} />,
    );

    expect(
      await screen.findByText("표를 불러오지 못했어요."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "다시 시도" }),
    ).toBeInTheDocument();
    // 삭제된 표(고아 블록)는 '표 블록 제거'로 지운다 → 블록 제거 콜백 호출.
    await user.click(screen.getByRole("button", { name: "표 블록 제거" }));
    expect(onDeleteBlock).toHaveBeenCalledTimes(1);
  });

  it("표 블록이 선택되면(blockSelected) 첫 셀을 자동으로 잡아 바로 편집되게 한다", async () => {
    mockDataset([["네트워크", "92"]]);
    renderWithRouter(<DatasetGrid datasetId={1} blockSelected={true} />);

    // 데이터가 로드되면 첫 셀(1행 1열)이 활성 입력창으로 잡힌다 — 이어서 키를 누르면 바로 편집.
    expect(
      await screen.findByLabelText("1행 1열 셀 (입력하면 편집)"),
    ).toHaveValue("네트워크");
  });

  it("blockSelected여도 특정 셀을 클릭하면 그 셀이 선택된다(첫 셀로 안 튐)", async () => {
    mockDataset([["네트워크", "92"]]);
    renderWithRouter(<DatasetGrid datasetId={1} blockSelected={true} />);
    // 처음엔 블록 선택으로 첫 셀이 잡힌다.
    await screen.findByLabelText("1행 1열 셀 (입력하면 편집)");

    // 원하는 셀("92" = 1행 2열)을 정확히 클릭 → 그 셀이 활성화(첫 셀로 되돌아가지 않음).
    const cell = (await screen.findByText("92")).parentElement as HTMLElement;
    fireEvent.pointerDown(cell, { button: 0 });
    expect(
      await screen.findByLabelText("1행 2열 셀 (입력하면 편집)"),
    ).toHaveValue("92");
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
    await screen.findByText("네트워크");

    const handle = screen.getByLabelText("1열 너비 조절");
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
            rowCount: 1,
          },
        }),
      ),
      http.get(`${API_BASE}/datasets/1/rows`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            rows: [{ id: 100, rowIndex: 0, cells: ["네트워크", "92"] }],
            offset: 0,
            limit: 100,
          },
        }),
      ),
    );
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("네트워크");

    // 제목 행이 없어졌으므로 값 행의 그리드 열 정의로 확인한다.
    const row = document.querySelector(
      '[data-testid="dataset-grid"] div[style*="translateY(0px)"]',
    ) as HTMLElement;
    expect(row.style.gridTemplateColumns).toBe("300px minmax(120px, 1fr)");
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
    await screen.findByText("네트워크");

    const handle = screen.getByLabelText("1열 너비 조절");
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
    await screen.findByText("네트워크");

    const handle = screen.getByLabelText("1열 너비 조절");
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
    await screen.findByText("네트워크");

    fireEvent.doubleClick(screen.getByLabelText("1열 너비 조절"));

    await waitFor(() => expect(deleted).toHaveBeenCalled());
  });

  // ---------- 셀 배경색 ----------

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

  it("우클릭 메뉴에서 정렬을 고르면 배경색을 보존해 일괄 PUT한다", async () => {
    mockDataset([["네트워크", "92"]]);
    // c0에 이미 배경색(green)이 있는 상태로 시작한다.
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
    );
    let sentCells: unknown = null;
    server.use(
      http.put(`${API_BASE}/datasets/1/cells/style`, async ({ request }) => {
        sentCells = ((await request.json()) as { cells: unknown }).cells;
        return HttpResponse.json({ code: "OK", data: [] });
      }),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    const cell = (await screen.findByText("네트워크"))
      .parentElement as HTMLElement; // (0,0)=c0
    fireEvent.pointerDown(cell, { button: 0 });

    fireEvent.contextMenu(cell);
    const menu = await screen.findByRole("menu", { name: "셀 메뉴" });
    await user.click(within(menu).getByLabelText("정렬 가운데"));

    // 배경색(green)은 보존하고 정렬만 얹어 일괄 엔드포인트로 보낸다.
    await waitFor(() =>
      expect(sentCells).toEqual([
        {
          rowIndex: 0,
          colKey: "c0",
          bg: "green",
          align: "center",
          valign: null,
        },
      ]),
    );
  });

  it("우클릭 메뉴에서 세로 정렬을 고르면 배경색·가로 정렬을 보존해 일괄 PUT한다", async () => {
    mockDataset([["네트워크", "92"]]);
    // c0에 이미 배경색(green)·가로 정렬(right)이 있는 상태로 시작한다.
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
                styles: { c0: { bg: "green", align: "right" } },
              },
            ],
            offset: 0,
            limit: 100,
          },
        }),
      ),
    );
    let sentCells: unknown = null;
    server.use(
      http.put(`${API_BASE}/datasets/1/cells/style`, async ({ request }) => {
        sentCells = ((await request.json()) as { cells: unknown }).cells;
        return HttpResponse.json({ code: "OK", data: [] });
      }),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    const cell = (await screen.findByText("네트워크"))
      .parentElement as HTMLElement; // (0,0)=c0
    fireEvent.pointerDown(cell, { button: 0 });

    fireEvent.contextMenu(cell);
    const menu = await screen.findByRole("menu", { name: "셀 메뉴" });
    await user.click(within(menu).getByLabelText("세로 정렬 가운데"));

    // 배경색(green)·가로 정렬(right)은 보존하고 세로 정렬만 얹어 보낸다.
    await waitFor(() =>
      expect(sentCells).toEqual([
        {
          rowIndex: 0,
          colKey: "c0",
          bg: "green",
          align: "right",
          valign: "middle",
        },
      ]),
    );
  });

  it("서버가 준 valign으로 셀 세로 정렬 클래스를 그린다", async () => {
    mockDataset([["네트워크", "92"]]);
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
                styles: { c1: { valign: "bottom" } },
              },
            ],
            offset: 0,
            limit: 100,
          },
        }),
      ),
    );
    renderWithRouter(<DatasetGrid datasetId={1} />);
    // valign=bottom 셀은 세로 flex로 내려 justify-end로 정렬한다.
    expect((await screen.findByText("92")).className).toContain("justify-end");
    // valign 없는 셀은 기본(truncate) 그대로.
    expect(screen.getByText("네트워크").className).toContain("truncate");
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

  // ---------- 선택(selection) ----------

  it("셀을 한 번 클릭하면 선택되고, 우클릭하면 '셀 1개' 메뉴가 뜬다", async () => {
    mockDataset([["네트워크", "92"]]);
    renderWithRouter(<DatasetGrid datasetId={1} />);
    const cell = (await screen.findByText("92")).parentElement as HTMLElement;
    fireEvent.pointerDown(cell, { button: 0 });

    // 편집 input(셀 N행 M열)은 아직 뜨지 않는다(선택일 뿐).
    expect(screen.queryByLabelText("셀 1행 2열")).not.toBeInTheDocument();

    // 옵션은 플로팅 툴바 없이 우클릭 메뉴 하나로 통일 — 선택 범위가 '셀 1개'로 표시된다.
    fireEvent.contextMenu(cell);
    const menu = await screen.findByRole("menu", { name: "셀 메뉴" });
    expect(within(menu).getByText("셀 1개")).toBeInTheDocument();
  });

  it("shift+클릭으로 셀 범위를 선택하고, 우클릭 메뉴가 '셀 4개'를 보여준다", async () => {
    mockDataset([
      ["네트워크", "92"],
      ["운영체제", "78"],
    ]);
    renderWithRouter(<DatasetGrid datasetId={1} />);
    const a = (await screen.findByText("네트워크"))
      .parentElement as HTMLElement;
    fireEvent.pointerDown(a, { button: 0 });
    const b = screen.getByText("78").parentElement as HTMLElement;
    fireEvent.pointerDown(b, { button: 0, shiftKey: true });

    // (0,0)~(1,1) = 4칸. 선택 안을 우클릭하면 선택이 유지된다.
    fireEvent.contextMenu(a);
    const menu = await screen.findByRole("menu", { name: "셀 메뉴" });
    expect(within(menu).getByText("셀 4개")).toBeInTheDocument();
  });

  it("행 핸들로 행을 선택하고 우클릭하면 '1행'과 행 삽입 옵션이 나온다", async () => {
    mockDataset([["네트워크", "92"]]);
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("네트워크");
    fireEvent.pointerDown(screen.getByRole("button", { name: "1행 선택" }));

    fireEvent.contextMenu(screen.getByText("네트워크"));
    const menu = await screen.findByRole("menu", { name: "셀 메뉴" });
    expect(within(menu).getByText("1행")).toBeInTheDocument();
    expect(
      within(menu).getByRole("menuitem", { name: "위에 행 삽입" }),
    ).toBeInTheDocument();
    expect(
      within(menu).getByRole("menuitem", { name: "아래에 행 삽입" }),
    ).toBeInTheDocument();
  });

  it("열 핸들로 열을 선택하고 우클릭하면 '1열'과 열 삽입 옵션이 나온다", async () => {
    mockDataset([["네트워크", "92"]]);
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("네트워크");
    fireEvent.pointerDown(screen.getByRole("button", { name: "1열 선택" }));

    fireEvent.contextMenu(screen.getByText("네트워크"));
    const menu = await screen.findByRole("menu", { name: "셀 메뉴" });
    expect(within(menu).getByText("1열")).toBeInTheDocument();
    expect(
      within(menu).getByRole("menuitem", { name: "왼쪽에 열 삽입" }),
    ).toBeInTheDocument();
  });

  it("코너로 표 전체를 선택하고 우클릭하면 '표 전체'가 나온다", async () => {
    mockDataset([["네트워크", "92"]]);
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("네트워크");
    fireEvent.pointerDown(screen.getByRole("button", { name: "표 전체 선택" }));

    fireEvent.contextMenu(screen.getByText("네트워크"));
    const menu = await screen.findByRole("menu", { name: "셀 메뉴" });
    expect(within(menu).getByText("표 전체")).toBeInTheDocument();
  });

  it("방향키로 활성 셀을 상하좌우로 옮긴다", async () => {
    mockDataset([
      ["a", "b"],
      ["c", "d"],
    ]);
    renderWithRouter(<DatasetGrid datasetId={1} />);
    const cell = (await screen.findByText("a")).parentElement as HTMLElement;
    fireEvent.pointerDown(cell, { button: 0 }); // (0,0) 선택
    const input = await screen.findByLabelText("1행 1열 셀 (입력하면 편집)");

    // → 오른쪽 칸으로.
    fireEvent.keyDown(input, { key: "ArrowRight" });
    expect(
      await screen.findByLabelText("1행 2열 셀 (입력하면 편집)"),
    ).toBeInTheDocument();

    // ↓ 아래 칸으로.
    fireEvent.keyDown(screen.getByLabelText("1행 2열 셀 (입력하면 편집)"), {
      key: "ArrowDown",
    });
    expect(
      await screen.findByLabelText("2행 2열 셀 (입력하면 편집)"),
    ).toBeInTheDocument();
  });

  it("Shift+방향키로 셀 범위를 확장한다 — 우클릭 메뉴가 '셀 2개'", async () => {
    mockDataset([["a", "b"]]);
    renderWithRouter(<DatasetGrid datasetId={1} />);
    const a = (await screen.findByText("a")).parentElement as HTMLElement;
    fireEvent.pointerDown(a, { button: 0 }); // (0,0)
    const input = await screen.findByLabelText("1행 1열 셀 (입력하면 편집)");
    fireEvent.keyDown(input, { key: "ArrowRight", shiftKey: true }); // (0,0)~(0,1)

    fireEvent.contextMenu(a);
    const menu = await screen.findByRole("menu", { name: "셀 메뉴" });
    expect(within(menu).getByText("셀 2개")).toBeInTheDocument();
  });

  it("Ctrl/Cmd+A로 표 전체를 선택한다", async () => {
    mockDataset([
      ["a", "b"],
      ["c", "d"],
    ]);
    renderWithRouter(<DatasetGrid datasetId={1} />);
    const cell = (await screen.findByText("a")).parentElement as HTMLElement;
    fireEvent.pointerDown(cell, { button: 0 });
    const input = await screen.findByLabelText("1행 1열 셀 (입력하면 편집)");
    fireEvent.keyDown(input, { key: "a", metaKey: true });

    fireEvent.contextMenu(cell);
    const menu = await screen.findByRole("menu", { name: "셀 메뉴" });
    expect(within(menu).getByText("표 전체")).toBeInTheDocument();
  });

  it("편집 중엔 방향키가 셀을 옮기지 않는다(입력창 커서 이동에 맡긴다)", async () => {
    mockDataset([["a", "b"]]);
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await user.dblClick(await screen.findByText("a")); // 편집 진입
    const input = await screen.findByLabelText("셀 1행 1열");
    fireEvent.keyDown(input, { key: "ArrowRight" });

    // 여전히 같은 셀을 편집 중 — 옆 칸으로 안 넘어간다.
    expect(screen.getByLabelText("셀 1행 1열")).toBeInTheDocument();
    expect(screen.queryByLabelText("셀 1행 2열")).not.toBeInTheDocument();
  });

  it("행 핸들 shift+클릭으로 여러 행을 선택한다 — 우클릭 메뉴가 '2행'", async () => {
    mockDataset([
      ["a", "b"],
      ["c", "d"],
    ]);
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("a");
    fireEvent.pointerDown(screen.getByRole("button", { name: "1행 선택" }));
    fireEvent.pointerDown(screen.getByRole("button", { name: "2행 선택" }), {
      shiftKey: true,
    });

    fireEvent.contextMenu(screen.getByText("a"));
    const menu = await screen.findByRole("menu", { name: "셀 메뉴" });
    expect(within(menu).getByText("2행")).toBeInTheDocument();
  });

  it("열 핸들 shift+클릭으로 여러 열을 선택한다 — 우클릭 메뉴가 '2열'", async () => {
    mockDataset([["a", "b"]]);
    renderWithRouter(<DatasetGrid datasetId={1} />);
    await screen.findByText("a");
    fireEvent.pointerDown(screen.getByRole("button", { name: "1열 선택" }));
    fireEvent.pointerDown(screen.getByRole("button", { name: "2열 선택" }), {
      shiftKey: true,
    });

    fireEvent.contextMenu(screen.getByText("a"));
    const menu = await screen.findByRole("menu", { name: "셀 메뉴" });
    expect(within(menu).getByText("2열")).toBeInTheDocument();
  });

  // ---------- 채우기 핸들(fill) ----------

  it("채우기 핸들을 아래로 끌면 소스 셀을 대상 행에 채우고 응답으로 화면을 맞춘다", async () => {
    mockDataset([
      ["a", "1"],
      ["b", "2"],
      ["c", "3"],
    ]);
    let filled: unknown = null;
    server.use(
      http.post(`${API_BASE}/datasets/1/cells/fill`, async ({ request }) => {
        filled = await request.json();
        return HttpResponse.json({
          code: "OK",
          data: [
            { id: 101, rowIndex: 1, cells: ["a", "2"] },
            { id: 102, rowIndex: 2, cells: ["a", "3"] },
          ],
        });
      }),
    );
    renderWithRouter(<DatasetGrid datasetId={1} />);
    const src = (await screen.findByText("a")).parentElement as HTMLElement; // (0,0)
    fireEvent.pointerDown(src, { button: 0 }); // 선택

    const handle = await screen.findByLabelText("채우기 핸들");
    fireEvent.pointerDown(handle);
    // 3행(rowIndex 2)까지 끈다.
    fireEvent.pointerEnter(screen.getByText("c").parentElement as HTMLElement);
    fireEvent.pointerUp(window);

    await waitFor(() =>
      expect(filled).toEqual({
        cols: ["c0"],
        srcR0: 0,
        srcR1: 0,
        dstR0: 1,
        dstR1: 2,
      }),
    );
    // 응답 행들이 반영돼 c0가 세 행 모두 "a"가 된다("b"·"c"는 사라진다).
    await waitFor(() =>
      expect(screen.getAllByText("a").length).toBeGreaterThanOrEqual(3),
    );
    expect(screen.queryByText("b")).not.toBeInTheDocument();
    expect(screen.queryByText("c")).not.toBeInTheDocument();
  });

  it("채우기 핸들을 위로도 끌 수 있다", async () => {
    mockDataset([
      ["a", "1"],
      ["b", "2"],
      ["c", "3"],
    ]);
    let filled: unknown = null;
    server.use(
      http.post(`${API_BASE}/datasets/1/cells/fill`, async ({ request }) => {
        filled = await request.json();
        return HttpResponse.json({ code: "OK", data: [] });
      }),
    );
    renderWithRouter(<DatasetGrid datasetId={1} />);
    const src = (await screen.findByText("c")).parentElement as HTMLElement; // (2,0)
    fireEvent.pointerDown(src, { button: 0 });

    const handle = await screen.findByLabelText("채우기 핸들");
    fireEvent.pointerDown(handle);
    fireEvent.pointerEnter(screen.getByText("a").parentElement as HTMLElement); // 위로 1행
    fireEvent.pointerUp(window);

    await waitFor(() =>
      expect(filled).toEqual({
        cols: ["c0"],
        srcR0: 2,
        srcR1: 2,
        dstR0: 0,
        dstR1: 1,
      }),
    );
  });

  it("채우기 핸들을 소스 안에서 놓으면(대상 없음) 채우지 않는다", async () => {
    mockDataset([
      ["a", "1"],
      ["b", "2"],
    ]);
    let called = false;
    server.use(
      http.post(`${API_BASE}/datasets/1/cells/fill`, () => {
        called = true;
        return HttpResponse.json({ code: "OK", data: [] });
      }),
    );
    renderWithRouter(<DatasetGrid datasetId={1} />);
    const src = (await screen.findByText("a")).parentElement as HTMLElement;
    fireEvent.pointerDown(src, { button: 0 });
    const handle = await screen.findByLabelText("채우기 핸들");
    fireEvent.pointerDown(handle);
    fireEvent.pointerUp(window); // 대상 행으로 안 끌고 바로 놓음

    await new Promise((r) => setTimeout(r, 30));
    expect(called).toBe(false);
  });

  it("우클릭 메뉴에서 배경색을 고르면 선택한 셀에 일괄 PUT한다", async () => {
    mockDataset([["네트워크", "92"]]);
    let sentCells: unknown = null;
    server.use(
      http.put(`${API_BASE}/datasets/1/cells/style`, async ({ request }) => {
        sentCells = ((await request.json()) as { cells: unknown }).cells;
        return HttpResponse.json({
          code: "OK",
          data: [
            {
              id: 100,
              rowIndex: 0,
              cells: ["네트워크", "92"],
              formulas: {},
              styles: { c1: { bg: "green" } },
            },
          ],
        });
      }),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    const cell = (await screen.findByText("92")).parentElement as HTMLElement; // (0,1)=c1
    fireEvent.pointerDown(cell, { button: 0 });

    fireEvent.contextMenu(cell);
    const menu = await screen.findByRole("menu", { name: "셀 메뉴" });
    await user.click(within(menu).getByLabelText("배경색 green"));

    // 선택 셀 하나를 일괄 엔드포인트로 보낸다(정렬은 기존값 없음 → null).
    await waitFor(() =>
      expect(sentCells).toEqual([
        { rowIndex: 0, colKey: "c1", bg: "green", align: null, valign: null },
      ]),
    );
  });

  it("범위를 선택해 우클릭 배경색을 고르면 한 번의 요청으로 모든 셀에 적용한다", async () => {
    mockDataset([
      ["네트워크", "92"],
      ["운영체제", "78"],
    ]);
    let requestCount = 0;
    let count = 0;
    server.use(
      http.put(`${API_BASE}/datasets/1/cells/style`, async ({ request }) => {
        requestCount++;
        count = ((await request.json()) as { cells: unknown[] }).cells.length;
        return HttpResponse.json({ code: "OK", data: [] });
      }),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    const a = (await screen.findByText("네트워크"))
      .parentElement as HTMLElement; // (0,0)
    fireEvent.pointerDown(a, { button: 0 });
    const b = screen.getByText("78").parentElement as HTMLElement; // (1,1)
    fireEvent.pointerDown(b, { button: 0, shiftKey: true });

    fireEvent.contextMenu(a);
    const menu = await screen.findByRole("menu", { name: "셀 메뉴" });
    await user.click(within(menu).getByLabelText("배경색 blue"));

    // 4칸(2×2)을 한 요청으로 보낸다.
    await waitFor(() => expect(requestCount).toBe(1));
    expect(count).toBe(4);
  });

  it("Esc로 선택을 해제한다", async () => {
    mockDataset([["네트워크", "92"]]);
    renderWithRouter(<DatasetGrid datasetId={1} />);
    const cell = (await screen.findByText("92")).parentElement as HTMLElement;
    fireEvent.pointerDown(cell, { button: 0 });
    // 선택되면 활성 입력창(선택 모드)이 뜬다.
    expect(
      screen.getByLabelText("1행 2열 셀 (입력하면 편집)"),
    ).toBeInTheDocument();

    fireEvent.keyDown(window, { key: "Escape" });
    await waitFor(() =>
      expect(
        screen.queryByLabelText("1행 2열 셀 (입력하면 편집)"),
      ).not.toBeInTheDocument(),
    );
  });

  it("셀 범위를 우클릭해 '병합'하면 앵커 셀에 rowSpan·colSpan으로 PUT한다", async () => {
    mockDataset([
      ["네트워크", "92"],
      ["운영체제", "78"],
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
              merges: [{ rowIndex: 0, colKey: "c0", rowSpan: 2, colSpan: 2 }],
            },
          });
        },
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    const a = (await screen.findByText("네트워크"))
      .parentElement as HTMLElement; // (0,0)
    fireEvent.pointerDown(a, { button: 0 });
    const b = screen.getByText("78").parentElement as HTMLElement; // (1,1)
    fireEvent.pointerDown(b, { button: 0, shiftKey: true });

    fireEvent.contextMenu(a);
    const menu = await screen.findByRole("menu", { name: "셀 메뉴" });
    await user.click(within(menu).getByRole("menuitem", { name: "병합" }));

    // 2×2 사각 범위를 앵커(0,0=c0)에 한 병합으로 보낸다.
    await waitFor(() => expect(sent).toEqual({ rowSpan: 2, colSpan: 2 }));
  });

  it("셀 범위를 우클릭해 '내용 지우기'하면 값이 비워져 저장된다", async () => {
    mockDataset([
      ["네트워크", "92"],
      ["운영체제", "78"],
    ]);
    const patched: Record<number, string[]> = {};
    server.use(
      http.patch(
        `${API_BASE}/datasets/1/rows/:i`,
        async ({ params, request }) => {
          const body = (await request.json()) as { cells: string[] };
          patched[Number(params.i)] = body.cells;
          return HttpResponse.json({
            code: "OK",
            data: {
              edited: {
                id: 100,
                rowIndex: Number(params.i),
                cells: body.cells,
              },
              affected: [],
            },
          });
        },
      ),
    );
    const user = userEvent.setup();
    renderWithRouter(<DatasetGrid datasetId={1} />);
    const a = (await screen.findByText("네트워크"))
      .parentElement as HTMLElement; // (0,0)
    fireEvent.pointerDown(a, { button: 0 });
    const b = screen.getByText("78").parentElement as HTMLElement; // (1,1)
    fireEvent.pointerDown(b, { button: 0, shiftKey: true });

    fireEvent.contextMenu(a);
    const menu = await screen.findByRole("menu", { name: "셀 메뉴" });
    await user.click(
      within(menu).getByRole("menuitem", { name: "내용 지우기" }),
    );

    // 두 행의 두 열 모두 빈 값으로 저장된다.
    await waitFor(() => {
      expect(patched[0]).toEqual(["", ""]);
      expect(patched[1]).toEqual(["", ""]);
    });
  });
});
