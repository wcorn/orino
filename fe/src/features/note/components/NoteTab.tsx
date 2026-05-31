import { ArrowLeft, Plus } from "lucide-react";
import { useEffect } from "react";
import { useSearchParams } from "react-router-dom";

import { Button } from "@/components/ui/button";

import { useNoteDetail } from "../hooks/useNoteDetail";
import { useCreateNote } from "../hooks/useNoteMutations";
import { useNoteTree } from "../hooks/useNoteTree";
import { NoteEditor } from "./NoteEditor";
import { NoteTreeSidebar } from "./NoteTreeSidebar";

interface Props {
  materialId: number;
}

export function NoteTab({ materialId }: Props) {
  const [searchParams, setSearchParams] = useSearchParams();
  const noteParam = searchParams.get("note");
  const activeNoteId = noteParam ? Number(noteParam) : null;

  const treeQuery = useNoteTree(materialId);
  const detailQuery = useNoteDetail(activeNoteId);
  const createNote = useCreateNote(materialId);

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

  if (treeQuery.isLoading) {
    return <p className="text-muted-foreground text-sm">불러오는 중...</p>;
  }
  if (treeQuery.isError) {
    return (
      <p className="text-destructive text-sm">노트를 불러오지 못했어요.</p>
    );
  }

  // 빈 상태: 노트 0개 + 선택된 노트도 없음 (생성 직후 refetch 전이면 에디터로 진행)
  if (tree.length === 0 && activeNoteId == null) {
    return (
      <div className="flex min-h-[40svh] flex-col items-center justify-center gap-4 text-center">
        <p className="text-muted-foreground text-sm">아직 노트가 없습니다.</p>
        <Button onClick={handleAddRoot} disabled={createNote.isPending}>
          <Plus className="size-4" /> 첫 노트 만들기
        </Button>
      </div>
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
          addRootPending={createNote.isPending}
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
          <p className="text-muted-foreground text-sm">불러오는 중...</p>
        ) : detailQuery.isError || !detailQuery.data ? (
          <p className="text-destructive text-sm">노트를 불러오지 못했어요.</p>
        ) : (
          <NoteEditor
            key={detailQuery.data.id}
            materialId={materialId}
            note={detailQuery.data}
            onOpenNote={setActiveNote}
          />
        )}
      </div>
    </div>
  );
}
