import { mergeAttributes, Node } from "@tiptap/core";
import { Fragment, type Node as PMNode, Slice } from "@tiptap/pm/model";
import { NodeSelection, Plugin } from "@tiptap/pm/state";
import { ReactNodeViewRenderer } from "@tiptap/react";

import { DatasetTableView } from "./DatasetTableView";

export interface DatasetTableAttrs {
  datasetId: number;
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
  draggable: true,

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
    return ReactNodeViewRenderer(DatasetTableView);
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
