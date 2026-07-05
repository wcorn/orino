import { ArrowLeft, Plus } from "lucide-react";
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { ConfirmDialog } from "@/components/ConfirmDialog";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { FieldError } from "@/components/ui/field-error";
import { LoadingText } from "@/components/ui/loading-text";

import type { MemoTreeNode } from "../api/memos";
import { useMemoDetail } from "../hooks/useMemoDetail";
import { useCreateMemo, useDeleteMemo } from "../hooks/useMemoMutations";
import { useMemoTree } from "../hooks/useMemoTree";
import { MemoEditor } from "./MemoEditor";
import { MemoTreeSidebar } from "./MemoTreeSidebar";

function collectSubtreeIds(node: MemoTreeNode): number[] {
  return [node.id, ...node.children.flatMap(collectSubtreeIds)];
}

function countDescendants(node: MemoTreeNode): number {
  return node.children.reduce(
    (sum, child) => sum + 1 + countDescendants(child),
    0,
  );
}

/** 독립 메모장 본체. 좌측 트리 사이드바 + 우측 Tiptap 에디터 2-pane. */
export function MemoWorkspace() {
  const [searchParams, setSearchParams] = useSearchParams();
  const memoParam = searchParams.get("memo");
  const activeMemoId = memoParam ? Number(memoParam) : null;

  const treeQuery = useMemoTree();
  const detailQuery = useMemoDetail(activeMemoId);
  const createMemo = useCreateMemo();
  const deleteMemo = useDeleteMemo();
  const [pendingDelete, setPendingDelete] = useState<MemoTreeNode | null>(null);

  const setActiveMemo = (memoId: number | null) => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (memoId == null) {
          next.delete("memo");
        } else {
          next.set("memo", String(memoId));
        }
        return next;
      },
      { replace: true },
    );
  };

  const tree = treeQuery.data ?? [];

  // 메모가 있는데 아무것도 선택 안 됐으면 첫 루트 자동 선택
  useEffect(() => {
    if (activeMemoId == null && tree.length > 0) {
      setActiveMemo(tree[0].id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeMemoId, tree]);

  const handleAddRoot = () => {
    if (createMemo.isPending) return;
    createMemo.mutate(
      { parentId: null, title: "제목 없음" },
      { onSuccess: (created) => setActiveMemo(created.id) },
    );
  };

  const handleAddChild = (parentId: number) => {
    if (createMemo.isPending) return;
    createMemo.mutate(
      { parentId, title: "제목 없음" },
      { onSuccess: (created) => setActiveMemo(created.id) },
    );
  };

  const handleConfirmDelete = () => {
    if (!pendingDelete || deleteMemo.isPending) return;
    const removedIds = new Set(collectSubtreeIds(pendingDelete));
    deleteMemo.mutate(pendingDelete.id, {
      onSuccess: () => {
        // 삭제된 서브트리에 활성 메모가 포함됐으면 선택 해제
        // (남은 루트가 있으면 useEffect가 자동 선택)
        if (activeMemoId != null && removedIds.has(activeMemoId)) {
          setActiveMemo(null);
        }
        setPendingDelete(null);
      },
      onError: () => setPendingDelete(null),
    });
  };

  const showEditorOnMobile = activeMemoId != null;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="메모" />

      {treeQuery.isLoading ? (
        <LoadingText />
      ) : treeQuery.isError ? (
        <FieldError>메모를 불러오지 못했어요.</FieldError>
      ) : tree.length === 0 && activeMemoId == null ? (
        <EmptyState>
          <p className="text-muted-foreground text-sm">아직 메모가 없습니다.</p>
          <Button onClick={handleAddRoot} disabled={createMemo.isPending}>
            <Plus className="size-4" /> 첫 메모 만들기
          </Button>
        </EmptyState>
      ) : (
        <div className="flex flex-col gap-4 md:flex-row md:items-start md:gap-6">
          {/* 모바일: 메모 선택 시 트리 숨김(드릴다운) */}
          <div className={showEditorOnMobile ? "hidden md:block" : "block"}>
            <MemoTreeSidebar
              tree={tree}
              activeMemoId={activeMemoId}
              onSelect={setActiveMemo}
              onAddRoot={handleAddRoot}
              onAddChild={handleAddChild}
              onRequestDelete={setPendingDelete}
              addPending={createMemo.isPending}
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
              onClick={() => setActiveMemo(null)}
            >
              <ArrowLeft className="size-4" /> 메모 목록
            </Button>

            {activeMemoId == null ? (
              <p className="text-muted-foreground hidden text-sm md:block">
                왼쪽에서 메모를 선택하거나 새로 만드세요.
              </p>
            ) : detailQuery.isLoading ? (
              <LoadingText />
            ) : detailQuery.isError || !detailQuery.data ? (
              <FieldError>메모를 불러오지 못했어요.</FieldError>
            ) : (
              <MemoEditor key={detailQuery.data.id} memo={detailQuery.data} />
            )}
          </div>
        </div>
      )}

      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => {
          if (!open) setPendingDelete(null);
        }}
        title="메모를 삭제할까요?"
        description={
          pendingDelete && countDescendants(pendingDelete) > 0
            ? `하위 메모 ${countDescendants(pendingDelete)}개도 함께 삭제됩니다. 되돌릴 수 없어요.`
            : "이 메모가 삭제됩니다. 되돌릴 수 없어요."
        }
        confirmLabel="삭제"
        destructive
        onConfirm={handleConfirmDelete}
        pending={deleteMemo.isPending}
      />
    </div>
  );
}
