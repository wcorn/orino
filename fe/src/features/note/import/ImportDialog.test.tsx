import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { JSONContent } from "@tiptap/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useAuthStore } from "@/features/auth/store/authStore";
import { useToastStore } from "@/shared/lib/toast";
import { server } from "@/test/mocks/server";

import { ImportDialog } from "./ImportDialog";

const API_BASE = "https://api.orino.dev/api";

const XLSX_TYPE =
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

/**
 * 파일을 읽는 쪽은 서버다(#1310) — 서식·수식·병합은 브라우저에서 읽을 수 없다.
 * 그래서 여기서 만드는 파일은 <b>내용이 없어도 된다</b>. 화면이 볼 것은 서버가 준 시트 요약뿐이다.
 */
function dummyFile(name = "test.xlsx", type = XLSX_TYPE): File {
  return new File([new Uint8Array([1, 2, 3])], name, { type });
}

interface SheetSummary {
  name: string;
  rowCount: number;
  columnCount: number;
  preview: string[][];
}

/** 마지막 가져오기 요청에 실린 질의 문자열. 멀티파트 본문은 jsdom에서 읽을 수 없다. */
let importQuery: URLSearchParams | null;
let formulasAsValue: number;

function mockServer(sheets: SheetSummary[]) {
  server.use(
    http.post(`${API_BASE}/datasets/import/analyze`, () =>
      HttpResponse.json({ code: "OK", data: sheets }),
    ),
    http.post(`${API_BASE}/datasets/import`, ({ request }) => {
      importQuery = new URL(request.url).searchParams;
      return HttpResponse.json({
        code: "OK",
        data: {
          datasetId: 99,
          rowCount: 1,
          columnCount: 2,
          formulasImported: 0,
          formulasAsValue,
        },
      });
    }),
  );
}

beforeEach(() => {
  useAuthStore.setState({ accessToken: "mock-token" });
  useToastStore.setState({ toasts: [] });
  importQuery = null;
  formulasAsValue = 0;
});

function renderDialog(onInsert = vi.fn()) {
  render(<ImportDialog open onOpenChange={() => {}} onInsert={onInsert} />);
  return onInsert;
}

async function upload(file: File) {
  await userEvent.upload(screen.getByLabelText("가져올 파일"), file);
}

