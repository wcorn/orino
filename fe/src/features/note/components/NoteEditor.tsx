import { useQueryClient } from "@tanstack/react-query";
import Placeholder from "@tiptap/extension-placeholder";
import TaskItem from "@tiptap/extension-task-item";
import TaskList from "@tiptap/extension-task-list";
import { EditorContent, type JSONContent, useEditor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { useEffect, useRef, useState } from "react";

import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Input } from "@/components/ui/input";

import type { NoteContent, NoteDetail } from "../api/notes";
import { ChildPage, collectChildPageIds } from "../editor/childPage";
import { useAutoSaveNote } from "../hooks/useAutoSaveNote";
import { useCreateNote, useDeleteNote } from "../hooks/useNoteMutations";
import { noteKeys } from "../queryKeys";
import { EditorToolbar } from "./EditorToolbar";
import { SaveStatusIndicator } from "./SaveStatusIndicator";

interface Props {
  materialId: number;
  note: NoteDetail;
  /** childPage 블록 클릭 시 자식 노트로 이동 */
  onOpenNote: (noteId: number) => void;
}

export function NoteEditor({ materialId, note, onOpenNote }: Props) {
  const queryClient = useQueryClient();
  const { status, savedAt, schedule, flush, retry } = useAutoSaveNote(note.id, {
    onSaved: (patch) => {
      // 제목 저장 시 사이드바 트리 라벨 갱신
      if (patch.title !== undefined) {
        queryClient.invalidateQueries({ queryKey: noteKeys.tree(materialId) });
      }
    },
  });
  const createNote = useCreateNote(materialId);
  const deleteNote = useDeleteNote(materialId);

  const [title, setTitle] = useState(note.title);
  const [pendingDelete, setPendingDelete] = useState<number | null>(null);
  // 본문에 현재 박혀 있는 childPage noteId 집합 (삭제 diff 기준)
  const childIdsRef = useRef<Set<number>>(new Set());

  const editor = useEditor(
    {
      extensions: [
        StarterKit,
        TaskList,
        TaskItem.configure({ nested: true }),
        Placeholder.configure({
          placeholder: "내용을 입력하거나 페이지를 추가하세요...",
        }),
        ChildPage.configure({ onOpen: onOpenNote }),
      ],
      content: note.content as JSONContent,
      editorProps: {
        attributes: {
          class:
            "prose prose-sm dark:prose-invert max-w-none min-h-[40svh] focus:outline-none p-4",
          "aria-label": "노트 본문",
        },
      },
      onUpdate: ({ editor }) => {
        const json = editor.getJSON() as NoteContent;
        schedule({ content: json });
        detectRemovedChildPage(json);
      },
    },
    [note.id],
  );

  // 노트 전환 시 제목/본문/childPage 기준 초기화
  useEffect(() => {
    setTitle(note.title);
    childIdsRef.current = collectChildPageIds(note.content);
    editor?.commands.setContent(note.content as JSONContent);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [note.id, editor]);

  useEffect(() => {
    return () => {
      flush();
    };
  }, [flush]);

  const detectRemovedChildPage = (json: NoteContent) => {
    const current = collectChildPageIds(json);
    const prev = childIdsRef.current;
    const removed = [...prev].filter((id) => !current.has(id));
    childIdsRef.current = current;
    if (removed.length > 0) {
      setPendingDelete(removed[0]);
    }
  };

  const handleTitleChange = (next: string) => {
    setTitle(next);
    schedule({ title: next });
  };

  const handleInsertPage = () => {
    if (createNote.isPending) return;
    createNote.mutate(
      { parentId: note.id, title: "제목 없음" },
      {
        onSuccess: (created) => {
          editor
            ?.chain()
            .focus()
            .insertChildPage({ noteId: created.id, title: created.title })
            .run();
          // 새 childPage 반영
          if (editor) {
            childIdsRef.current = collectChildPageIds(
              editor.getJSON() as NoteContent,
            );
          }
        },
      },
    );
  };

  const confirmDelete = () => {
    if (pendingDelete == null) return;
    deleteNote.mutate(pendingDelete, {
      onSuccess: () => setPendingDelete(null),
      onError: () => setPendingDelete(null),
    });
  };

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-3">
        <Input
          value={title}
          onChange={(e) => handleTitleChange(e.target.value)}
          aria-label="노트 제목"
          placeholder="제목 없음"
          className="border-none px-0 text-lg font-semibold shadow-none focus-visible:ring-0"
        />
        <SaveStatusIndicator
          status={status}
          savedAt={savedAt}
          onRetry={retry}
        />
      </div>

      <div className="border-border bg-card overflow-hidden rounded-md border">
        <EditorToolbar
          editor={editor}
          onInsertPage={handleInsertPage}
          insertPagePending={createNote.isPending}
        />
        <EditorContent editor={editor} />
      </div>

      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => {
          if (!open) setPendingDelete(null);
        }}
        title="하위 페이지를 삭제할까요?"
        description="본문에서 제거한 하위 페이지와 그 안의 모든 내용이 삭제됩니다. 되돌릴 수 없어요."
        confirmLabel="삭제"
        destructive
        onConfirm={confirmDelete}
        pending={deleteNote.isPending}
      />
    </div>
  );
}
