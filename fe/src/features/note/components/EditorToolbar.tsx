import type { Editor } from "@tiptap/react";
import {
  Bold,
  Code,
  Code2,
  FilePlus,
  Heading1,
  Heading2,
  Italic,
  List,
  ListOrdered,
  Quote,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface Props {
  editor: Editor | null;
  onInsertPage?: () => void;
  insertPagePending?: boolean;
}

interface ToolbarButton {
  label: string;
  icon: typeof Bold;
  isActive: () => boolean;
  onClick: () => void;
}

export function EditorToolbar({
  editor,
  onInsertPage,
  insertPagePending,
}: Props) {
  if (!editor) return null;

  const buttons: ToolbarButton[] = [
    {
      label: "제목 1",
      icon: Heading1,
      isActive: () => editor.isActive("heading", { level: 1 }),
      onClick: () => editor.chain().focus().toggleHeading({ level: 1 }).run(),
    },
    {
      label: "제목 2",
      icon: Heading2,
      isActive: () => editor.isActive("heading", { level: 2 }),
      onClick: () => editor.chain().focus().toggleHeading({ level: 2 }).run(),
    },
    {
      label: "굵게",
      icon: Bold,
      isActive: () => editor.isActive("bold"),
      onClick: () => editor.chain().focus().toggleBold().run(),
    },
    {
      label: "기울임",
      icon: Italic,
      isActive: () => editor.isActive("italic"),
      onClick: () => editor.chain().focus().toggleItalic().run(),
    },
    {
      label: "글머리 기호",
      icon: List,
      isActive: () => editor.isActive("bulletList"),
      onClick: () => editor.chain().focus().toggleBulletList().run(),
    },
    {
      label: "번호 매기기",
      icon: ListOrdered,
      isActive: () => editor.isActive("orderedList"),
      onClick: () => editor.chain().focus().toggleOrderedList().run(),
    },
    {
      label: "인용",
      icon: Quote,
      isActive: () => editor.isActive("blockquote"),
      onClick: () => editor.chain().focus().toggleBlockquote().run(),
    },
    {
      label: "인라인 코드",
      icon: Code,
      isActive: () => editor.isActive("code"),
      onClick: () => editor.chain().focus().toggleCode().run(),
    },
    {
      label: "코드 블록",
      icon: Code2,
      isActive: () => editor.isActive("codeBlock"),
      onClick: () => editor.chain().focus().toggleCodeBlock().run(),
    },
  ];

  return (
    <div
      role="toolbar"
      aria-label="에디터 도구"
      className="border-border bg-background flex flex-wrap gap-0.5 rounded-t-md border-b p-1"
    >
      {buttons.map((btn) => {
        const Icon = btn.icon;
        return (
          <Button
            key={btn.label}
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label={btn.label}
            aria-pressed={btn.isActive()}
            className={cn(btn.isActive() && "bg-muted")}
            onClick={btn.onClick}
          >
            <Icon className="size-4" />
          </Button>
        );
      })}
      {onInsertPage && (
        <>
          <span className="bg-border mx-1 w-px self-stretch" aria-hidden />
          <Button
            type="button"
            variant="ghost"
            size="sm"
            aria-label="하위 페이지 추가"
            disabled={insertPagePending}
            onClick={onInsertPage}
          >
            <FilePlus className="size-4" /> 페이지
          </Button>
        </>
      )}
    </div>
  );
}