describe("ImportDialog", () => {
  it("업로드하면 서버가 읽은 시트 미리보기를 보여주고, 가져오면 표 블록을 넣는다", async () => {
    mockServer([
      {
        name: "Sheet1",
        rowCount: 2,
        columnCount: 2,
        preview: [
          ["이름", "점수"],
          ["김철수", "90"],
        ],
      },
    ]);
    const onInsert = renderDialog();
    await upload(dummyFile());

    // 첫 행이 머리글(기본값)이라 본문은 한 줄이다.
    expect(await screen.findByText("총 1행 × 2열")).toBeInTheDocument();
    expect(screen.getByText("점수")).toBeInTheDocument();

    await userEvent.click(
      screen.getByRole("button", { name: "표로 가져오기" }),
    );

    await waitFor(() => expect(onInsert).toHaveBeenCalledTimes(1));
    const node = onInsert.mock.calls[0][0] as JSONContent;
    expect(node.type).toBe("datasetTable");
    expect(node.attrs?.datasetId).toBe(99);
    expect(importQuery?.get("sheet")).toBe("Sheet1");
    expect(importQuery?.get("firstRowAsHeader")).toBe("true");
  });

  it("'첫 행을 머리글로'를 끄면 그대로 서버에 전한다", async () => {
    mockServer([
      {
        name: "S",
        rowCount: 2,
        columnCount: 2,
        preview: [
          ["a", "b"],
          ["c", "d"],
        ],
      },
    ]);
    const onInsert = renderDialog();
    await upload(dummyFile());
    await screen.findByText(/총 \d행 × 2열/);

    await userEvent.click(screen.getByRole("switch"));
    // 머리글을 안 쓰면 본문이 한 줄 늘어난다.
    expect(await screen.findByText("총 2행 × 2열")).toBeInTheDocument();

    await userEvent.click(
      screen.getByRole("button", { name: "표로 가져오기" }),
    );

    await waitFor(() => expect(onInsert).toHaveBeenCalledTimes(1));
    expect(importQuery?.get("firstRowAsHeader")).toBe("false");
  });

  it("옮기지 못한 수식이 있으면 몇 개인지 알린다 — 조용히 값으로 바꾸지 않는다", async () => {
    formulasAsValue = 3;
    mockServer([
      { name: "S", rowCount: 2, columnCount: 1, preview: [["a"], ["b"]] },
    ]);
    const onInsert = renderDialog();
    await upload(dummyFile());
    await screen.findByText(/총 \d행 × 1열/);

    await userEvent.click(
      screen.getByRole("button", { name: "표로 가져오기" }),
    );

    await waitFor(() => expect(onInsert).toHaveBeenCalledTimes(1));
    expect(useToastStore.getState().toasts.map((t) => t.message)).toContain(
      "수식 3개는 옮길 수 없어 값으로 들어왔어요.",
    );
  });

  it("전부 옮겨졌으면 굳이 말하지 않는다", async () => {
    mockServer([
      { name: "S", rowCount: 2, columnCount: 1, preview: [["a"], ["b"]] },
    ]);
    const onInsert = renderDialog();
    await upload(dummyFile());
    await screen.findByText(/총 \d행 × 1열/);

    await userEvent.click(
      screen.getByRole("button", { name: "표로 가져오기" }),
    );

    await waitFor(() => expect(onInsert).toHaveBeenCalledTimes(1));
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  it("여러 시트면 시트를 골라 그 시트를 가져온다", async () => {
    mockServer([
      { name: "첫째", rowCount: 1, columnCount: 1, preview: [["x"]] },
      {
        name: "둘째",
        rowCount: 2,
        columnCount: 2,
        preview: [
          ["y1", "y2"],
          ["z1", "z2"],
        ],
      },
    ]);
    const onInsert = renderDialog();
    await upload(dummyFile());

    expect(await screen.findByText("시트")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("combobox"));
    await userEvent.click(await screen.findByRole("option", { name: "둘째" }));

    await userEvent.click(
      screen.getByRole("button", { name: "표로 가져오기" }),
    );
    await waitFor(() => expect(onInsert).toHaveBeenCalledTimes(1));
    expect(importQuery?.get("sheet")).toBe("둘째");
  });

  it("확장자가 안 맞으면 서버까지 가지 않고 막는다", async () => {
    mockServer([]);
    renderDialog();
    const txt = new File(["a,b"], "data.txt", { type: "text/plain" });
    await userEvent.upload(screen.getByLabelText("가져올 파일"), txt, {
      applyAccept: false,
    });

    expect(
      await screen.findByText(".xlsx 파일만 가져올 수 있어요."),
    ).toBeInTheDocument();
  });

  it("CSV 소스로 전환해 .csv를 가져온다 — 같은 길로 간다", async () => {
    mockServer([
      {
        name: "data",
        rowCount: 3,
        columnCount: 2,
        preview: [
          ["항목", "값"],
          ["A", "1"],
        ],
      },
    ]);
    const onInsert = renderDialog();
    await userEvent.click(screen.getByRole("button", { name: "CSV (.csv)" }));

    await upload(new File(["항목,값"], "data.csv", { type: "text/csv" }));

    expect(await screen.findByText("총 2행 × 2열")).toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("button", { name: "표로 가져오기" }),
    );

    await waitFor(() => expect(onInsert).toHaveBeenCalledTimes(1));
    expect((onInsert.mock.calls[0][0] as JSONContent).type).toBe(
      "datasetTable",
    );
  });

  it("빈 시트는 '데이터가 없어요' 경고 + 가져오기 비활성", async () => {
    mockServer([{ name: "Empty", rowCount: 0, columnCount: 0, preview: [] }]);
    renderDialog();
    await upload(dummyFile());

    expect(
      await screen.findByText("이 시트에는 데이터가 없어요."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "표로 가져오기" }),
    ).toBeDisabled();
  });

  it("서버가 파일을 못 읽으면 그 사실을 말한다", async () => {
    server.use(
      http.post(
        `${API_BASE}/datasets/import/analyze`,
        () => new HttpResponse(null, { status: 400 }),
      ),
    );
    renderDialog();
    await upload(dummyFile());

    expect(
      await screen.findByText(/파일을 읽을 수 없어요/),
    ).toBeInTheDocument();
  });
});
