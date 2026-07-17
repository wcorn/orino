import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  type ColumnDef,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table";
import { useVirtualizer } from "@tanstack/react-virtual";
import { FunctionSquare, Palette, Plus, Trash2, X } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";

import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field-error";
import { LoadingText } from "@/components/ui/loading-text";
import { cn } from "@/lib/utils";
import { toast } from "@/shared/lib/toast";

import {
  addDatasetColumn,
  CELL_BG_TOKENS,
  type CellBgToken,
  type CellStyle,
  type DatasetMeta,
  deleteDatasetColumn,
  deleteDatasetRow,
  insertDatasetRow,
  MAX_COLUMN_WIDTH,
  MIN_COLUMN_WIDTH,
  renameDatasetColumn,
  reorderDatasetColumns,
  resetDatasetColumnWidth,
  resizeDatasetColumn,
  setCellStyle,
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
  const {
    getRow,
    getFormulas,
    getStyles,
    ensureRange,
    setRowLocal,
    setFormulasLocal,
    setStylesLocal,
    reset,
  } = useDatasetRows(datasetId);
  const scrollRef = useRef<HTMLDivElement>(null);
  const [editing, setEditing] = useState<{ row: number; col: number } | null>(
    null,
  );
  const [draft, setDraft] = useState("");
  const [editingCol, setEditingCol] = useState<string | null>(null);
  const [colDraft, setColDraft] = useState("");
  const [pendingColDelete, setPendingColDelete] = useState<string | null>(null);
  const [dragKey, setDragKey] = useState<string | null>(null);
  // 리사이즈 중인 열과 진행 중 너비. 드래그하는 동안엔 여기 값으로 그리고,
  // 저장은 놓을 때 한 번만 한다(mousemove마다 PATCH를 보내지 않는다).
  const [resizing, setResizing] = useState<{
    key: string;
    startX: number;
    startWidth: number;
    width: number;
  } | null>(null);
  // D6 — 수식은 평소 숨기고 선택 시에만 보여준다. 이 토글은 상시 표시.
  const [showFormulas, setShowFormulas] = useState(false);
  // 색 팔레트를 연 셀(행 index·열 index). null이면 닫힘.
  const [palette, setPalette] = useState<{ row: number; col: number } | null>(
    null,
  );

  const colCount = meta?.columns.length ?? 0;
  const rowCount = meta?.rowCount ?? 0;

  const invalidateMeta = () =>
    queryClient.invalidateQueries({ queryKey: datasetKeys.meta(datasetId) });

  const updateMut = useMutation({
    mutationFn: (v: { index: number; cells: string[] }) =>
      updateDatasetRow(datasetId, v.index, v.cells),
    // 서버가 계산한 값과 수식으로 맞춘다 — '=1+2'를 치면 화면엔 3이 와야 하고,
    // 전파가 같은 행의 다른 셀을 고쳤을 수도 있다.
    onSuccess: (row) => {
      setRowLocal(row.rowIndex, row.cells);
      setFormulasLocal(row.rowIndex, row.formulas ?? {});
    },
    onError: (e) => {
      // 수식 문법 오류·순환 참조는 서버가 무엇이 틀렸는지 알려준다. 그대로 보여준다.
      const message = serverMessage(e);
      if (message) toast(message, "error");
      reset();
    },
  });
  const styleMut = useMutation({
    mutationFn: (v: { row: number; colKey: string; style: CellStyle }) =>
      setCellStyle(datasetId, v.row, v.colKey, v.style),
    // 서식만 바뀌므로 값·수식 캐시는 건드리지 않고 styles만 갱신한다.
    onSuccess: (row) => setStylesLocal(row.rowIndex, row.styles ?? {}),
    onError: (e) => {
      const message = serverMessage(e);
      if (message) toast(message, "error");
    },
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
  const reorderColMut = useMutation({
    mutationFn: (keys: string[]) => reorderDatasetColumns(datasetId, keys),
    onSuccess: (next: DatasetMeta) => {
      queryClient.setQueryData(datasetKeys.meta(datasetId), next);
      // 캐시된 cells는 이전 열 순서 기준이라 그대로 쓰면 값이 어긋난다.
      reset();
    },
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
  const resizeColMut = useMutation({
    mutationFn: (v: { key: string; width: number }) =>
      resizeDatasetColumn(datasetId, v.key, v.width),
    // 너비는 열 단위 속성이라 행 캐시를 버릴 이유가 없다(reorder/delete와 달리 값이 안 밀린다).
    onSuccess: (next: DatasetMeta) =>
      queryClient.setQueryData(datasetKeys.meta(datasetId), next),
    onError: () => void invalidateMeta(),
  });
  const resetColWidthMut = useMutation({
    mutationFn: (key: string) => resetDatasetColumnWidth(datasetId, key),
    onSuccess: (next: DatasetMeta) =>
      queryClient.setQueryData(datasetKeys.meta(datasetId), next),
    onError: () => void invalidateMeta(),
  });
  const addColMut = useMutation({
    mutationFn: () => addDatasetColumn(datasetId),
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

  // 너비를 지정한 열은 고정 px, 안 한 열은 기존대로 남는 폭을 나눠 갖는다.
  // 드래그 중인 열은 아직 저장 전이므로 진행 중 너비로 그린다.
  const gridTemplateColumns =
    meta.columns
      .map((col) => {
        const width = resizing?.key === col.key ? resizing.width : col.width;
        return width != null ? `${width}px` : "minmax(120px, 1fr)";
      })
      .join(" ") + " 44px";

  const clampWidth = (width: number) =>
    Math.round(Math.min(MAX_COLUMN_WIDTH, Math.max(MIN_COLUMN_WIDTH, width)));

  const startResize = (key: string, event: React.PointerEvent) => {
    // 헤더 셀의 실제 렌더 폭에서 시작한다. 너비 미지정(1fr) 열도 이 값으로 잡히므로
    // 첫 드래그가 기본 폭에서 자연스럽게 이어진다.
    const headerCell = event.currentTarget.parentElement;
    if (!headerCell) return;
    const startWidth = headerCell.getBoundingClientRect().width;
    event.preventDefault();
    event.stopPropagation();
    // 포인터를 잡아 두면 핸들 밖으로 벗어나도 move/up이 계속 온다.
    // (jsdom엔 없어서 옵셔널 호출 — 없으면 캡처만 못 할 뿐 동작은 같다)
    event.currentTarget.setPointerCapture?.(event.pointerId);
    setResizing({
      key,
      startX: event.clientX,
      startWidth,
      width: clampWidth(startWidth),
    });
  };

  const moveResize = (event: React.PointerEvent) => {
    if (!resizing) return;
    setResizing({
      ...resizing,
      width: clampWidth(
        resizing.startWidth + (event.clientX - resizing.startX),
      ),
    });
  };

  const endResize = () => {
    if (!resizing) return;
    const { key, width, startWidth } = resizing;
    setResizing(null);
    // 폭이 그대로면 저장하지 않는다 — 핸들을 그냥 누르기만 한 경우.
    if (Math.round(startWidth) === width) return;
    resizeColMut.mutate({ key, width });
  };

  const startEdit = (row: number, col: number) => {
    const cells = getRow(row);
    if (!cells) return;
    setEditing({ row, col });
    // 수식 셀을 고를 땐 계산된 값이 아니라 수식을 보여준다 — 값을 보여주면
    // 자기가 쓴 수식을 다시 볼 방법이 없다.
    setDraft(getFormulas(row)[meta.columns[col].key] ?? cells[col] ?? "");
  };

  const commitEdit = () => {
    if (!editing) return;
    const cells = getRow(editing.row);
    if (!cells) {
      setEditing(null);
      return;
    }
    const rowFormulas = getFormulas(editing.row);

    // 서버로는 수식 셀에 원본 수식을 돌려준다 — 계산된 값을 돌려주면 서버가
    // 사용자가 직접 입력한 것으로 보고 수식을 지운다.
    const sent = Array.from({ length: colCount }, (_, c) => {
      if (c === editing.col) return draft;
      return rowFormulas[meta.columns[c].key] ?? cells[c] ?? "";
    });
    // 화면엔 값을 유지한다(수식 원본이 잠깐 보이면 안 된다).
    const shown = Array.from({ length: colCount }, (_, c) =>
      c === editing.col ? draft : (cells[c] ?? ""),
    );

    setRowLocal(editing.row, shown);
    updateMut.mutate({ index: editing.row, cells: sent });
    setEditing(null);
  };

  /** 셀 배경색을 바꾼다. 정렬 등 다른 서식은 보존한다(서버는 통째로 교체하므로). */
  const applyBg = (row: number, col: number, bg: CellBgToken | null) => {
    const colKey = meta.columns[col].key;
    const current = getStyles(row)[colKey];
    styleMut.mutate({
      row,
      colKey,
      style: { bg: bg ?? undefined, align: current?.align },
    });
    setPalette(null);
  };

  const addRow = () =>
    insertMut.mutate(Array.from({ length: colCount }, () => ""));

  // 이름은 서버가 붙인다 — 열 개수로 지으면 삭제 후 중복된다.
  const addColumn = () => addColMut.mutate();

  const columnLabel = (key: string) =>
    meta.columns.find((c) => c.key === key)?.label ?? key;

  /** 끌던 열을 대상 열 자리에 놓는다. */
  const dropColumnOn = (targetKey: string) => {
    const from = dragKey;
    setDragKey(null);
    if (!from || from === targetKey) return;
    const keys = meta.columns.map((c) => c.key);
    const at = keys.indexOf(from);
    const to = keys.indexOf(targetKey);
    if (at < 0 || to < 0) return;
    keys.splice(to, 0, keys.splice(at, 1)[0]);
    reorderColMut.mutate(keys);
  };

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
              // 이름 편집 중엔 텍스트 선택을 막지 않도록 드래그를 끈다.
              // 리사이즈 중에도 꺼야 한다 — 켜두면 핸들을 끄는 순간 HTML5 드래그가
              // 시작돼 열 순서 변경으로 새어 나간다.
              draggable={editingCol !== header.id && resizing === null}
              onDragStart={() => setDragKey(header.id)}
              onDragEnd={() => setDragKey(null)}
              onDragOver={(e) => e.preventDefault()}
              onDrop={() => dropColumnOn(header.id)}
              data-dragging={dragKey === header.id || undefined}
              className={cn(
                "border-border group flex items-center border-r",
                editingCol !== header.id && "cursor-grab",
                dragKey === header.id && "opacity-50",
              )}
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
              {/* 열 경계 리사이즈 핸들. 이름 편집 중엔 내보내지 않는다. */}
              {editingCol !== header.id && (
                <div
                  role="separator"
                  aria-orientation="vertical"
                  aria-label={`${columnLabel(header.id)} 열 너비 조절`}
                  title="드래그해 너비 조절 · 더블클릭해 기본 폭으로"
                  onPointerDown={(e) => startResize(header.id, e)}
                  onPointerMove={moveResize}
                  onPointerUp={endResize}
                  onPointerCancel={endResize}
                  onDoubleClick={() => resetColWidthMut.mutate(header.id)}
                  // 부모가 draggable이라 핸들에서 시작한 드래그가 순서 변경으로 새는 것을 막는다.
                  draggable={false}
                  onDragStart={(e) => e.preventDefault()}
                  className={cn(
                    "hover:bg-primary/60 -mr-[3px] h-full w-[6px] shrink-0 cursor-col-resize touch-none",
                    resizing?.key === header.id && "bg-primary/60",
                  )}
                />
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
                  const colKey = meta.columns[c].key;
                  const formula = getFormulas(vi.index)[colKey];
                  const style = getStyles(vi.index)[colKey];
                  const paletteOpen =
                    palette?.row === vi.index && palette.col === c;
                  return (
                    <div
                      key={c}
                      className="border-border group/cell relative truncate border-r"
                      style={
                        style?.bg
                          ? { background: `var(--cell-bg-${style.bg})` }
                          : undefined
                      }
                      onClick={() => startEdit(vi.index, c)}
                    >
                      {/* 셀 배경색 버튼 — 호버 시 나타난다. */}
                      {!isEditing && (
                        <button
                          type="button"
                          aria-label={`${vi.index + 1}행 ${c + 1}열 배경색`}
                          onClick={(e) => {
                            e.stopPropagation();
                            setPalette(
                              paletteOpen ? null : { row: vi.index, col: c },
                            );
                          }}
                          className="text-muted-foreground hover:text-foreground absolute top-0.5 right-0.5 z-10 opacity-0 group-hover/cell:opacity-100 focus-visible:opacity-100"
                        >
                          <Palette className="size-3" />
                        </button>
                      )}
                      {paletteOpen && (
                        <div
                          role="menu"
                          className="border-border bg-popover absolute top-5 right-0 z-20 flex gap-1 rounded-md border p-1 shadow-md"
                          onClick={(e) => e.stopPropagation()}
                        >
                          {CELL_BG_TOKENS.map((token) => (
                            <button
                              key={token}
                              type="button"
                              aria-label={`배경색 ${token}`}
                              onClick={() => applyBg(vi.index, c, token)}
                              className={cn(
                                "size-4 rounded-full border",
                                style?.bg === token
                                  ? "border-foreground"
                                  : "border-border",
                              )}
                              style={{ background: `var(--cell-bg-${token})` }}
                            />
                          ))}
                          <button
                            type="button"
                            aria-label="배경색 지우기"
                            onClick={() => applyBg(vi.index, c, null)}
                            className="text-muted-foreground hover:text-foreground border-border flex size-4 items-center justify-center rounded-full border"
                          >
                            <X className="size-2.5" />
                          </button>
                        </div>
                      )}
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
                            isCellError(cells?.[c]) && "text-destructive",
                            formula !== undefined &&
                              showFormulas &&
                              "text-muted-foreground font-mono text-xs",
                          )}
                          title={formula}
                        >
                          {cells === undefined
                            ? "…"
                            : (showFormulas && formula) || (cells[c] ?? "")}
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
      <div className="border-border flex items-center gap-1 border-t p-1">
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={addRow}
          disabled={insertMut.isPending}
        >
          <Plus className="size-4" /> 행 추가
        </Button>
        <Button
          type="button"
          variant={showFormulas ? "secondary" : "ghost"}
          size="sm"
          aria-pressed={showFormulas}
          onClick={() => setShowFormulas((v) => !v)}
          title="수식 셀에 계산 결과 대신 수식을 보여준다"
        >
          <FunctionSquare className="size-4" /> 수식 보기
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

/** 서버가 알려주는 실패 사유(수식 문법 오류·순환 참조 등). 없으면 조용히 넘어간다. */
function serverMessage(e: unknown): string | null {
  const data = (e as { response?: { data?: { message?: string } } })?.response
    ?.data;
  return typeof data?.message === "string" ? data.message : null;
}

/** 서버가 계산 대신 넣은 셀 에러(#REF! #VALUE! #DIV/0!). 쿼리의 isError와 헷갈리지 않게 이름을 구분한다. */
function isCellError(value: string | undefined): boolean {
  return value !== undefined && value.startsWith("#");
}
