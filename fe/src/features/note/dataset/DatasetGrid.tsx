import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  type ColumnDef,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table";
import { useVirtualizer } from "@tanstack/react-virtual";
import { Plus, Trash2, X } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";

import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field-error";
import { LoadingText } from "@/components/ui/loading-text";
import { cn } from "@/lib/utils";

import {
  addDatasetColumn,
  type DatasetMeta,
  deleteDatasetColumn,
  deleteDatasetRow,
  insertDatasetRow,
  renameDatasetColumn,
  updateDatasetRow,
} from "./api/datasets";
import { useDatasetMeta } from "./hooks/useDatasetMeta";
import { useDatasetRows } from "./hooks/useDatasetRows";
import { datasetKeys } from "./queryKeys";

const ROW_HEIGHT = 36;
const MAX_BODY_HEIGHT = 420;

interface Props {
  datasetId: number;
}

/** 대용량 편집 표. TanStack Table(열/헤더) + Virtual(행 가상화) + 지연 로드 + 셀/행 편집. */
export function DatasetGrid({ datasetId }: Props) {
  const queryClient = useQueryClient();
  const { data: meta, isLoading, isError } = useDatasetMeta(datasetId);
  const { getRow, ensureRange, setRowLocal, reset } = useDatasetRows(datasetId);
  const scrollRef = useRef<HTMLDivElement>(null);
  const [editing, setEditing] = useState<{ row: number; col: number } | null>(
    null,
  );
  const [draft, setDraft] = useState("");
  const [editingCol, setEditingCol] = useState<string | null>(null);
  const [colDraft, setColDraft] = useState("");
  const [pendingColDelete, setPendingColDelete] = useState<string | null>(null);

  const colCount = meta?.columns.length ?? 0;
  const rowCount = meta?.rowCount ?? 0;

  const invalidateMeta = () =>
    queryClient.invalidateQueries({ queryKey: datasetKeys.meta(datasetId) });

  const updateMut = useMutation({
    mutationFn: (v: { index: number; cells: string[] }) =>
      updateDatasetRow(datasetId, v.index, v.cells),
    onError: () => reset(),
  });
  const insertMut = useMutation({
    mutationFn: (cells: string[]) => insertDatasetRow(datasetId, cells),
    onSuccess: () => {
      reset();
      void invalidateMeta();
    },
  });
  const deleteMut = useMutation({
    mutationFn: (index: number) => deleteDatasetRow(datasetId, index),
    onSuccess: () => {
      reset();
      void invalidateMeta();
    },
  });
  const renameColMut = useMutation({
    mutationFn: (v: { key: string; label: string }) =>
      renameDatasetColumn(datasetId, v.key, v.label),
    onSuccess: (next: DatasetMeta) =>
      queryClient.setQueryData(datasetKeys.meta(datasetId), next),
    onError: () => void invalidateMeta(),
  });
  const deleteColMut = useMutation({
    mutationFn: (key: string) => deleteDatasetColumn(datasetId, key),
    onSuccess: (next: DatasetMeta) => {
      queryClient.setQueryData(datasetKeys.meta(datasetId), next);
      // 캐시된 cells는 삭제 전 열 순서 기준이라 그대로 쓰면 값이 밀린다. 다시 받아 온다.
      reset();
    },
    onError: () => void invalidateMeta(),
  });
  const addColMut = useMutation({
    mutationFn: (label: string) => addDatasetColumn(datasetId, label),
    // 행은 다시 받지 않는다. 새 열의 key는 방금 발급돼 어느 행에도 값이 없으므로,
    // 캐시된 짧은 cells를 그대로 두면 렌더가 새 열을 빈 칸으로 그린다(편집 시 패딩됨).
    onSuccess: (next: DatasetMeta) =>
      queryClient.setQueryData(datasetKeys.meta(datasetId), next),
    onError: () => void invalidateMeta(),
  });

  // TanStack Table — 열/헤더 모델만(본문은 가상화로 직접 렌더).
  const columns = useMemo<ColumnDef<string[]>[]>(
    () =>
      (meta?.columns ?? []).map((col, c) => ({
        id: col.key,
        header: col.label,
        accessorFn: (row) => row[c] ?? "",
      })),
    [meta],
  );
  const table = useReactTable({
    data: EMPTY,
    columns,
    getCoreRowModel: getCoreRowModel(),
  });

  const virtualizer = useVirtualizer({
    count: rowCount,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 12,
  });
  const virtualItems = virtualizer.getVirtualItems();
  const firstIndex = virtualItems[0]?.index ?? 0;
  const lastIndex = virtualItems[virtualItems.length - 1]?.index ?? 0;

  useEffect(() => {
    if (rowCount > 0) ensureRange(firstIndex, lastIndex);
  }, [firstIndex, lastIndex, rowCount, ensureRange]);

  if (isLoading) return <LoadingText />;
  if (isError || !meta) {
    return <FieldError>표를 불러오지 못했어요.</FieldError>;
  }

  const gridTemplateColumns = `repeat(${colCount}, minmax(120px, 1fr)) 44px`;

  const startEdit = (row: number, col: number) => {
    const cells = getRow(row);
    if (!cells) return;
    setEditing({ row, col });
    setDraft(cells[col] ?? "");
  };

  const commitEdit = () => {
    if (!editing) return;
    const cells = getRow(editing.row);
    if (!cells) {
      setEditing(null);
      return;
    }
    const next = [...cells];
    while (next.length < colCount) next.push("");
    next[editing.col] = draft;
    setRowLocal(editing.row, next);
    updateMut.mutate({ index: editing.row, cells: next });
    setEditing(null);
  };

  const addRow = () =>
    insertMut.mutate(Array.from({ length: colCount }, () => ""));

  const addColumn = () => addColMut.mutate(`열 ${colCount + 1}`);

  const columnLabel = (key: string) =>
    meta.columns.find((c) => c.key === key)?.label ?? key;

  const startColEdit = (key: string, label: string) => {
    setEditingCol(key);
    setColDraft(label);
  };

  const commitColEdit = () => {
    if (!editingCol) return;
    const key = editingCol;
    const label = colDraft.trim();
    const current = meta.columns.find((c) => c.key === key);
    setEditingCol(null);
    // 빈 이름은 서버가 거부하므로 보내지 않고, 변경 없으면 요청 자체를 생략한다.
    if (!label || label === current?.label) return;
    renameColMut.mutate({ key, label });
  };

  return (
    <div
      className="border-border bg-card my-2 flex flex-col overflow-hidden rounded-md border"
      data-testid="dataset-grid"
    >
      {/* 헤더+본문을 한 스크롤 컨테이너에 두어 스크롤바 폭과 무관하게 열이 정렬되게 한다. */}
      <div
        ref={scrollRef}
        className="overflow-auto"
        style={{ maxHeight: MAX_BODY_HEIGHT }}
      >
        {/* 헤더 (TanStack Table) — sticky로 상단 고정 */}
        <div
          className="bg-muted text-muted-foreground sticky top-0 z-10 grid border-b text-sm font-semibold"
          style={{ gridTemplateColumns }}
        >
          {table.getFlatHeaders().map((header) => (
            <div
              key={header.id}
              className="border-border group flex items-center border-r"
            >
              {editingCol === header.id ? (
                <input
                  autoFocus
                  value={colDraft}
                  onChange={(e) => setColDraft(e.target.value)}
                  onBlur={commitColEdit}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") commitColEdit();
                    if (e.key === "Escape") setEditingCol(null);
                  }}
                  aria-label={`열 이름 ${header.id}`}
                  className="focus-visible:ring-ring h-full w-full bg-transparent px-2 py-1.5 font-semibold outline-none focus-visible:ring-1"
                />
              ) : (
                <>
                  <div
                    className="min-w-0 flex-1 cursor-text truncate px-2 py-1.5"
                    title="더블클릭해 열 이름 변경"
                    onDoubleClick={() =>
                      startColEdit(
                        header.id,
                        meta.columns.find((c) => c.key === header.id)?.label ??
                          "",
                      )
                    }
                  >
                    {flexRender(
                      header.column.columnDef.header,
                      header.getContext(),
                    )}
                  </div>
                  {/* 마지막 한 열은 서버가 거부하므로 버튼도 내보내지 않는다. */}
                  {colCount > 1 && (
                    <button
                      type="button"
                      aria-label={`${columnLabel(header.id)} 열 삭제`}
                      onClick={() => setPendingColDelete(header.id)}
                      className="text-muted-foreground hover:text-destructive mr-1 shrink-0 opacity-0 group-hover:opacity-100 focus-visible:opacity-100"
                    >
                      <X className="size-3.5" />
                    </button>
                  )}
                </>
              )}
            </div>
          ))}
          {/* 행 삭제 버튼 열(44px) 위 자리 — 열 추가 버튼을 둔다. */}
          <button
            type="button"
            aria-label="열 추가"
            title="열 추가"
            onClick={addColumn}
            disabled={addColMut.isPending}
            className="text-muted-foreground hover:text-foreground flex items-center justify-center disabled:opacity-50"
          >
            <Plus className="size-3.5" />
          </button>
        </div>

        {/* 본문 (가상화) */}
        <div
          style={{ height: virtualizer.getTotalSize(), position: "relative" }}
        >
          {virtualItems.map((vi) => {
            const cells = getRow(vi.index);
            return (
              <div
                key={vi.key}
                className="border-border absolute top-0 left-0 grid w-full border-b text-sm"
                style={{
                  height: ROW_HEIGHT,
                  transform: `translateY(${vi.start}px)`,
                  gridTemplateColumns,
                }}
              >
                {Array.from({ length: colCount }, (_, c) => {
                  const isEditing =
                    editing?.row === vi.index && editing.col === c;
                  return (
                    <div
                      key={c}
                      className="border-border truncate border-r"
                      onClick={() => startEdit(vi.index, c)}
                    >
                      {isEditing ? (
                        <input
                          autoFocus
                          value={draft}
                          onChange={(e) => setDraft(e.target.value)}
                          onBlur={commitEdit}
                          onKeyDown={(e) => {
                            if (e.key === "Enter") commitEdit();
                            if (e.key === "Escape") setEditing(null);
                          }}
                          aria-label={`셀 ${vi.index + 1}행 ${c + 1}열`}
                          className="focus-visible:ring-ring h-full w-full bg-transparent px-2 py-1 outline-none focus-visible:ring-1"
                        />
                      ) : (
                        <div
                          className={cn(
                            "h-full cursor-text truncate px-2 py-1.5",
                            cells === undefined && "text-muted-foreground/40",
                          )}
                        >
                          {cells === undefined ? "…" : (cells[c] ?? "")}
                        </div>
                      )}
                    </div>
                  );
                })}
                <button
                  type="button"
                  aria-label={`${vi.index + 1}행 삭제`}
                  onClick={() => deleteMut.mutate(vi.index)}
                  className="text-muted-foreground hover:text-destructive flex items-center justify-center"
                >
                  <Trash2 className="size-3.5" />
                </button>
              </div>
            );
          })}
        </div>
      </div>

      {/* 행 추가 */}
      <div className="border-border border-t p-1">
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={addRow}
          disabled={insertMut.isPending}
        >
          <Plus className="size-4" /> 행 추가
        </Button>
      </div>

      <ConfirmDialog
        open={pendingColDelete !== null}
        onOpenChange={(open) => {
          if (!open) setPendingColDelete(null);
        }}
        title={
          pendingColDelete
            ? `'${columnLabel(pendingColDelete)}' 열을 삭제할까요?`
            : "열을 삭제할까요?"
        }
        description="이 열의 모든 셀 값이 표에서 사라집니다. 되돌릴 수 없어요."
        confirmLabel="삭제"
        destructive
        onConfirm={() => {
          if (pendingColDelete) deleteColMut.mutate(pendingColDelete);
          setPendingColDelete(null);
        }}
        pending={deleteColMut.isPending}
      />
    </div>
  );
}

const EMPTY: string[][] = [];
