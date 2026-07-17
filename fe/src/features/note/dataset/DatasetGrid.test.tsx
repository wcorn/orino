import { fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useAuthStore } from "@/features/auth/store/authStore";
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
});
