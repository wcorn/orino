import { mergeAttributes, Node } from "@tiptap/core";
import { Fragment, type Node as PMNode, Slice } from "@tiptap/pm/model";
import { Plugin } from "@tiptap/pm/state";
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
