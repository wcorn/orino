import { mergeAttributes, Node } from "@tiptap/core";
import { Fragment, type Node as PMNode, Slice } from "@tiptap/pm/model";
import { Plugin } from "@tiptap/pm/state";
import { ReactNodeViewRenderer } from "@tiptap/react";

import { ChildPageView } from "./ChildPageView";

export interface ChildPageAttrs {
  noteId: number;
  title: string;
}

export interface ChildPageOptions {
  onOpen: (noteId: number) => void;
  /** 라이브 제목 조회용. attrs.title은 삽입 시점 캐시라 stale할 수 있어 트리에서 보강한다. */
  materialId: number;
}

declare module "@tiptap/core" {
  interface Commands<ReturnType> {
    childPage: {
      insertChildPage: (attrs: ChildPageAttrs) => ReturnType;
    };
  }
}

/**
 * Notion 스타일 인라인 하위 페이지. atom block 노드.
 * attrs.noteId = 실제 note 레코드 id, attrs.title = 표시용 캐시.
 */
export const ChildPage = Node.create<ChildPageOptions>({
  name: "childPage",
  group: "block",
  atom: true,
  selectable: true,
  draggable: false,

  addOptions() {
    return {
      onOpen: () => {},
      materialId: 0,
    };
  },

  addAttributes() {
    return {
      noteId: {
        default: null,
        parseHTML: (el) => {
          const v = el.getAttribute("data-note-id");
          return v ? Number(v) : null;
        },
        renderHTML: (attrs) =>
          attrs.noteId == null ? {} : { "data-note-id": String(attrs.noteId) },
      },
      title: {
        default: "제목 없음",
        parseHTML: (el) => el.getAttribute("data-title") ?? "제목 없음",
        renderHTML: (attrs) => ({ "data-title": attrs.title }),
      },
    };
  },

  parseHTML() {
    return [{ tag: "div[data-child-page]" }];
  },

  renderHTML({ HTMLAttributes }) {
    return ["div", mergeAttributes(HTMLAttributes, { "data-child-page": "" })];
  },

  addNodeView() {
    return ReactNodeViewRenderer(ChildPageView);
  },

  addCommands() {
    return {
      insertChildPage:
        (attrs: ChildPageAttrs) =>
        ({ commands }) =>
          commands.insertContent({
            type: this.name,
            attrs,
          }),
    };
  },

  // 복붙/복제 차단: 붙여넣기 slice에서 childPage 노드 제거 (noteId 중복 방지)
  addProseMirrorPlugins() {
    const nodeType = this.name;
    return [
      new Plugin({
        props: {
          transformPasted(slice) {
            const kept: PMNode[] = [];
            slice.content.forEach((node) => {
              if (node.type.name !== nodeType) {
                kept.push(node);
              }
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

/**
 * Tiptap doc JSON에서 모든 childPage 노드의 noteId 집합을 수집한다.
 */
export function collectChildPageIds(doc: unknown): Set<number> {
  const ids = new Set<number>();
  const walk = (node: unknown) => {
    if (!node || typeof node !== "object") return;
    const n = node as {
      type?: string;
      attrs?: { noteId?: number };
      content?: unknown[];
    };
    if (n.type === "childPage" && typeof n.attrs?.noteId === "number") {
      ids.add(n.attrs.noteId);
    }
    if (Array.isArray(n.content)) {
      n.content.forEach(walk);
    }
  };
  walk(doc);
  return ids;
}
