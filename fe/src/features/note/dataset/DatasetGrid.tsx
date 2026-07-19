import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  type ColumnDef,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table";
import { useWindowVirtualizer } from "@tanstack/react-virtual";
import {
  AlignCenter,
  AlignLeft,
  AlignRight,
  FunctionSquare,
  Palette,
  Plus,
  TableCellsMerge,
  TableCellsSplit,
  Trash2,
  X,
} from "lucide-react";
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";

import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field-error";
import { LoadingText } from "@/components/ui/loading-text";
import { cn } from "@/lib/utils";
import { toast } from "@/shared/lib/toast";

import {
  addDatasetColumn,
  CELL_BG_TOKENS,
  type CellAlign,
  type CellBgToken,
  type CellStyle,
  type DatasetMeta,
  deleteCellMerge,
  deleteDatasetColumn,
  deleteDatasetRow,
  insertDatasetRow,
  MAX_COLUMN_WIDTH,
  type MergeSpan,
  type MergeView,
  MIN_COLUMN_WIDTH,
  renameDatasetColumn,
  reorderDatasetColumns,
  resetDatasetColumnWidth,
  resizeDatasetColumn,
  setCellMerge,
  setCellStyle,
  setDatasetColumnAlign,
  updateDatasetRow,
} from "./api/datasets";
import { useDatasetMerges } from "./hooks/useDatasetMerges";
import { useDatasetMeta } from "./hooks/useDatasetMeta";
import { useDatasetRows } from "./hooks/useDatasetRows";
import { datasetKeys } from "./queryKeys";

/** 행 높이(px) — 고정. 좌우(열 너비)만 조절 가능하고 위아래는 조절하지 않는다. */
const ROW_HEIGHT = 36;

interface Props {
  datasetId: number;
}

