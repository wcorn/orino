import { ArrowLeft, Plus } from "lucide-react";
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { ConfirmDialog } from "@/components/ConfirmDialog";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { FieldError } from "@/components/ui/field-error";
import { LoadingText } from "@/components/ui/loading-text";

import type { NoteTreeNode } from "../api/notes";
import { useNoteDetail } from "../hooks/useNoteDetail";
import {
  useCreateNote,
  useDeleteNote,
  useMoveNote,
} from "../hooks/useNoteMutations";
import { useNoteTree } from "../hooks/useNoteTree";
import { computeMove, type DropPosition } from "../treeMove";
import { NoteEditor } from "./NoteEditor";
import { NoteTreeSidebar } from "./NoteTreeSidebar";

function collectSubtreeIds(node: NoteTreeNode): number[] {
  return [node.id, ...node.children.flatMap(collectSubtreeIds)];
}

function countDescendants(node: NoteTreeNode): number {
  return node.children.reduce(
    (sum, child) => sum + 1 + countDescendants(child),
    0,
  );
}

/** 독립 노트 본체(자료 종속 없음). 좌측 트리 사이드바 + 우측 Tiptap 에디터 2-pane. */
export function NoteWorkspace() {
  const [searchParams, setSearchParams] = useSearchParams();
  const noteParam = searchParams.get("note");
  const activeNoteId = noteParam ? Number(noteParam) : null;

  // materialId 미지정 → 독립 노트 스코프
  const treeQuery = useNoteTree();
  const detailQuery = useNoteDetail(activeNoteId);
  const createNote = useCreateNote();
  const deleteNote = useDeleteNote();
  const moveNote = useMoveNote();
  const [pendingDelete, setPendingDelete] = useState<NoteTreeNode | null>(null);

  const setActiveNote = (noteId: number | null) => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (noteId == null) {
          next.delete("note");
        } else {
          next.set("note", String(noteId));
        }
        return next;
      },
      { replace: true },
    );
  };

  const tree = treeQuery.data ?? [];

  // 노트가 있는데 아무것도 선택 안 됐으면 첫 루트 자동 선택
  useEffect(() => {
    if (activeNoteId == null && tree.length > 0) {
      setActiveNote(tree[0].id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeNoteId, tree]);

  const handleAddRoot = () => {
    if (createNote.isPending) return;
    createNote.mutate(
      { parentId: null, title: "제목 없음" },
      { onSuccess: (created) => setActiveNote(created.id) },
    );
  };

  const handleAddChild = (parentId: number) => {
    if (createNote.isPending) return;
    createNote.mutate(
      { parentId, title: "제목 없음" },
      { onSuccess: (created) => setActiveNote(created.id) },
    );
  };

  const handleMove = (
    dragId: number,
    targetId: number,
    position: DropPosition,
  ) => {
    if (moveNote.isPending) return;
    const plan = computeMove(tree, dragId, targetId, position);
    if (!plan) return;
    moveNote.mutate({ plan, dragId });
  };

  const handleConfirmDelete = () => {
    if (!pendingDelete || deleteNote.isPending) return;
    const removedIds = new Set(collectSubtreeIds(pendingDelete));
    deleteNote.mutate(pendingDelete.id, {
      onSuccess: () => {
        // 삭제된 서브트리에 활성 노트가 포함됐으면 선택 해제
        // (남은 루트가 있으면 useEffect가 자동 선택)
        if (activeNoteId != null && removedIds.has(activeNoteId)) {
          setActiveNote(null);
        }
        setPendingDelete(null);
      },
      onError: () => setPendingDelete(null),
    });
  };

  const showEditorOnMobile = activeNoteId != null;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="노트" />

      {treeQuery.isLoading ? (
        <LoadingText />
      ) : treeQuery.isError ? (
        <FieldError>노트를 불러오지 못했어요.</FieldError>
      ) : tree.length === 0 && activeNoteId == null ? (
        <EmptyState>
          <p className="text-muted-foreground text-sm">아직 노트가 없습니다.</p>
          <Button onClick={handleAddRoot} disabled={createNote.isPending}>
            <Plus className="size-4" /> 첫 노트 만들기
          </Button>
        </EmptyState>
      ) : (
        <div className="flex flex-col gap-4 md:flex-row md:items-start md:gap-6">
          {/* 모바일: 노트 선택 시 트리 숨김(드릴다운) */}
          <div className={showEditorOnMobile ? "hidden md:block" : "block"}>
            <NoteTreeSidebar
              tree={tree}
              activeNoteId={activeNoteId}
              onSelect={setActiveNote}
              onAddRoot={handleAddRoot}
              onAddChild={handleAddChild}
              onRequestDelete={setPendingDelete}
              onMove={handleMove}
              addPending={createNote.isPending}
            />
          </div>

          <div
            className={
              "min-w-0 flex-1 " +
              (showEditorOnMobile ? "block" : "hidden md:block")
            }
          >
            <Button
              variant="ghost"
              size="sm"
              className="mb-2 md:hidden"
              onClick={() => setActiveNote(null)}
            >
              <ArrowLeft className="size-4" /> 노트 목록
            </Button>

            {activeNoteId == null ? (
              <p className="text-muted-foreground hidden text-sm md:block">
                왼쪽에서 노트를 선택하거나 새로 만드세요.
              </p>
            ) : detailQuery.isLoading ? (
              <LoadingText />
            ) : detailQuery.isError || !detailQuery.data ? (
              <FieldError>노트를 불러오지 못했어요.</FieldError>
            ) : (
              <NoteEditor
                key={detailQuery.data.id}
                note={detailQuery.data}
                onOpenNote={setActiveNote}
              />
            )}
          </div>
        </div>
      )}

      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => {
          if (!open) setPendingDelete(null);
        }}
        title="노트를 삭제할까요?"
        description={
          pendingDelete && countDescendants(pendingDelete) > 0
            ? `하위 노트 ${countDescendants(pendingDelete)}개도 함께 삭제됩니다. 되돌릴 수 없어요.`
            : "이 노트가 삭제됩니다. 되돌릴 수 없어요."
        }
        confirmLabel="삭제"
        destructive
        onConfirm={handleConfirmDelete}
        pending={deleteNote.isPending}
      />
    </div>
  );
}
