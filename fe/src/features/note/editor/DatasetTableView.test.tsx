import {
  createEvent,
  fireEvent,
  screen,
  waitFor,
} from "@testing-library/react";
import { Editor } from "@tiptap/core";
import { EditorContent } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { DatasetTable } from "./datasetTable";

const API_BASE = "https://api.orino.dev/api";

function mockDataset() {
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
    http.get(`${API_BASE}/datasets/1/merges`, () =>
      HttpResponse.json({ code: "OK", data: { merges: [] } }),
    ),
  );
}

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

function makeEditor() {
  return new Editor({
    extensions: [StarterKit, DatasetTable],
    content: {
      type: "doc",
      content: [
        { type: "paragraph", content: [{ type: "text", text: "위" }] },
        { type: "datasetTable", attrs: { datasetId: 1 } },
      ],
    },
  });
}

// 표가 NodeSelection으로 선택되면 PM이 표 래퍼에 draggable=true를 심어(spec.draggable:false여도),
// 셀을 드래그해 범위 선택하려 할 때 표가 통째로 끌려나온다. NodeViewWrapper의 onDragStartCapture로
// 네이티브 드래그를 취소한다. 반드시 capture 변형이어야 한다 — NodeViewWrapper가 onDragStart prop을
// 자기 것으로 덮어써 무효화하기 때문(이 회귀를 잡는 테스트).
describe("DatasetTableView — 셀 드래그 시 표가 끌려나오지 않는다", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
    vi.spyOn(Element.prototype, "getBoundingClientRect").mockReturnValue(
      FAKE_RECT,
    );
    mockDataset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("표 노드가 선택된 상태에서 표 래퍼의 dragstart는 preventDefault되고 PM이 표를 끌지 않는다", async () => {
    const editor = makeEditor();
    renderWithRouter(<EditorContent editor={editor} />);

    // NodeView(표 래퍼)가 마운트될 때까지 기다린다.
    const wrap = await waitFor(() => {
      const el = editor.view.dom.querySelector("[data-dataset-table]");
      if (!el) throw new Error("표 래퍼 미마운트");
      return el as HTMLElement;
    });

    // 표를 NodeSelection으로 선택(셀 클릭 시 blockSelected 되는 상태 재현).
    let pos = -1;
    editor.state.doc.descendants((n, p) => {
      if (n.type.name === "datasetTable") pos = p;
    });
    editor.commands.setNodeSelection(pos);

    // 실제 드래그 소스는 NodeViewWrapper가 아니라 그 부모(TipTap이 만든 NodeView 컨테이너,
    // = PM이 draggable=true를 심는 nodeDOM)다. 여기서 시작되는 네이티브 dragstart를 시뮬레이션한다.
    const nodeDom = wrap.parentElement as HTMLElement;
    expect(nodeDom).toBe(editor.view.nodeDOM(pos));
    const dragEvent = createEvent.dragStart(nodeDom);
    fireEvent(nodeDom, dragEvent);

    // 바깥 nodeDOM에 건 dragstart 리스너가 preventDefault → 네이티브 드래그 취소 → 표가 안 끌린다.
    // (핸들러를 NodeViewWrapper 자식에 걸면 부모발 이 이벤트를 못 잡아 defaultPrevented=false로 실패한다.)
    expect(dragEvent.defaultPrevented).toBe(true);

    editor.destroy();
  });
});

// TipTap의 `selected`는 "이 표가 단독 선택됨"이 아니라 "선택이 이 표를 덮음"이라(isNodeViewSelected),
// Cmd+A(문서 전체 선택)에서도 참이 된다. 그대로 그리드에 넘기면 표를 건드리지도 않았는데 그리드가
// 첫 셀을 잡고 셀 입력창이 DOM 포커스를 가져가, 그 뒤 키 입력이 에디터 대신 셀로 새어 Backspace가
// 먹통이 됐다(#1008). 표'만' 선택됐을 때로 좁힌 것을 검증한다.
describe("DatasetTableView — 표를 덮는 선택이 셀 포커스를 뺏지 않는다", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
    vi.spyOn(Element.prototype, "getBoundingClientRect").mockReturnValue(
      FAKE_RECT,
    );
    mockDataset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  /** 표 노드의 문서 내 위치. */
  function datasetTablePos(editor: Editor): number {
    let pos = -1;
    editor.state.doc.descendants((n, p) => {
      if (n.type.name === "datasetTable") pos = p;
    });
    return pos;
  }

  const activeCellInput = () =>
    screen.queryByLabelText("1행 1열 셀 (입력하면 편집)");

  it("문서 전체 선택(Cmd+A)은 표의 첫 셀을 잡지 않는다", async () => {
    const editor = makeEditor();
    renderWithRouter(<EditorContent editor={editor} />);
    await screen.findByTestId("dataset-grid");

    editor.commands.selectAll();

    // NodeView의 선택 반영은 rAF로 미뤄지므로 붙잡을 시간을 준다.
    await waitFor(() => {
      expect(editor.state.selection.from).toBeLessThanOrEqual(
        datasetTablePos(editor),
      );
    });
    await new Promise((r) => setTimeout(r, 50));

    expect(activeCellInput()).not.toBeInTheDocument();
    editor.destroy();
  });

  it("표만 선택하면(NodeSelection) 첫 셀을 잡아 바로 타이핑할 수 있다", async () => {
    const editor = makeEditor();
    renderWithRouter(<EditorContent editor={editor} />);
    await screen.findByTestId("dataset-grid");

    editor.commands.setNodeSelection(datasetTablePos(editor));

    await waitFor(() => {
      expect(activeCellInput()).toBeInTheDocument();
    });
    editor.destroy();
  });
});
