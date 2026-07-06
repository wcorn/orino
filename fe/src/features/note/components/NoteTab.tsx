import { ArrowLeft, Plus } from "lucide-react";
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { ConfirmDialog } from "@/components/ConfirmDialog";
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

interface Props {
  materialId: number;
}

function collectSubtreeIds(node: NoteTreeNode): number[] {
  return [node.id, ...node.children.flatMap(collectSubtreeIds)];
}

function countDescendants(node: NoteTreeNode): number {
  return node.children.reduce(
    (sum, child) => sum + 1 + countDescendants(child),
    0,
  );
}

export function NoteTab({ materialId }: Props) {
  const [searchParams, setSearchParams] = useSearchParams();
  const noteParam = searchParams.get("note");
  const activeNoteId = noteParam ? Number(noteParam) : null;
  // 노트 탭이 활성일 때만 자동선택을 수행한다. base-ui Tabs는 패널이 숨겨진
  // 직후에도 한 렌더 동안 마운트를 유지하는데, 그 사이 자동선택이 tab=note로
  // URL을 되돌려 카드 탭 전환을 막던 버그가 있었다.
  const isNoteTab = (searchParams.get("tab") ?? "note") === "note";

  const treeQuery = useNoteTree(materialId);
  const detailQuery = useNoteDetail(activeNoteId);
  const createNote = useCreateNote(materialId);
  const deleteNote = useDeleteNote(materialId);
  const moveNote = useMoveNote(materialId);
  const [pendingDelete, setPendingDelete] = useState<NoteTreeNode | null>(null);

  const setActiveNote = (noteId: number | null) => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.set("tab", "note");
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

  // 노트가 있는데 아무것도 선택 안 됐으면 첫 루트 자동 선택 (노트 탭 한정)
  useEffect(() => {
    if (isNoteTab && activeNoteId == null && tree.length > 0) {
      setActiveNote(tree[0].id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isNoteTab, activeNoteId, tree]);

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

  if (treeQuery.isLoading) {
    return <LoadingText />;
  }
  if (treeQuery.isError) {
    return <FieldError>노트를 불러오지 못했어요.</FieldError>;
  }

  // 빈 상태: 노트 0개 + 선택된 노트도 없음 (생성 직후 refetch 전이면 에디터로 진행)
  if (tree.length === 0 && activeNoteId == null) {
    return (
      <EmptyState>
        <p className="text-muted-foreground text-sm">아직 노트가 없습니다.</p>
        <Button onClick={handleAddRoot} disabled={createNote.isPending}>
          <Plus className="size-4" /> 첫 노트 만들기
        </Button>
      </EmptyState>
    );
  }

  const showEditorOnMobile = activeNoteId != null;

  return (
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
          "min-w-0 flex-1 " + (showEditorOnMobile ? "block" : "hidden md:block")
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
            materialId={materialId}
            note={detailQuery.data}
            onOpenNote={setActiveNote}
          />
        )}
      </div>

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
