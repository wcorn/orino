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
      const slice = rows
        .slice(offset, offset + limit)
        .map((cells, i) => ({ rowIndex: offset + i, cells }));
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
            data: { rowIndex: Number(params.i), cells: body.cells },
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
