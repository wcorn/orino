import { Extension } from "@tiptap/core";
import {
  isNodeRangeSelection,
  NodeRangeSelection,
} from "@tiptap/extension-node-range";
import type { Node } from "@tiptap/pm/model";
import { type EditorState, TextSelection } from "@tiptap/pm/state";
import type { Editor } from "@tiptap/react";

/** 문서 최상위 블록들의 위치 목록. 블록 단위 조작은 전부 이 경계를 기준으로 한다. */
function topLevelBlocks(doc: Node): { from: number; to: number }[] {
  const out: { from: number; to: number }[] = [];
  let pos = 0;
  doc.forEach((node) => {
    out.push({ from: pos, to: pos + node.nodeSize });
    pos += node.nodeSize;
  });
  return out;
}

/**
 * 지금 조작 대상이 되는 블록 구간.
 * 블록 선택이면 그 블록들, 커서만 있으면 커서가 놓인 블록 하나(Notion과 동일 —
 * 블록을 고르지 않아도 "지금 있는 블록"에 복제·이동이 먹는다).
 */
export function targetBlockRange(
  state: EditorState,
): { from: number; to: number; firstIndex: number; lastIndex: number } | null {
  const { doc, selection } = state;
  const blocks = topLevelBlocks(doc);
  if (blocks.length === 0) return null;

  const covered: number[] = [];
  blocks.forEach((b, i) => {
    if (b.from < selection.to && b.to > selection.from) covered.push(i);
  });
  // 블록 경계에 딱 붙은 커서는 위 조건에 안 걸린다 — 그 자리를 품는 블록으로 되돌린다.
  if (covered.length === 0) {
    const i = blocks.findIndex(
      (b) => b.from <= selection.from && b.to >= selection.from,
    );
    if (i < 0) return null;
    covered.push(i);
  }

  const firstIndex = covered[0];
  const lastIndex = covered[covered.length - 1];
  return {
    from: blocks[firstIndex].from,
    to: blocks[lastIndex].to,
    firstIndex,
    lastIndex,
  };
}

/** 커서가 놓인 블록을 블록 선택으로 승격한다(Esc 1단). */
export function selectCurrentBlock(editor: Editor): boolean {
  const { state, view } = editor;
  const range = targetBlockRange(state);
  if (!range) return false;
  view.dispatch(
    state.tr.setSelection(
      NodeRangeSelection.create(state.doc, range.from, range.to),
    ),
  );
  return true;
}

/** 블록 선택을 풀고 그 자리에 커서를 돌려놓는다(Esc 2단). */
export function collapseBlockSelection(editor: Editor): boolean {
  const { state, view } = editor;
  if (!isNodeRangeSelection(state.selection)) return false;
  const pos = state.selection.from;
  view.dispatch(
    state.tr.setSelection(TextSelection.near(state.doc.resolve(pos))),
  );
  return true;
}

/** 대상 블록들을 바로 아래에 복제하고, 복제본을 선택 상태로 남긴다. */
export function duplicateBlocks(editor: Editor): boolean {
  const { state, view } = editor;
  const range = targetBlockRange(state);
  if (!range) return false;

  const slice = state.doc.slice(range.from, range.to);
  const size = range.to - range.from;
  const tr = state.tr.insert(range.to, slice.content);
  // 복제 직후 계속 조작할 수 있게 새로 생긴 쪽을 잡아준다.
  tr.setSelection(NodeRangeSelection.create(tr.doc, range.to, range.to + size));
  view.dispatch(tr.scrollIntoView());
  return true;
}

/**
 * 대상 블록들을 위/아래 이웃과 자리바꿈한다.
 * 지우고 다시 넣는 방식이라, 아래로 옮길 땐 삭제로 줄어든 만큼(size) 넣을 위치를 당겨야 한다.
 */
export function moveBlocks(editor: Editor, dir: -1 | 1): boolean {
  const { state, view } = editor;
  const range = targetBlockRange(state);
  if (!range) return false;

  const blocks = topLevelBlocks(state.doc);
  const neighbor =
    dir < 0 ? blocks[range.firstIndex - 1] : blocks[range.lastIndex + 1];
  if (!neighbor) return false; // 이미 끝 — 조용히 아무 일도 안 한다.

  const size = range.to - range.from;
  const slice = state.doc.slice(range.from, range.to);
  const insertAt = dir < 0 ? neighbor.from : neighbor.to - size;

  const tr = state.tr.delete(range.from, range.to);
  tr.insert(insertAt, slice.content);
  tr.setSelection(NodeRangeSelection.create(tr.doc, insertAt, insertAt + size));
  view.dispatch(tr.scrollIntoView());
  return true;
}

/**
 * 블록 단위 조작 — Esc 하강 사다리와 복제·이동.
 *
 * 모든 핸들러는 `view.hasFocus()`로 가드한다. 표(데이터 그리드)는 자체 단축키를 갖고 있고
 * 포커스가 셀 입력창에 있는데, 셀 입력창은 에디터 DOM 안이라 키가 여기까지 올라온다.
 * 가드가 없으면 셀에서 Esc·Cmd+D를 눌렀을 때 본문 블록이 함께 반응한다.
 */
export const BlockActions = Extension.create({
  name: "blockActions",
  priority: 200,

  addKeyboardShortcuts() {
    const guarded = (run: (editor: Editor) => boolean) => {
      return ({ editor }: { editor: Editor }) => {
        if (!editor.view.hasFocus()) return false;
        return run(editor);
      };
    };

    return {
      // 편집 → 블록 선택 → 커서 복귀.
      Escape: guarded((editor) =>
        isNodeRangeSelection(editor.state.selection)
          ? collapseBlockSelection(editor)
          : selectCurrentBlock(editor),
      ),
      // 브라우저 기본 동작(북마크 추가)을 막고 블록을 복제한다.
      "Mod-d": guarded(duplicateBlocks),
      "Mod-Shift-ArrowUp": guarded((editor) => moveBlocks(editor, -1)),
      "Mod-Shift-ArrowDown": guarded((editor) => moveBlocks(editor, 1)),
    };
  },
});
