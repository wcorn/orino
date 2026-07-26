import { mergeAttributes, Node } from "@tiptap/core";
import { Fragment, type Node as PMNode, Slice } from "@tiptap/pm/model";
import { NodeSelection, Plugin } from "@tiptap/pm/state";
import { ReactNodeViewRenderer } from "@tiptap/react";

import { DatasetTableView } from "./DatasetTableView";

export interface DatasetTableAttrs {
  datasetId: number;
}

/**
 * 그리드(셀) 안에서 일어난, 그리드가 스스로 처리하는 이벤트인가. 이런 이벤트는 에디터
 * (ProseMirror)가 건드리지 않게 한다({@link Node.addNodeView}의 stopEvent).
 *
 * <p>그리드는 contentEditable=false NodeView 안의 진짜 {@code <input>}으로 편집을 처리한다.
 * stopEvent로 막지 않으면 PM의 편집 핸들러가 셀의 이벤트까지 가로챈다:
 * <ul>
 *   <li><b>keypress/beforeinput</b>: PM이 preventDefault → 셀에 글자가 안 들어간다(영어 입력 불가).
 *   <li><b>composition*</b>: 노드가 선택된 상태면 PM이 조합 텍스트로 노드를 대체 → 한글 치면 표 삭제.
 *   <li><b>clipboard(paste/copy/cut)</b>: PM의 handlePaste가 표 조각을 새 표로 삽입(왔다갔다).
 *   <li><b>dragstart</b>: 표가 NodeSelection으로 선택된 상태면 PM의 MouseDown이 spec.draggable:false를
 *       무시하고 노드 DOM(표 래퍼)에 draggable=true를 심는다(NodeSelection 분기). 그 뒤 셀을 드래그해
 *       범위 선택하려 하면 드래그 소스가 표 래퍼가 되어(gridBox의 onDragStart는 자식이라 우회됨) PM의
 *       dragstart가 표 노드를 통째로 끌어낸다. stopEvent로 막아 PM이 이 드래그를 손대지 않게 한다
 *       (네이티브 드래그 취소는 {@link DatasetTableView}의 NodeViewWrapper onDragStartCapture가 맡는다).
 * </ul>
 * 마우스·포커스 등은 PM에 맡긴다(그리드가 포커스 이동에 그 동작이 필요하다). drop/dragover는 막지 않는다
 * — 블록 이동 핸들(⣿)로 표 근처에 다른 블록을 드롭하는 동작이 PM 드롭 처리에 의존한다.
 */
export function isGridSelfHandledEvent(event: Event): boolean {
  switch (event.type) {
    case "keydown":
    case "keypress":
    case "keyup":
    case "beforeinput":
    case "input":
    case "compositionstart":
    case "compositionupdate":
    case "compositionend":
    case "paste":
    case "copy":
    case "cut":
    case "dragstart":
      return true;
    default:
      return false;
  }
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
      // 셀 안의 키보드·입력·조합·클립보드 이벤트는 에디터가 손대지 않는다(그리드가 직접 처리).
      // 안 막으면 PM이 keypress를 preventDefault(영어 입력 불가)하거나 조합으로 노드를 대체
      // (한글 치면 표 삭제)하고, 붙여넣기는 새 표를 만든다.
      stopEvent: ({ event }) => isGridSelfHandledEvent(event),
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
