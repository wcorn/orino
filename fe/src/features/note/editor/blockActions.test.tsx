import { fireEvent } from "@testing-library/react";
import { Editor } from "@tiptap/core";
import { NodeRange } from "@tiptap/extension-node-range";
import StarterKit from "@tiptap/starter-kit";
import { afterEach, describe, expect, it } from "vitest";

import { BlockActions } from "./blockActions";
import { isAllBlocksSelected, SelectionLadder } from "./blockSelection";

/**
 * Esc 하강 사다리와 블록 복제·이동. 키맵 우선순위와 브라우저 기본 동작 차단이 핵심이라
 * 커맨드를 직접 부르지 않고 실제 키 이벤트를 에디터 DOM에 쏜다.
 */

function makeEditor(texts: string[]) {
  const editor = new Editor({
    extensions: [
      StarterKit,
      NodeRange.configure({ key: null }),
      SelectionLadder,
      BlockActions,
    ],
    content: {
      type: "doc",
      content: texts.map((text) => ({
        type: "paragraph",
        content: [{ type: "text", text }],
      })),
    },
  });
  // 핸들러가 view.hasFocus()로 가드하므로 테스트에서도 포커스를 준다(jsdom도 activeElement를 옮긴다).
  editor.view.dom.setAttribute("tabindex", "-1");
  document.body.append(editor.view.dom);
  editor.view.dom.focus();
  return editor;
}

function press(
  editor: Editor,
  key: string,
  opts: Record<string, boolean> = {},
) {
  fireEvent.keyDown(editor.view.dom, { key, ...opts });
}

/** 문단 텍스트를 순서대로. 이동·복제 결과를 눈으로 보듯 확인한다. */
function paragraphs(editor: Editor): string[] {
  const out: string[] = [];
  editor.state.doc.forEach((node) => out.push(node.textContent));
  return out;
}

let editor: Editor | null = null;
afterEach(() => {
  editor?.view.dom.remove();
  editor?.destroy();
  editor = null;
});

describe("Esc 하강 사다리", () => {
  it("편집 중 Esc는 커서가 놓인 블록을 선택한다", () => {
    editor = makeEditor(["첫째", "둘째"]);
    editor.commands.setTextSelection(2);

    press(editor, "Escape");

    expect(editor.state.selection.constructor.name).toContain(
      "NodeRangeSelection",
    );
    // 커서가 있던 블록 하나만 — 문서 전체가 아니다.
    expect(isAllBlocksSelected(editor.state)).toBe(false);
    expect(
      editor.state.doc.textBetween(
        editor.state.selection.from,
        editor.state.selection.to,
      ),
    ).toBe("첫째");
  });

  it("블록 선택 중 Esc는 선택을 풀고 커서를 돌려놓는다", () => {
    editor = makeEditor(["첫째", "둘째"]);
    editor.commands.setTextSelection(2);

    press(editor, "Escape");
    press(editor, "Escape");

    expect(editor.state.selection.constructor.name).toContain("TextSelection");
    expect(editor.state.selection.empty).toBe(true);
  });
});

describe("블록 복제 (Cmd+D)", () => {
  it("커서만 있어도 그 블록을 바로 아래에 복제한다", () => {
    editor = makeEditor(["첫째", "둘째"]);
    editor.commands.setTextSelection(2);

    press(editor, "d", { ctrlKey: true });

    expect(paragraphs(editor)).toEqual(["첫째", "첫째", "둘째"]);
  });

  it("복제본이 선택된 채로 남아 이어서 조작할 수 있다", () => {
    editor = makeEditor(["첫째", "둘째"]);
    editor.commands.setTextSelection(2);

    press(editor, "d", { ctrlKey: true });

    const { from, to } = editor.state.selection;
    expect(editor.state.doc.textBetween(from, to)).toBe("첫째");
    // 원본이 아니라 새로 생긴 두 번째 문단이다.
    expect(from).toBeGreaterThan(0);
  });

  it("여러 블록을 선택했으면 그 묶음을 통째로 복제한다", () => {
    editor = makeEditor(["첫째", "둘째", "셋째"]);
    editor.commands.setTextSelection(2);
    press(editor, "a", { ctrlKey: true }); // 블록 안 텍스트
    press(editor, "a", { ctrlKey: true }); // 문서 전체 블록

    press(editor, "d", { ctrlKey: true });

    expect(paragraphs(editor)).toEqual([
      "첫째",
      "둘째",
      "셋째",
      "첫째",
      "둘째",
      "셋째",
    ]);
  });
});

describe("블록 이동 (Cmd+Shift+↑/↓)", () => {
  it("위로 옮기면 앞 블록과 자리가 바뀐다", () => {
    editor = makeEditor(["첫째", "둘째", "셋째"]);
    editor.commands.setTextSelection(8); // "둘째" 안

    press(editor, "ArrowUp", { ctrlKey: true, shiftKey: true });

    expect(paragraphs(editor)).toEqual(["둘째", "첫째", "셋째"]);
  });

  it("아래로 옮기면 뒤 블록과 자리가 바뀐다", () => {
    editor = makeEditor(["첫째", "둘째", "셋째"]);
    editor.commands.setTextSelection(2); // "첫째" 안

    press(editor, "ArrowDown", { ctrlKey: true, shiftKey: true });

    expect(paragraphs(editor)).toEqual(["둘째", "첫째", "셋째"]);
  });

  it("옮긴 뒤에도 그 블록이 선택돼 있어 연속으로 옮길 수 있다", () => {
    editor = makeEditor(["첫째", "둘째", "셋째"]);
    editor.commands.setTextSelection(14); // "셋째" 안

    press(editor, "ArrowUp", { ctrlKey: true, shiftKey: true });
    press(editor, "ArrowUp", { ctrlKey: true, shiftKey: true });

    expect(paragraphs(editor)).toEqual(["셋째", "첫째", "둘째"]);
  });

  it("맨 끝에서 더 옮기려 하면 아무 일도 일어나지 않는다", () => {
    editor = makeEditor(["첫째", "둘째"]);
    editor.commands.setTextSelection(2); // "첫째" 안

    press(editor, "ArrowUp", { ctrlKey: true, shiftKey: true });

    expect(paragraphs(editor)).toEqual(["첫째", "둘째"]);
  });
});
