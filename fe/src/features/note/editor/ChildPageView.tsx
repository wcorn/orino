import { type NodeViewProps, NodeViewWrapper } from "@tiptap/react";
import { FileText } from "lucide-react";

import { type LiveTreeNode, useChildPageContext } from "./childPageContext";

/** 트리에서 id에 해당하는 노드의 현재 제목을 찾는다. */
function findTitle(
  nodes: LiveTreeNode[] | undefined,
  id: number,
): string | undefined {
  if (!nodes) return undefined;
  for (const node of nodes) {
    if (node.id === id) return node.title;
    const found = findTitle(node.children, id);
    if (found !== undefined) return found;
  }
  return undefined;
}

/**
 * 본문 안의 하위 페이지 줄. 한 줄을 차지하되 박스 없이 아이콘 + 제목만 보인다.
 * 클릭 시 컨텍스트의 onOpen(id) 호출 → 자식 노트/메모로 라우팅.
 * 제목은 트리(라이브)에서 조회하고, 없으면 attrs.title(삽입 시점 캐시)로 폴백한다.
 */
export function ChildPageView({ node }: NodeViewProps) {
  const noteId = node.attrs.noteId as number | null;
  const cachedTitle = (node.attrs.title as string) || "제목 없음";
  const { onOpen, tree } = useChildPageContext();

  const liveTitle = noteId != null ? findTitle(tree, noteId) : undefined;
  const title = liveTitle ?? cachedTitle;

  const handleOpen = () => {
    if (noteId == null) return;
    onOpen(noteId);
  };

  return (
    <NodeViewWrapper
      as="div"
      data-child-page={noteId ?? ""}
      className="my-0.5"
      contentEditable={false}
    >
      <button
        type="button"
        onClick={handleOpen}
        aria-label={`하위 페이지 ${title} 열기`}
        className="group/childpage flex w-full items-center gap-1.5 py-0.5 text-left text-sm"
      >
        <FileText className="text-muted-foreground size-4 shrink-0" />
        <span className="truncate underline-offset-2 group-hover/childpage:underline">
          {title}
        </span>
      </button>
    </NodeViewWrapper>
  );
}
