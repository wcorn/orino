import { Extension } from "@tiptap/core";
import {
  isNodeRangeSelection,
  NodeRangeSelection,
} from "@tiptap/extension-node-range";
import { type EditorState, TextSelection } from "@tiptap/pm/state";
import type { Editor } from "@tiptap/react";

/**
 * 문서의 모든 블록을 블록 선택(NodeRangeSelection)한다.
 * 텍스트 전체 선택(selectAll)과 달리 "블록들이 골라진" 상태라, 여기서 바로 블록 단위
 * 삭제·이동·드래그가 이어진다.
 */
export function selectAllBlocks(editor: Editor): void {
  const { state, view } = editor;
  const { doc, tr } = state;
  view.dispatch(
    tr.setSelection(NodeRangeSelection.create(doc, 0, doc.content.size)),
  );
}

/** 커서가 놓인 블록의 내용을 통째로 텍스트 선택한다(그 블록 안에서만). */
export function selectCurrentBlockText(editor: Editor): void {
  const { state, view } = editor;
  const { $from } = state.selection;
  view.dispatch(
    state.tr.setSelection(
      TextSelection.create(state.doc, $from.start(), $from.end()),
    ),
  );
}

/** 문서의 모든 블록이 블록 선택된 상태인가. */
export function isAllBlocksSelected(state: EditorState): boolean {
  const { selection, doc } = state;
  if (!isNodeRangeSelection(selection)) return false;
  return selection.from <= 1 && selection.to >= doc.content.size - 1;
}

/** 커서가 놓인 블록의 내용이 이미 통째로 선택돼 있는가. */
export function isWholeBlockSelected(state: EditorState): boolean {
  const { selection } = state;
  if (!(selection instanceof TextSelection)) return false;
  const { $from, $to, from, to } = selection;
  if (!$from.sameParent($to)) return false;
  return from === $from.start() && to === $from.end();
}

/**
 * Cmd/Ctrl+A 점진 선택 — Notion처럼 한 번에 다 잡지 않고 한 칸씩 넓힌다.
 *
 *   블록 안 텍스트 → 문서 전체 블록
 *
 * 기본 동작을 이기려면 우선순위를 올려야 한다. TipTap 코어 keymap이 `Mod-a`를
 * `selectAll()`로, node-range가 `Mod-a`를 "항상 전체 블록"으로 쥐고 있는데, 둘 다
 * 기본 우선순위(100)라 이 확장이 먼저 잡아야 사다리가 성립한다.
 */
export const SelectionLadder = Extension.create({
  name: "selectionLadder",
  priority: 200,

  addKeyboardShortcuts() {
    return {
      "Mod-a": ({ editor }) => {
        const { state } = editor;
        const { selection } = state;

        // 이미 꼭대기 — 더 넓힐 곳이 없다.
        if (isAllBlocksSelected(state)) return true;

        const isTextSel = selection instanceof TextSelection;
        const spansBlocks =
          isTextSel && !selection.$from.sameParent(selection.$to);
        // 빈 블록은 "안쪽 텍스트"가 없어 한 칸이 헛돈다 — 바로 전체로 올린다.
        const blockIsEmpty =
          isTextSel && selection.$from.start() === selection.$from.end();

        // 텍스트 선택이 아니거나(표 등 노드 선택), 이미 블록을 다 잡았거나,
        // 여러 블록에 걸쳐 있으면 → 문서 전체 블록으로.
        if (
          !isTextSel ||
          spansBlocks ||
          blockIsEmpty ||
          isWholeBlockSelected(state)
        ) {
          selectAllBlocks(editor);
          return true;
        }

        selectCurrentBlockText(editor);
        return true;
      },
    };
  },
});
