import { mergeAttributes, Node } from "@tiptap/core";
import { Fragment, type Node as PMNode, Slice } from "@tiptap/pm/model";
import { NodeSelection, Plugin } from "@tiptap/pm/state";
import { ReactNodeViewRenderer } from "@tiptap/react";

import { DatasetTableView } from "./DatasetTableView";

export interface DatasetTableAttrs {
  datasetId: number;
}

/**
 * 그리드(셀) 안에서 일어난 클립보드 이벤트인가. 이런 이벤트는 에디터(ProseMirror)가 처리하지
 * 않게 한다({@link Node.addNodeView}의 stopEvent) — 안 그러면 셀 붙여넣기가 에디터의
 * handlePaste까지 버블링돼 노트에 표가 하나 더 생긴다(그리드는 자체 핸들러로 붙여넣는다).
 */
export function isGridClipboardEvent(event: Event): boolean {
  return (
    event.type === "paste" || event.type === "copy" || event.type === "cut"
  );
}

declare module "@tiptap/core" {
  interface Commands<ReturnType> {
    datasetTable: {
      insertDatasetTable: (attrs: DatasetTableAttrs) => ReturnType;
    };
  }
}

/**
 * 대용량 편집 표(데이터 그리드 블록). atom block 노드로, attrs.datasetId만 담는다.
 * 실제 표 데이터는 별도 dataset 리소스에 있고 NodeView(가상화 그리드)가 지연 로드·편집한다.
 */
export const DatasetTable = Node.create({
  name: "datasetTable",
  group: "block",
  atom: true,
  selectable: true,
  // draggable을 노드에 켜면 표 전체(react-renderer)가 draggable="true"가 되어, 셀을 드래그해
  // 범위 선택하려 할 때 브라우저가 블록(또는 선택된 텍스트)을 대신 끌어 이동이 꼬인다.
  // 블록 이동은 외부 드래그 핸들(⣿)이 view.dragging을 직접 세워 처리하므로 여기선 끈다.
  draggable: false,

  addAttributes() {
    return {
      datasetId: {
        default: null,
        parseHTML: (el) => {
          const v = el.getAttribute("data-dataset-id");
          return v ? Number(v) : null;
        },
        renderHTML: (attrs) =>
          attrs.datasetId == null
            ? {}
            : { "data-dataset-id": String(attrs.datasetId) },
      },
    };
  },

  parseHTML() {
    return [{ tag: "div[data-dataset-table]" }];
  },

  renderHTML({ HTMLAttributes }) {
    return [
      "div",
      mergeAttributes(HTMLAttributes, { "data-dataset-table": "" }),
    ];
  },

  addNodeView() {
    return ReactNodeViewRenderer(DatasetTableView, {
      // 셀 안 클립보드 이벤트는 에디터가 손대지 않는다(그리드가 직접 처리) — 셀 붙여넣기가
      // handlePaste까지 닿아 노트에 표가 하나 더 생기던 문제를 막는다.
      stopEvent: ({ event }) => isGridClipboardEvent(event),
    });
  },

  addCommands() {
    return {
      insertDatasetTable:
        (attrs: DatasetTableAttrs) =>
        ({ commands }) =>
          commands.insertContent({ type: this.name, attrs }),
    };
  },

  // 복붙 복제 차단: 붙여넣기 slice에서 datasetTable 제거(datasetId 중복 방지).
  addProseMirrorPlugins() {
    const nodeType = this.name;
    return [
      new Plugin({
        props: {
          transformPasted(slice) {
            const kept: PMNode[] = [];
            slice.content.forEach((node) => {
              if (node.type.name !== nodeType) kept.push(node);
            });
            return new Slice(
              Fragment.fromArray(kept),
              slice.openStart,
              slice.openEnd,
            );
          },
          // 표 블록(노드)이 선택된 상태에서 문서를 바꾸는 키를 전부 막는다.
          // ProseMirror는 노드가 선택됐을 때 글자를 치면 노드를 그 글자로 교체(=표 삭제)하고,
          // Backspace/Delete로도 지운다. 셀 선택 없이 블록만 잡혔을 때 'd' 등으로 표가
          // 사라지던 문제를 없앤다. 표 삭제는 표 우클릭 메뉴의 '표 삭제'로만 한다.
          handleKeyDown(view, event) {
            const { selection } = view.state;
            const nodeSelected =
              selection instanceof NodeSelection &&
              selection.node.type.name === nodeType;
            if (!nodeSelected) return false;
            if (event.key === "Backspace" || event.key === "Delete") {
              return true;
            }
            // 순수 문자/숫자/기호 한 글자(수정키 없음)로 노드가 교체되는 것을 막는다.
            // 방향키·Esc·Tab, Cmd/Ctrl 조합(복사·실행취소 등)은 그대로 통과시킨다.
            return (
              event.key.length === 1 &&
              !event.ctrlKey &&
              !event.metaKey &&
              !event.altKey
            );
          },
          // 문자 삽입 경로(beforeinput)도 막는다 — keydown만으로 잡히지 않는 브라우저 대비.
          handleTextInput(view) {
            const { selection } = view.state;
            return (
              selection instanceof NodeSelection &&
              selection.node.type.name === nodeType
            );
          },
        },
      }),
    ];
  },
});

/** Tiptap doc JSON에서 모든 datasetTable 노드의 datasetId 집합을 수집한다(정리/추적용). */
export function collectDatasetIds(doc: unknown): Set<number> {
  const ids = new Set<number>();
  const walk = (node: unknown) => {
    if (!node || typeof node !== "object") return;
    const n = node as {
      type?: string;
      attrs?: { datasetId?: number };
      content?: unknown[];
    };
    if (n.type === "datasetTable" && typeof n.attrs?.datasetId === "number") {
      ids.add(n.attrs.datasetId);
    }
    if (Array.isArray(n.content)) n.content.forEach(walk);
  };
  walk(doc);
  return ids;
}
