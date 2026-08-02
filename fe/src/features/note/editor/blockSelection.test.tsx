import { fireEvent } from "@testing-library/react";
import { Editor } from "@tiptap/core";
import { NodeRange } from "@tiptap/extension-node-range";
import StarterKit from "@tiptap/starter-kit";
import { afterEach, describe, expect, it } from "vitest";

import { isAllBlocksSelected, SelectionLadder } from "./blockSelection";

/**
 * Cmd+A 점진 선택 — 한 번에 다 잡지 않고 "블록 안 텍스트 → 문서 전체 블록" 순으로 넓힌다.
 * 키맵은 우선순위 싸움이 핵심이라(코어 keymap의 selectAll, node-range의 전체-블록 Mod-a)
 * 커맨드를 직접 부르지 않고 **실제 키 이벤트**를 에디터 DOM에 쏴서 검증한다.
 */

function makeEditor(content: unknown) {
  return new Editor({
    extensions: [
      StarterKit,
      NodeRange.configure({ key: null }),
      SelectionLadder,
    ],
    content: content as never,
  });
}

/** Cmd+A 한 번. ProseMirror는 view.dom의 keydown에서 키맵을 돌린다. */
function pressSelectAll(editor: Editor) {
  fireEvent.keyDown(editor.view.dom, {
    key: "a",
    code: "KeyA",
    ctrlKey: true,
  });
}

const doc = {
  type: "doc",
  content: [
    { type: "paragraph", content: [{ type: "text", text: "첫째 줄" }] },
    { type: "paragraph", content: [{ type: "text", text: "둘째 줄" }] },
  ],
};

let editor: Editor | null = null;
afterEach(() => {
  editor?.destroy();
  editor = null;
});

describe("Cmd+A 점진 선택", () => {
  it("1번째는 커서가 놓인 블록 안 텍스트만 잡는다", () => {
    editor = makeEditor(doc);
    // "첫째 줄" 안에 커서를 둔다.
    editor.commands.setTextSelection(3);

    pressSelectAll(editor);

    const { from, to, $from } = editor.state.selection;
    expect(from).toBe($from.start());
    expect(to).toBe($from.end());
    // 둘째 줄까지 넘어가지 않는다.
    expect(editor.state.doc.textBetween(from, to)).toBe("첫째 줄");
    expect(isAllBlocksSelected(editor.state)).toBe(false);
  });

  it("2번째에 비로소 문서 전체 블록이 잡힌다", () => {
    editor = makeEditor(doc);
    editor.commands.setTextSelection(3);

    pressSelectAll(editor);
    pressSelectAll(editor);

    expect(isAllBlocksSelected(editor.state)).toBe(true);
    // 텍스트 선택이 아니라 블록 선택이다 — 여기서 블록 단위 삭제·이동이 이어진다.
    expect(editor.state.selection.constructor.name).toContain(
      "NodeRangeSelection",
    );
  });

  it("3번째는 꼭대기라 더 넓어지지 않는다", () => {
    editor = makeEditor(doc);
    editor.commands.setTextSelection(3);

    pressSelectAll(editor);
    pressSelectAll(editor);
    const after2 = editor.state.selection.toJSON();
    pressSelectAll(editor);

    expect(editor.state.selection.toJSON()).toEqual(after2);
  });

  it("빈 블록에선 헛돌지 않고 바로 문서 전체로 간다", () => {
    editor = makeEditor({
      type: "doc",
      content: [
        { type: "paragraph" },
        { type: "paragraph", content: [{ type: "text", text: "아래" }] },
      ],
    });
    editor.commands.setTextSelection(1);

    pressSelectAll(editor);

    expect(isAllBlocksSelected(editor.state)).toBe(true);
  });

  it("여러 블록에 걸친 선택에서 누르면 현재 블록으로 좁아지지 않고 전체로 넓어진다", () => {
    editor = makeEditor(doc);
    // 첫째 줄 중간 ~ 둘째 줄 중간.
    editor.commands.setTextSelection({ from: 3, to: 12 });

    pressSelectAll(editor);

    expect(isAllBlocksSelected(editor.state)).toBe(true);
  });
});
