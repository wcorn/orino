import Placeholder from "@tiptap/extension-placeholder";
import TaskItem from "@tiptap/extension-task-item";
import TaskList from "@tiptap/extension-task-list";
import { EditorContent, type JSONContent, useEditor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { useEffect } from "react";

import type { NoteContent } from "../api/notes";
import { useAutoSaveNote } from "../hooks/useAutoSaveNote";
import { EditorToolbar } from "./EditorToolbar";
import { SaveStatusIndicator } from "./SaveStatusIndicator";

interface Props {
  materialId: number;
  initialContent: NoteContent;
}

export function NoteEditor({ materialId, initialContent }: Props) {
  const { status, savedAt, schedule, flush, retry } =
    useAutoSaveNote(materialId);

  const editor = useEditor({
    extensions: [
      StarterKit,
      TaskList,
      TaskItem.configure({ nested: true }),
      Placeholder.configure({
        placeholder: "학습한 내용을 정리해보세요...",
      }),
    ],
    content: initialContent as JSONContent,
    editorProps: {
      attributes: {
        class:
          "prose prose-sm dark:prose-invert max-w-none min-h-[40svh] focus:outline-none p-4",
        "aria-label": "노트 본문",
      },
    },
    onUpdate: ({ editor }) => {
      schedule(editor.getJSON() as NoteContent);
    },
  });

  useEffect(() => {
    return () => {
      flush();
    };
  }, [flush]);

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-end">
        <SaveStatusIndicator
          status={status}
          savedAt={savedAt}
          onRetry={retry}
        />
      </div>
      <div className="border-border bg-card overflow-hidden rounded-md border">
        <EditorToolbar editor={editor} />
        <EditorContent editor={editor} />
      </div>
    </div>
  );
}
