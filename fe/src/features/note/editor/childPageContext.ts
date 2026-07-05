import { createContext, useContext } from "react";

/** 라이브 제목 조회에 필요한 최소 트리 노드(노트·메모 공용). */
export interface LiveTreeNode {
  id: number;
  title: string;
  children: LiveTreeNode[];
}

export interface ChildPageContextValue {
  /** childPage 블록 클릭 → 해당 노트/메모로 이동. */
  onOpen: (id: number) => void;
  /** 라이브 제목 조회용 트리(attrs.title은 삽입 시점 캐시라 stale할 수 있음). */
  tree: LiveTreeNode[] | undefined;
}

/**
 * childPage NodeView에 onOpen·트리를 주입하는 컨텍스트.
 * NoteEditor·MemoEditor가 각자의 훅(useNoteTree/useMemoTree)으로 값을 채워
 * EditorContent를 이 Provider로 감싼다. Tiptap React NodeView는 상위 컨텍스트를
 * 상속하므로 ChildPageView에서 읽을 수 있다.
 */
export const ChildPageContext = createContext<ChildPageContextValue>({
  onOpen: () => {},
  tree: undefined,
});

export function useChildPageContext(): ChildPageContextValue {
  return useContext(ChildPageContext);
}