/**
 * 대용량 편집 표. TanStack Table(열/헤더) + 윈도우 가상화(행) + 지연 로드 + 셀/행 편집.
 * 세로는 노트 페이지 흐름을 따라 무한히 자라고(자체 스크롤 뷰포트 없음),
 * 가로는 열이 화면을 넘으면 스크롤한다.
 */
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
  // 병합은 dataset 단위로 통째 받는다 — 세로 병합은 앵커가 화면 밖이어도 덮인 행을 그려야 한다.
  const { data: merges = [] } = useDatasetMerges(datasetId);
  // 가상화 목록(본문)의 문서 최상단으로부터의 오프셋. 윈도우 스크롤 기준 가상화에 쓴다.
  const listRef = useRef<HTMLDivElement>(null);
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
  // 본문 목록의 문서 오프셋(px). 윈도우 가상화의 scrollMargin — 위쪽 콘텐츠 높이가
  // 바뀌면 달라지므로 ResizeObserver로 다시 잰다.
  const [scrollMargin, setScrollMargin] = useState(0);
  // 우클릭 컨텍스트 메뉴(뷰포트 좌표 + 대상 셀). null이면 닫힘.
  const [ctxMenu, setCtxMenu] = useState<{
    x: number;
    y: number;
    row: number;
    col: number;
  } | null>(null);

  const colCount = meta?.columns.length ?? 0;
  const rowCount = meta?.rowCount ?? 0;

  const invalidateMeta = () =>
    queryClient.invalidateQueries({ queryKey: datasetKeys.meta(datasetId) });
  // 행/열 구조가 바뀌면 병합의 행 번호가 밀리거나(행 삽입·삭제) 병합이 해제될 수 있어(열 삭제·순서변경)
  // 병합을 다시 받는다.
  const invalidateMerges = () =>
    queryClient.invalidateQueries({ queryKey: datasetKeys.merges(datasetId) });

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
  const mergeMut = useMutation({
    mutationFn: (v: { row: number; colKey: string; span: MergeSpan }) =>
      setCellMerge(datasetId, v.row, v.colKey, v.span),
    // 병합은 표시 오버레이라 값 캐시는 그대로 두고 병합 리스트만 갱신한다(서버가 전체를 돌려준다).
    onSuccess: (list: MergeView[]) =>
      queryClient.setQueryData(datasetKeys.merges(datasetId), list),
    onError: (e) => {
      // 경계 초과·겹침 등은 서버가 사유를 알려준다.
      const message = serverMessage(e);
      if (message) toast(message, "error");
    },
  });
  const unmergeMut = useMutation({
    mutationFn: (v: { row: number; colKey: string }) =>
      deleteCellMerge(datasetId, v.row, v.colKey),
    onSuccess: (list: MergeView[]) =>
      queryClient.setQueryData(datasetKeys.merges(datasetId), list),
    onError: (e) => {
      const message = serverMessage(e);
      if (message) toast(message, "error");
    },
  });
  const insertMut = useMutation({
    mutationFn: (v: { cells: string[]; atIndex?: number }) =>
      insertDatasetRow(datasetId, v.cells, v.atIndex),
    onSuccess: () => {
      reset();
      void invalidateMeta();
      void invalidateMerges(); // 뒤 행이 밀려 병합의 행 번호가 바뀌고, 안에 끼면 해제된다.
    },
  });
  const deleteMut = useMutation({
    mutationFn: (index: number) => deleteDatasetRow(datasetId, index),
    onSuccess: () => {
      reset();
      void invalidateMeta();
      void invalidateMerges(); // 앵커 행 삭제는 cascade, 뒤 행은 번호가 밀린다.
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
      void invalidateMerges(); // 순서 변경 시 서버가 병합을 해제한다(v1 보수적).
    },
    onError: () => void invalidateMeta(),
  });
  const deleteColMut = useMutation({
    mutationFn: (key: string) => deleteDatasetColumn(datasetId, key),
    onSuccess: (next: DatasetMeta) => {
      queryClient.setQueryData(datasetKeys.meta(datasetId), next);
      // 캐시된 cells는 삭제 전 열 순서 기준이라 그대로 쓰면 값이 밀린다. 다시 받아 온다.
      reset();
      void invalidateMerges(); // 그 열에 걸친 병합이 해제됐을 수 있다.
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
  const setColAlignMut = useMutation({
    mutationFn: (v: { key: string; align: CellAlign }) =>
      setDatasetColumnAlign(datasetId, v.key, v.align),
    // 정렬은 열 단위 속성이라 행 캐시를 버릴 이유가 없다(너비와 같다 — 값이 안 밀린다).
    onSuccess: (next: DatasetMeta) =>
      queryClient.setQueryData(datasetKeys.meta(datasetId), next),
    onError: () => void invalidateMeta(),
  });
  const addColMut = useMutation({
    mutationFn: (v: { atIndex?: number }) =>
      addDatasetColumn(datasetId, v.atIndex),
    onSuccess: (next: DatasetMeta, v) => {
      queryClient.setQueryData(datasetKeys.meta(datasetId), next);
      // 끝에 추가면 새 열 key는 어느 행에도 값이 없어 캐시된 짧은 cells를 그대로 둬도 된다.
      // 중간 삽입이면 뒤 열이 밀려 위치 배열이 어긋나고, 안에 끼면 병합이 해제됐을 수 있다 → 다시 받는다.
      if (v.atIndex != null) {
        reset();
        void invalidateMerges();
      }
    },
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

  const virtualizer = useWindowVirtualizer({
    count: rowCount,
    estimateSize: () => ROW_HEIGHT,
    overscan: 12,
    scrollMargin,
  });
  const virtualItems = virtualizer.getVirtualItems();
  const firstIndex = virtualItems[0]?.index ?? 0;
  const lastIndex = virtualItems[virtualItems.length - 1]?.index ?? 0;

  useEffect(() => {
    if (rowCount > 0) ensureRange(firstIndex, lastIndex);
  }, [firstIndex, lastIndex, rowCount, ensureRange]);

  // 본문 목록의 문서 오프셋을 잰다. 표 위의 콘텐츠(다른 노트 블록) 높이가 바뀌면
  // 오프셋이 달라지므로 document.body 리사이즈와 창 리사이즈에 다시 측정한다.
  useLayoutEffect(() => {
    const measure = () => {
      const el = listRef.current;
      if (!el) return;
      const next = el.getBoundingClientRect().top + window.scrollY;
      setScrollMargin((prev) => (Math.abs(prev - next) > 0.5 ? next : prev));
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(document.body);
    window.addEventListener("resize", measure);
    return () => {
      observer.disconnect();
      window.removeEventListener("resize", measure);
    };
  }, []);

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

  const colIndexByKey = new Map(meta.columns.map((c, i) => [c.key, i]));
  // 세로·블록 병합(rowSpan>1)은 오버레이로 그린다(가상화와 양립하려면 앵커 행 렌더에 의존하면 안 된다).
  // 가로 전용 병합(rowSpan===1)은 그 행 안에서 in-grid span으로 그린다(슬라이스 1과 같다).
  const overlayMerges = merges.filter((m) => m.rowSpan > 1);
  const colOf = (key: string) => colIndexByKey.get(key) ?? -1;
  /** (row,c)를 덮는 세로·블록 병합. 있으면 그 셀은 오버레이가 그리므로 base엔 자리만 둔다. */
  const overlayCovering = (row: number, c: number) =>
    overlayMerges.find((m) => {
      const ai = colOf(m.colKey);
      return (
        ai >= 0 &&
        ai <= c &&
        c < ai + m.colSpan &&
        m.rowIndex <= row &&
        row < m.rowIndex + m.rowSpan
      );
    });

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

  /**
   * 셀 정렬을 바꾼다(열 기본을 덮는 셀 단위 override). 배경색은 보존한다.
   * null이면 셀 정렬을 지워 열 기본 정렬을 따르게 한다.
   */
  const applyAlign = (row: number, col: number, align: CellAlign | null) => {
    const colKey = meta.columns[col].key;
    const current = getStyles(row)[colKey];
    styleMut.mutate({
      row,
      colKey,
      style: { bg: current?.bg, align: align ?? undefined },
    });
    setPalette(null);
  };

  /** 열 기본 정렬을 다음 값으로 돌린다(좌→가운데→우→좌). 기본(미설정)은 좌로 본다. */
  const cycleColumnAlign = (key: string, current: CellAlign | undefined) => {
    const next: CellAlign =
      current === "center" ? "right" : current === "right" ? "left" : "center";
    setColAlignMut.mutate({ key, align: next });
  };

  /** (row,col)이 앵커인 병합. 없으면 undefined. */
  const mergeAt = (row: number, col: number): MergeView | undefined =>
    merges.find(
      (m) => m.rowIndex === row && m.colKey === meta.columns[col].key,
    );

  /** 앵커 셀을 오른쪽 열과 한 칸 더 병합한다. 이미 병합이면 colSpan+1(rowSpan은 유지). */
  const mergeRight = (row: number, col: number) => {
    const current = mergeAt(row, col);
    const colSpan = (current?.colSpan ?? 1) + 1;
    const rowSpan = current?.rowSpan ?? 1;
    // 오른쪽 끝을 넘으면 담을 열이 없다(서버도 막지만 헛요청을 줄인다).
    if (col + colSpan > colCount) return;
    mergeMut.mutate({
      row,
      colKey: meta.columns[col].key,
      span: { rowSpan, colSpan },
    });
    setPalette(null);
  };

  /** 앵커 셀을 아래 행과 한 칸 더 병합한다. 이미 병합이면 rowSpan+1(colSpan은 유지). */
  const mergeDown = (row: number, col: number) => {
    const current = mergeAt(row, col);
    const rowSpan = (current?.rowSpan ?? 1) + 1;
    const colSpan = current?.colSpan ?? 1;
    if (row + rowSpan > rowCount) return; // 아래 끝을 넘으면 담을 행이 없다.
    mergeMut.mutate({
      row,
      colKey: meta.columns[col].key,
      span: { rowSpan, colSpan },
    });
    setPalette(null);
  };

  /** 병합 해제. 덮여 있던 셀 값이 그 자리에 되살아난다. */
  const unmerge = (row: number, col: number) => {
    unmergeMut.mutate({ row, colKey: meta.columns[col].key });
    setPalette(null);
  };

  /** 셀 우클릭 → 그 자리에 컨텍스트 메뉴를 연다(행/열 삽입·삭제·병합). */
  const openContextMenu = (
    event: React.MouseEvent,
    row: number,
    col: number,
  ) => {
    event.preventDefault();
    setPalette(null);
    setCtxMenu({ x: event.clientX, y: event.clientY, row, col });
  };

  const emptyRow = () => Array.from({ length: colCount }, () => "");
  // atIndex를 주면 그 위치에 삽입(위/아래), 없으면 끝에 추가.
  const addRow = (atIndex?: number) =>
    insertMut.mutate({ cells: emptyRow(), atIndex });

  // 이름은 서버가 붙인다 — 열 개수로 지으면 삭제 후 중복된다.
  const addColumn = (atIndex?: number) => addColMut.mutate({ atIndex });

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

  /**
   * 셀 내부(서식 버튼·팝오버·값/입력). base 그리드 셀과 세로 병합 오버레이 박스가 함께 쓴다 —
   * 병합 셀 UI를 오버레이에서 다시 만들지 않으려는 것.
   */
  const renderCellInner = (rowIndex: number, c: number) => {
    const colKey = meta.columns[c].key;
    const isEditing = editing?.row === rowIndex && editing.col === c;
    const cells = getRow(rowIndex);
    const formula = getFormulas(rowIndex)[colKey];
    const style = getStyles(rowIndex)[colKey];
    // 셀 정렬(override) > 열 기본 정렬 > 기본(left) (#828 D2).
    const align = style?.align ?? meta.columns[c].align ?? "left";
    const paletteOpen = palette?.row === rowIndex && palette.col === c;
    return (
      <>
        {/* 셀 서식·병합 버튼 — 호버 시 나타난다. */}
        {!isEditing && (
          <button
            type="button"
            aria-label={`${rowIndex + 1}행 ${c + 1}열 서식`}
            onClick={(e) => {
              e.stopPropagation();
              setPalette(paletteOpen ? null : { row: rowIndex, col: c });
            }}
            className="text-muted-foreground hover:text-foreground absolute top-0.5 right-0.5 z-10 opacity-0 group-hover/cell:opacity-100 focus-visible:opacity-100"
          >
            <Palette className="size-3" />
          </button>
        )}
        {paletteOpen && (
          <div
            role="menu"
            className="border-border bg-popover absolute top-5 right-0 z-20 flex flex-col gap-1 rounded-md border p-1 shadow-md"
            onClick={(e) => e.stopPropagation()}
          >
            {/* 배경색 — 6색 스와치 + 지우기 */}
            <div className="flex gap-1">
              {CELL_BG_TOKENS.map((token) => (
                <button
                  key={token}
                  type="button"
                  aria-label={`배경색 ${token}`}
                  onClick={() => applyBg(rowIndex, c, token)}
                  className={cn(
                    "size-4 rounded-full border",
                    style?.bg === token ? "border-foreground" : "border-border",
                  )}
                  style={{ background: `var(--cell-bg-${token})` }}
                />
              ))}
              <button
                type="button"
                aria-label="배경색 지우기"
                onClick={() => applyBg(rowIndex, c, null)}
                className="text-muted-foreground hover:text-foreground border-border flex size-4 items-center justify-center rounded-full border"
              >
                <X className="size-2.5" />
              </button>
            </div>
            {/* 정렬 — 셀 단위 override(열 기본을 덮는다) + 열 기본 따르기 */}
            <div className="flex gap-1">
              {ALIGN_ORDER.map((value) => {
                const Icon = ALIGN_ICONS[value];
                return (
                  <button
                    key={value}
                    type="button"
                    aria-label={`정렬 ${ALIGN_LABELS[value]}`}
                    onClick={() => applyAlign(rowIndex, c, value)}
                    className={cn(
                      "hover:bg-accent flex size-4 items-center justify-center rounded border",
                      style?.align === value
                        ? "border-foreground text-foreground"
                        : "border-border text-muted-foreground",
                    )}
                  >
                    <Icon className="size-2.5" />
                  </button>
                );
              })}
              <button
                type="button"
                aria-label="셀 정렬 지우기 (열 기본 따르기)"
                title="열 기본 정렬을 따른다"
                onClick={() => applyAlign(rowIndex, c, null)}
                className="text-muted-foreground hover:text-foreground border-border flex size-4 items-center justify-center rounded border"
              >
                <X className="size-2.5" />
              </button>
            </div>
            {/* 삽입·삭제·병합은 셀 우클릭 메뉴로. */}
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
            aria-label={`셀 ${rowIndex + 1}행 ${c + 1}열`}
            className={cn(
              "focus-visible:ring-ring h-full w-full bg-transparent px-2 py-1 outline-none focus-visible:ring-1",
              ALIGN_CLASS[align],
            )}
          />
        ) : (
          <div
            className={cn(
              "h-full cursor-text truncate px-2 py-1.5",
              ALIGN_CLASS[align],
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
      </>
    );
  };

  return (
    <div
      className="border-border bg-card group/grid my-2 flex flex-col overflow-hidden rounded-md border"
      data-testid="dataset-grid"
    >
      {/* 헤더+본문을 한 컨테이너에 둔다. 가로는 열이 넘치면 스크롤하고(overflow-x-auto),
          세로는 뷰포트를 두지 않아 행이 늘수록 페이지 흐름대로 자란다. */}
      <div className="overflow-x-auto">
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
                  {/* 열 기본 정렬 — 클릭하면 좌→가운데→우로 순환한다. 아이콘이 현재 정렬을 보여준다. */}
                  {(() => {
                    const align =
                      meta.columns.find((c) => c.key === header.id)?.align ??
                      "left";
                    const Icon = ALIGN_ICONS[align];
                    return (
                      <button
                        type="button"
                        aria-label={`${columnLabel(header.id)} 열 정렬 (현재 ${ALIGN_LABELS[align]})`}
                        title="클릭해 열 정렬 변경 (좌·가운데·우)"
                        onClick={() =>
                          cycleColumnAlign(
                            header.id,
                            meta.columns.find((c) => c.key === header.id)
                              ?.align,
                          )
                        }
                        className="text-muted-foreground hover:text-foreground mr-1 shrink-0 opacity-0 group-hover:opacity-100 focus-visible:opacity-100"
                      >
                        <Icon className="size-3.5" />
                      </button>
                    );
                  })()}
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
          {/* 행 삭제 버튼 열(44px) 위 자리 — 열 추가 버튼을 둔다.
              표에 마우스를 올렸을 때(또는 키보드 포커스 시)만 보인다. */}
          <button
            type="button"
            aria-label="열 추가"
            title="열 추가"
            onClick={() => addColumn()}
            disabled={addColMut.isPending}
            className="text-muted-foreground hover:text-foreground flex items-center justify-center opacity-0 transition-opacity group-hover/grid:opacity-100 focus-visible:opacity-100 disabled:opacity-50"
          >
            <Plus className="size-3.5" />
          </button>
        </div>

        {/* 본문 (윈도우 가상화) */}
        <div
          ref={listRef}
          style={{ height: virtualizer.getTotalSize(), position: "relative" }}
        >
          {virtualItems.map((vi) => {
            // 그 행에 앵커가 있는 가로 전용 병합(rowSpan===1). 덮인 칸은 스킵하고 앵커가 span으로 넓게.
            const hCovered = new Set<number>();
            const hAnchor = new Map<number, MergeView>();
            for (const m of merges) {
              if (m.rowIndex !== vi.index || m.rowSpan !== 1) continue;
              const ai = colOf(m.colKey);
              if (ai < 0) continue;
              hAnchor.set(ai, m);
              for (let k = 1; k < m.colSpan; k++) hCovered.add(ai + k);
            }
            return (
              <div
                key={vi.key}
                className="border-border absolute top-0 left-0 grid w-full border-b text-sm"
                style={{
                  height: ROW_HEIGHT,
                  // vi.start는 문서 좌표(scrollMargin 포함)라 목록 기준으로 되돌린다.
                  transform: `translateY(${vi.start - scrollMargin}px)`,
                  gridTemplateColumns,
                }}
              >
                {Array.from({ length: colCount }, (_, c) => {
                  // 세로·블록 병합이 덮는 칸은 오버레이가 그리므로 base엔 자리만 둔다(정렬 유지).
                  if (overlayCovering(vi.index, c)) {
                    return <div key={c} aria-hidden className="border-r" />;
                  }
                  // 가로 병합에 덮인 칸은 앵커가 span으로 차지하므로 렌더하지 않는다.
                  if (hCovered.has(c)) return null;
                  const anchor = hAnchor.get(c);
                  const style = getStyles(vi.index)[meta.columns[c].key];
                  return (
                    <div
                      key={c}
                      className="border-border group/cell relative truncate border-r"
                      style={{
                        ...(style?.bg
                          ? { background: `var(--cell-bg-${style.bg})` }
                          : {}),
                        // 가로 병합 앵커는 colSpan만큼 그리드 열을 차지한다(덮인 칸 스킵과 짝).
                        ...(anchor
                          ? { gridColumn: `span ${anchor.colSpan}` }
                          : {}),
                      }}
                      onClick={() => startEdit(vi.index, c)}
                      onContextMenu={(e) => openContextMenu(e, vi.index, c)}
                    >
                      {renderCellInner(vi.index, c)}
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

          {/* 세로·블록 병합(rowSpan>1) 오버레이 — 가상화가 앵커 행을 안 그려도 덮인 영역을 그린다.
              같은 gridTemplateColumns를 쓰는 grid에 열 배치로 x·폭을 맞추고(유연폭 대응),
              고정 행 높이라 세로는 top·height를 px로 계산한다(측정 불필요). */}
          <div
            className="pointer-events-none absolute top-0 left-0 grid w-full"
            style={{ gridTemplateColumns, height: virtualizer.getTotalSize() }}
          >
            {overlayMerges
              .filter(
                (m) =>
                  m.rowIndex <= lastIndex &&
                  m.rowIndex + m.rowSpan > firstIndex,
              )
              .map((m) => {
                const ai = colOf(m.colKey);
                if (ai < 0) return null;
                const style = getStyles(m.rowIndex)[m.colKey];
                return (
                  <div
                    key={`${m.rowIndex}:${m.colKey}`}
                    style={{ gridColumn: `${ai + 1} / span ${m.colSpan}` }}
                    className="relative"
                  >
                    <div
                      className="border-border bg-card group/cell pointer-events-auto absolute right-0 left-0 truncate border-r border-b text-sm"
                      style={{
                        top: m.rowIndex * ROW_HEIGHT,
                        height: m.rowSpan * ROW_HEIGHT,
                        ...(style?.bg
                          ? { background: `var(--cell-bg-${style.bg})` }
                          : {}),
                      }}
                      onClick={() => startEdit(m.rowIndex, ai)}
                      onContextMenu={(e) => openContextMenu(e, m.rowIndex, ai)}
                    >
                      {renderCellInner(m.rowIndex, ai)}
                    </div>
                  </div>
                );
              })}
          </div>
        </div>
      </div>

      {/* 행 추가 — 표에 마우스를 올렸을 때(또는 키보드 포커스 시)만 [행 추가]가 보인다. */}
      <div className="border-border flex items-center gap-1 border-t p-1">
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => addRow()}
          disabled={insertMut.isPending}
          className="opacity-0 transition-opacity group-hover/grid:opacity-100 focus-visible:opacity-100"
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

      {/* 셀 우클릭 컨텍스트 메뉴 — 행/열 삽입·삭제·병합을 한 곳에 모은다. */}
      {ctxMenu &&
        (() => {
          const { row, col } = ctxMenu;
          const cm = mergeAt(row, col);
          const colSpan = cm?.colSpan ?? 1;
          const rowSpan = cm?.rowSpan ?? 1;
          const colKey = meta.columns[col].key;
          const close = () => setCtxMenu(null);
          const item = (
            label: string,
            onClick: () => void,
            opts: { icon?: React.ReactNode; destructive?: boolean } = {},
          ) => (
            <button
              type="button"
              role="menuitem"
              onClick={() => {
                onClick();
                close();
              }}
              className={cn(
                "hover:bg-accent flex w-full items-center gap-2 rounded px-2 py-1.5 text-left text-sm",
                opts.destructive && "text-destructive",
              )}
            >
              {opts.icon}
              {label}
            </button>
          );
          const canMergeRight = col + colSpan < colCount;
          const canMergeDown = row + rowSpan < rowCount;
          return (
            <>
              {/* 바깥 클릭·우클릭으로 닫는다. */}
              <div
                className="fixed inset-0 z-40"
                onClick={close}
                onContextMenu={(e) => {
                  e.preventDefault();
                  close();
                }}
              />
              <div
                role="menu"
                aria-label="셀 메뉴"
                className="border-border bg-popover fixed z-50 min-w-40 rounded-md border p-1 shadow-md"
                // 뷰포트 오른쪽·아래로 넘치지 않게 대략 클램프한다(메뉴 크기 여유분).
                style={{
                  left: Math.min(ctxMenu.x, window.innerWidth - 176),
                  top: Math.min(ctxMenu.y, window.innerHeight - 340),
                }}
              >
                {item("위에 행 삽입", () => addRow(row), {
                  icon: <Plus className="size-3.5" />,
                })}
                {item("아래에 행 삽입", () => addRow(row + 1), {
                  icon: <Plus className="size-3.5" />,
                })}
                {item("왼쪽에 열 삽입", () => addColumn(col), {
                  icon: <Plus className="size-3.5" />,
                })}
                {item("오른쪽에 열 삽입", () => addColumn(col + 1), {
                  icon: <Plus className="size-3.5" />,
                })}
                {(canMergeRight || canMergeDown || cm) && (
                  <div className="bg-border my-1 h-px" />
                )}
                {canMergeRight &&
                  item("오른쪽과 병합", () => mergeRight(row, col), {
                    icon: <TableCellsMerge className="size-3.5" />,
                  })}
                {canMergeDown &&
                  item("아래와 병합", () => mergeDown(row, col), {
                    icon: <TableCellsMerge className="size-3.5 rotate-90" />,
                  })}
                {cm &&
                  item("병합 해제", () => unmerge(row, col), {
                    icon: <TableCellsSplit className="size-3.5" />,
                  })}
                <div className="bg-border my-1 h-px" />
                {item("행 삭제", () => deleteMut.mutate(row), {
                  icon: <Trash2 className="size-3.5" />,
                  destructive: true,
                })}
                {colCount > 1 &&
                  item("열 삭제", () => setPendingColDelete(colKey), {
                    icon: <X className="size-3.5" />,
                    destructive: true,
                  })}
              </div>
            </>
          );
        })()}
    </div>
  );
}

const EMPTY: string[][] = [];

/** 정렬 값 → 아이콘. 열/셀 정렬 버튼과 렌더에 공용. */
const ALIGN_ICONS = {
  left: AlignLeft,
  center: AlignCenter,
  right: AlignRight,
} as const;
const ALIGN_LABELS: Record<CellAlign, string> = {
  left: "왼쪽",
  center: "가운데",
  right: "오른쪽",
};
/** 정렬 값 → Tailwind text-align 클래스. 인라인 style 대신 클래스로 둔다. */
const ALIGN_CLASS: Record<CellAlign, string> = {
  left: "text-left",
  center: "text-center",
  right: "text-right",
};
/** 정렬 버튼 순서. */
const ALIGN_ORDER: CellAlign[] = ["left", "center", "right"];

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
