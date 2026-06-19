import { useQueryClient } from "@tanstack/react-query";
import { DragHandle } from "@tiptap/extension-drag-handle-react";
import Image from "@tiptap/extension-image";
import Placeholder from "@tiptap/extension-placeholder";
import { TableKit } from "@tiptap/extension-table";
import TaskItem from "@tiptap/extension-task-item";
import TaskList from "@tiptap/extension-task-list";
import { EditorContent, type JSONContent, useEditor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { GripVertical } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Input } from "@/components/ui/input";

import type { NoteContent, NoteDetail } from "../api/notes";
import { ChildPage, collectChildPageIds } from "../editor/childPage";
import { extractImageFiles, uploadAndInsertImage } from "../editor/imageUpload";
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
      // 저장된 값을 detail 캐시에 반영해 노트 재진입 시 최신 내용이 보이게 한다.
      // (자동저장 응답엔 content가 없으므로 보낸 patch를 직접 머지)
      queryClient.setQueryData<NoteDetail>(noteKeys.detail(note.id), (old) =>
        old ? { ...old, ...patch } : old,
      );
      // 제목 저장 시 사이드바 트리 라벨도 갱신
      if (patch.title !== undefined) {
        queryClient.invalidateQueries({ queryKey: noteKeys.tree(materialId) });
      }
    },
  });
  const createNote = useCreateNote(materialId);
  const deleteNote = useDeleteNote(materialId);

  const [title, setTitle] = useState(note.title);
  const [pendingDelete, setPendingDelete] = useState<number | null>(null);
  // 본문에 현재 박혀 있는 childPage noteId 집합 (삭제 diff 기준).
  // NoteTab이 key={note.id}로 리마운트하므로 마운트 시점 content 기준으로 1회 초기화한다.
  const childIdsRef = useRef<Set<number>>(collectChildPageIds(note.content));
  // editorProps 핸들러(handlePaste/handleDrop)는 생성 시점 클로저라 editor를
  // 직접 못 잡으므로 ref로 우회한다.
  const editorRef = useRef<ReturnType<typeof useEditor>>(null);

  const editor = useEditor(
    {
      extensions: [
        StarterKit,
        TaskList,
        TaskItem.configure({ nested: true }),
        TableKit.configure({ table: { resizable: true } }),
        Image.configure({ inline: false }),
        Placeholder.configure({
          placeholder: "내용을 입력하거나 페이지를 추가하세요...",
        }),
        ChildPage.configure({ onOpen: onOpenNote, materialId }),
      ],
      content: note.content as JSONContent,
      editorProps: {
        attributes: {
          class:
            "prose prose-sm dark:prose-invert max-w-none min-h-[40svh] focus:outline-none py-4 pr-4 pl-10",
          "aria-label": "노트 본문",
        },
        // 클립보드 붙여넣기로 이미지 업로드
        handlePaste: (_view, event) => {
          const files = extractImageFiles(event.clipboardData);
          if (files.length === 0) return false;
          event.preventDefault();
          files.forEach((file) => {
            if (editorRef.current)
              void uploadAndInsertImage(editorRef.current, file);
          });
          return true;
        },
        // 드래그앤드롭으로 이미지 업로드
        handleDrop: (_view, event) => {
          const files = extractImageFiles(
            (event as DragEvent).dataTransfer ?? null,
          );
          if (files.length === 0) return false;
          event.preventDefault();
          files.forEach((file) => {
            if (editorRef.current)
              void uploadAndInsertImage(editorRef.current, file);
          });
          return true;
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
  editorRef.current = editor;

  // 노트 전환은 NoteTab의 key={note.id} 리마운트로 처리되므로
  // setContent를 수동 호출하지 않는다. (editor 재생성마다 setContent가
  // 캐시의 옛 content로 onUpdate를 발화해 방금 쓴 내용을 빈 doc으로
  // 덮어쓰던 버그를 제거)

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
    // min-w-0: 부모(md:flex-row) flex 자식의 기본 min-width:auto 때문에
    // 넓은 표가 들어오면 에디터가 부모를 넘어 페이지 전체가 늘어난다.
    // min-w-0으로 콘텐츠가 부모 너비를 못 넘게 하고, 표는 tableWrapper에서만 스크롤.
    <div className="flex min-w-0 flex-col gap-3">
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
        {editor && (
          // nested: 불릿/태스크 리스트 항목 등 중첩 블록도 개별 드래그 타깃이 되게 한다.
          // (기본 false면 리스트 전체를 한 블록으로만 잡음)
          <DragHandle editor={editor} nested className="z-10">
            <button
              type="button"
              aria-label="블록 이동"
              className="text-muted-foreground hover:bg-muted flex size-6 cursor-grab items-center justify-center rounded transition-colors active:cursor-grabbing"
            >
              <GripVertical className="size-4" />
            </button>
          </DragHandle>
        )}
        <EditorContent editor={editor} className="relative" />
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
