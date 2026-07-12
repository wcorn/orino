import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { JSONContent } from "@tiptap/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";
import * as XLSX from "xlsx";

import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";

import { ImportDialog } from "./ImportDialog";

const API_BASE = "https://api.orino.dev/api";

function makeXlsx(
  sheets: Record<string, unknown[][]>,
  name = "test.xlsx",
): File {
  const wb = XLSX.utils.book_new();
  for (const [sheetName, aoa] of Object.entries(sheets)) {
    XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(aoa), sheetName);
  }
  const buf = XLSX.write(wb, { type: "array", bookType: "xlsx" });
  return new File([buf], name, {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
}

function renderDialog(
  onInsert = vi.fn(),
  currentDoc: unknown = { type: "doc" },
) {
  render(
    <ImportDialog
      open
      onOpenChange={() => {}}
      currentDoc={currentDoc}
      onInsert={onInsert}
    />,
  );
  return onInsert;
}

async function upload(file: File) {
  const input = screen.getByLabelText("가져올 파일");
  await userEvent.upload(input, file);
}

describe("ImportDialog", () => {
  it("xlsx 업로드 → 미리보기 후 [표로 가져오기]로 표 노드를 삽입한다", async () => {
    const onInsert = renderDialog();
    await upload(
      makeXlsx({
        Sheet1: [
          ["이름", "점수"],
          ["김철수", 90],
        ],
      }),
    );

    // 미리보기(머리글 + 총계)
    expect(await screen.findByText("총 1행 × 2열")).toBeInTheDocument();
    expect(screen.getByText("점수")).toBeInTheDocument();

    await userEvent.click(
      screen.getByRole("button", { name: "표로 가져오기" }),
    );

    expect(onInsert).toHaveBeenCalledTimes(1);
    const node = onInsert.mock.calls[0][0] as JSONContent;
    expect(node.type).toBe("table");
    // 헤더 행(tableHeader) + 본문 1행
    expect(node.content).toHaveLength(2);
    const header = node.content![0] as JSONContent;
    expect((header.content![0] as JSONContent).type).toBe("tableHeader");
  });

  it("'첫 행을 머리글로'를 끄면 헤더 없이 전부 본문 행", async () => {
    const onInsert = renderDialog();
    await upload(
      makeXlsx({
        S: [
          ["a", "b"],
          ["c", "d"],
        ],
      }),
    );
    await screen.findByText(/총 \d행 × 2열/);

    await userEvent.click(screen.getByRole("switch"));

    await userEvent.click(
      screen.getByRole("button", { name: "표로 가져오기" }),
    );
    const node = onInsert.mock.calls[0][0] as JSONContent;
    // 헤더 없음 → 2행 모두 tableCell
    expect(node.content).toHaveLength(2);
    const first = node.content![0] as JSONContent;
    expect((first.content![0] as JSONContent).type).toBe("tableCell");
  });

  it("여러 시트면 시트를 선택할 수 있다", async () => {
    renderDialog();
    await upload(makeXlsx({ 첫째: [["x"]], 둘째: [["y1", "y2"]] }));

    // 시트 셀렉트 노출
    expect(await screen.findByText("시트")).toBeInTheDocument();
  });

  it("확장자가 안 맞으면 에러를 보여준다", async () => {
    renderDialog();
    // Excel 소스에 확장자가 안 맞는 파일 → accept 필터를 우회해 핸들러 도달(드롭 등) 재현
    const txt = new File(["a,b"], "data.txt", { type: "text/plain" });
    await userEvent.upload(screen.getByLabelText("가져올 파일"), txt, {
      applyAccept: false,
    });

    expect(
      await screen.findByText(".xlsx 파일만 가져올 수 있어요."),
    ).toBeInTheDocument();
  });

  it("CSV 소스로 전환해 .csv를 표로 가져온다", async () => {
    const onInsert = renderDialog();
    await userEvent.click(screen.getByRole("button", { name: "CSV (.csv)" }));

    const csv = new File(["항목,값\nA,1\nB,2"], "data.csv", {
      type: "text/csv",
    });
    await userEvent.upload(screen.getByLabelText("가져올 파일"), csv);

    expect(await screen.findByText("총 2행 × 2열")).toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("button", { name: "표로 가져오기" }),
    );

    expect(onInsert).toHaveBeenCalledTimes(1);
    const node = onInsert.mock.calls[0][0] as JSONContent;
    expect(node.type).toBe("table");
    expect(node.content).toHaveLength(3); // 헤더 + 2행
  });

  it("빈 시트는 '데이터가 없어요' 경고 + 가져오기 비활성", async () => {
    renderDialog();
    await upload(makeXlsx({ Empty: [] }));

    expect(
      await screen.findByText("이 시트에는 데이터가 없어요."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "표로 가져오기" }),
    ).toBeDisabled();
  });

  it("노트가 이미 크면 초과 경고 + 가져오기 비활성", async () => {
    renderDialog(vi.fn(), { type: "doc", big: "x".repeat(1_000_001) });
    await upload(makeXlsx({ S: [["a"]] }));

    expect(
      await screen.findByText("노트가 너무 커서 이 표를 넣을 수 없어요."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "표로 가져오기" }),
    ).toBeDisabled();
  });

  it("대용량 표는 dataset을 만들어 datasetTable 노드를 삽입한다", async () => {
    useAuthStore.setState({ accessToken: "mock-token" });
    let createdCols = 0;
    let bulkRows = 0;
    server.use(
      http.post(`${API_BASE}/datasets`, async ({ request }) => {
        const body = (await request.json()) as { columns: unknown[] };
        createdCols = body.columns.length;
        return HttpResponse.json({
          code: "OK",
          data: { id: 99, columns: body.columns, rowCount: 0 },
        });
      }),
      http.post(`${API_BASE}/datasets/99/rows/bulk`, async ({ request }) => {
        const body = (await request.json()) as { rows: unknown[] };
        bulkRows = body.rows.length;
        return HttpResponse.json({
          code: "OK",
          data: { id: 99, columns: [], rowCount: body.rows.length },
        });
      }),
    );

    // 35열(열 상한 30 초과) → dataset 경로
    const header = Array.from({ length: 35 }, (_, i) => `H${i}`);
    const row = Array.from({ length: 35 }, (_, i) => `v${i}`);
    const onInsert = renderDialog();
    await upload(makeXlsx({ Big: [header, row] }));

    expect(
      await screen.findByText(/데이터 그리드 블록으로 저장/),
    ).toBeInTheDocument();

    await userEvent.click(
      screen.getByRole("button", { name: "표로 가져오기" }),
    );

    await waitFor(() => expect(onInsert).toHaveBeenCalledTimes(1));
    const node = onInsert.mock.calls[0][0] as JSONContent;
    expect(node.type).toBe("datasetTable");
    expect(node.attrs?.datasetId).toBe(99);
    expect(createdCols).toBe(35);
    expect(bulkRows).toBe(1);
  });
});
