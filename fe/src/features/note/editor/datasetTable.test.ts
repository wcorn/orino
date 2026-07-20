import { Editor } from "@tiptap/core";
import { NodeSelection } from "@tiptap/pm/state";
import StarterKit from "@tiptap/starter-kit";
import { describe, expect, it } from "vitest";

import { collectDatasetIds, DatasetTable } from "./datasetTable";

describe("collectDatasetIds", () => {
  it("중첩 doc에서 모든 datasetTable datasetId를 수집한다", () => {
    const doc = {
      type: "doc",
      content: [
        { type: "paragraph", content: [{ type: "text", text: "hi" }] },
        { type: "datasetTable", attrs: { datasetId: 7 } },
        {
          type: "bulletList",
          content: [
            {
              type: "listItem",
              content: [{ type: "datasetTable", attrs: { datasetId: 8 } }],
            },
          ],
        },
      ],
    };

    expect([...collectDatasetIds(doc)].sort()).toEqual([7, 8]);
  });

  it("datasetTable이 없으면 빈 집합", () => {
    const doc = {
      type: "doc",
      content: [{ type: "paragraph", content: [{ type: "text", text: "x" }] }],
    };
    expect(collectDatasetIds(doc).size).toBe(0);
  });

  it("datasetId가 number가 아니면 무시한다", () => {
    const doc = {
      type: "doc",
      content: [{ type: "datasetTable", attrs: {} }],
    };
    expect(collectDatasetIds(doc).size).toBe(0);
  });
});

describe("DatasetTable 노드 스펙", () => {
  it("atom block 이고 draggable=false 다(블록 이동은 외부 드래그 핸들이 담당)", () => {
    expect(DatasetTable.config.atom).toBe(true);
    // 표 전체가 draggable이면 셀 드래그 선택과 충돌해 이동이 꼬인다 → 노드는 끄고 핸들로만 이동.
    expect(DatasetTable.config.draggable).toBe(false);
  });
});

describe("DatasetTable 노드 선택 시 키 차단(표 단축키 제거)", () => {
  function editorWithTable() {
    const element = document.createElement("div");
    document.body.appendChild(element);
    return new Editor({
      element,
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
  // 표 블록 키 차단은 우리 플러그인에만 있는 handleTextInput으로 골라낸다.
  // (ProseMirror prop 타입이 this 바인딩을 요구해, 호출용으로 느슨하게 캐스팅한다.)
  type GuardProps = {
    handleKeyDown: (view: unknown, event: KeyboardEvent) => boolean;
    handleTextInput: (
      view: unknown,
      from: number,
      to: number,
      text: string,
    ) => boolean;
  };
  function guardPlugin(editor: Editor): GuardProps {
    const plugin = editor.view.state.plugins.find(
      (p) => p.props.handleTextInput,
    );
    return plugin!.props as unknown as GuardProps;
  }
  function selectTableNode(editor: Editor) {
    let pos = -1;
    editor.state.doc.descendants((node, p) => {
      if (node.type.name === "datasetTable") pos = p;
    });
    editor.commands.setNodeSelection(pos);
    return pos;
  }
  const keydown = (k: string, init: KeyboardEventInit = {}) =>
    new KeyboardEvent("keydown", { key: k, ...init });

  it("표 노드가 선택되면 문자·Backspace·Delete가 막혀 표가 유지된다", () => {
    const editor = editorWithTable();
    const pos = selectTableNode(editor);
    expect(editor.state.selection instanceof NodeSelection).toBe(true);
    const props = guardPlugin(editor);
    const onKey = (k: string, init?: KeyboardEventInit) =>
      props.handleKeyDown(editor.view, keydown(k, init));

    expect(onKey("d")).toBe(true); // 글자 → 노드 교체(=표 삭제) 차단
    expect(onKey("Backspace")).toBe(true);
    expect(onKey("Delete")).toBe(true);
    // 문자 삽입(beforeinput) 경로도 막는다.
    expect(props.handleTextInput(editor.view, pos, pos + 1, "d")).toBe(true);
    editor.destroy();
  });

  it("방향키·Cmd 조합은 통과한다(표에서 빠져나가기·복사 등)", () => {
    const editor = editorWithTable();
    selectTableNode(editor);
    const props = guardPlugin(editor);
    const onKey = (k: string, init?: KeyboardEventInit) =>
      props.handleKeyDown(editor.view, keydown(k, init));

    expect(onKey("ArrowDown")).toBe(false);
    expect(onKey("Escape")).toBe(false);
    expect(onKey("c", { metaKey: true })).toBe(false);
    editor.destroy();
  });

  it("일반 문단에선 문자 입력을 막지 않는다", () => {
    const editor = editorWithTable();
    editor.commands.setTextSelection(1); // 문단 안(텍스트 선택)
    const props = guardPlugin(editor);
    expect(props.handleKeyDown(editor.view, keydown("d"))).toBe(false);
    expect(props.handleTextInput(editor.view, 1, 1, "d")).toBe(false);
    editor.destroy();
  });
});
