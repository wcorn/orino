import { type NodeViewProps, NodeViewWrapper } from "@tiptap/react";
import { FileText } from "lucide-react";

import type { ChildPageOptions } from "./childPage";

/**
 * 본문 안에 박히는 하위 페이지 블록.
 * 클릭 시 ChildPage 노드 옵션의 onOpen(noteId) 호출 → 자식 노트로 라우팅.
 */
export function ChildPageView({ node, extension }: NodeViewProps) {
  const noteId = node.attrs.noteId as number | null;
  const title = (node.attrs.title as string) || "제목 없음";
  const options = extension.options as ChildPageOptions;

  const handleOpen = () => {
    if (noteId == null) return;
    options.onOpen(noteId);
  };

  return (
    <NodeViewWrapper
      as="div"
      data-child-page={noteId ?? ""}
      className="my-1"
      contentEditable={false}
    >
      <button
        type="button"
        onClick={handleOpen}
        aria-label={`하위 페이지 ${title} 열기`}
        className="hover:bg-muted flex w-full items-center gap-2 rounded-md border px-3 py-2 text-left text-sm transition-colors"
      >
        <FileText className="text-muted-foreground size-4 shrink-0" />
        <span className="truncate underline-offset-2 hover:underline">
          {title}
        </span>
      </button>
    </NodeViewWrapper>
  );
}
