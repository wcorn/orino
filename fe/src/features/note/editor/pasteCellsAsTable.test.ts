import { Editor } from "@tiptap/core";
import StarterKit from "@tiptap/starter-kit";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";

import { DatasetTable } from "./datasetTable";
import { insertTableFromCells } from "./pasteCellsAsTable";

const API_BASE = "https://api.orino.dev/api";

function makeEditor() {
  const el = document.createElement("div");
  document.body.appendChild(el);
  return new Editor({
    element: el,
    extensions: [StarterKit, DatasetTable],
    content: { type: "doc", content: [{ type: "paragraph" }] },
  });
}

function datasetTableIds(editor: Editor): number[] {
  const ids: number[] = [];
  editor.state.doc.descendants((n) => {
    if (n.type.name === "datasetTable") ids.push(n.attrs.datasetId as number);
  });
  return ids;
}

describe("insertTableFromCells", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
  });

  it("복사한 셀 TSV로 새 표(dataset)를 만들어 삽입한다", async () => {
    let createdColumns: unknown[] = [];
    let bulkRows: unknown = null;
    server.use(
      http.post(`${API_BASE}/datasets`, async ({ request }) => {
        createdColumns = ((await request.json()) as { columns: unknown[] })
          .columns;
        return HttpResponse.json({
          code: "OK",
          data: { id: 42, columns: createdColumns, rowCount: 0 },
        });
      }),
      http.post(`${API_BASE}/datasets/42/rows/bulk`, async ({ request }) => {
        bulkRows = ((await request.json()) as { rows: unknown }).rows;
        return HttpResponse.json({
          code: "OK",
          data: { id: 42, columns: createdColumns, rowCount: 2 },
        });
      }),
    );

    const editor = makeEditor();
    await insertTableFromCells(editor, "네트워크\t92\n운영체제\t78");

    // 새 datasetTable 노드가 datasetId=42로 삽입된다.
    expect(datasetTableIds(editor)).toEqual([42]);
    // 값만(헤더 없음) 2행 2열로 벌크 업로드된다.
    expect(bulkRows).toEqual([
      ["네트워크", "92"],
      ["운영체제", "78"],
    ]);
    // 헤더가 없으니 열은 기본 2개(값 폭 기준).
    expect(createdColumns.length).toBe(2);
    editor.destroy();
  });

  it("빈 TSV는 표를 만들지 않는다", async () => {
    const editor = makeEditor();
    await insertTableFromCells(editor, "");
    expect(datasetTableIds(editor)).toEqual([]);
    editor.destroy();
  });
});
