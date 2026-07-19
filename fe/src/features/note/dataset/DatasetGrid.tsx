import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useWindowVirtualizer } from "@tanstack/react-virtual";
import {
  AlignCenter,
  AlignLeft,
  AlignRight,
  Eraser,
  Palette,
  Plus,
  TableCellsMerge,
  TableCellsSplit,
  Trash2,
  X,
} from "lucide-react";
import { useEffect, useLayoutEffect, useRef, useState } from "react";

import { ConfirmDialog } from "@/components/ConfirmDialog";
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
  resetDatasetColumnWidth,
  resizeDatasetColumn,
  setCellMerge,
  setCellStyle,
  setCellStylesBulk,
  updateDatasetRow,
} from "./api/datasets";
import { useDatasetMerges } from "./hooks/useDatasetMerges";
import { useDatasetMeta } from "./hooks/useDatasetMeta";
import { useDatasetRows } from "./hooks/useDatasetRows";
import { datasetKeys } from "./queryKeys";

/** 행 높이(px) — 고정. 좌우(열 너비)만 조절 가능하고 위아래는 조절하지 않는다. */
const ROW_HEIGHT = 36;

/**
 * 선택 범위 — 셀 사각 범위(a=앵커, b=포커스) / 행 묶음 / 열 묶음 / 표 전체.
 * null이면 선택 없음. 선택 위 플로팅 툴바가 이 종류를 보고 옵션을 바꾼다.
 */
type Sel =
  | { kind: "cells"; a: [number, number]; b: [number, number] }
  | { kind: "rows"; a: number; b: number }
  | { kind: "cols"; a: number; b: number }
  | { kind: "table" }
  | null;

interface Props {
  datasetId: number;
}

/**
 * notion 스타일 값-중심 표. 제목(헤더) 행 없이 값 셀만 그린다 + 윈도우 가상화(행) +
 * 지연 로드 + 셀 편집. 세로는 노트 페이지 흐름을 따라 무한히 자라고(자체 스크롤 뷰포트 없음),
 * 가로는 열이 화면을 넘으면 스크롤한다. 열 너비는 셀 오른쪽 경계 드래그로 조절한다.
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
  const [pendingColDelete, setPendingColDelete] = useState<string | null>(null);
  // 리사이즈 중인 열과 진행 중 너비. 드래그하는 동안엔 여기 값으로 그리고,
  // 저장은 놓을 때 한 번만 한다(mousemove마다 PATCH를 보내지 않는다).
  const [resizing, setResizing] = useState<{
    key: string;
    startX: number;
    startWidth: number;
    width: number;
  } | null>(null);
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
  // 선택 범위(셀/행/열/표). 클릭·드래그·핸들로 정하고, 선택 위 플로팅 툴바가 이걸 본다.
  const [sel, setSel] = useState<Sel>(null);
  // 드래그 선택 중인 축(셀/행/열). 눌러서 끌 때만 값이 있고 window pointerup에서 해제한다.
  const selDrag = useRef<null | "cells" | "rows" | "cols">(null);

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
  // 선택 범위 서식을 한 요청으로 적용(표 전체도 1회). 영향 행마다 styles 캐시를 갱신한다.
  const bulkStyleMut = useMutation({
    mutationFn: (
      cells: Array<{ rowIndex: number; colKey: string; style: CellStyle }>,
    ) => setCellStylesBulk(datasetId, cells),
    onSuccess: (rows) => {
      for (const row of rows) setStylesLocal(row.rowIndex, row.styles ?? {});
    },
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

  // 드래그 선택은 표 밖에서 손을 떼도 끝나야 하므로 window에서 pointerup을 듣는다.
  // Esc는 선택·편집을 함께 취소한다.
  useEffect(() => {
    const up = () => {
      selDrag.current = null;
    };
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        selDrag.current = null;
        setSel(null);
        setEditing(null);
      }
    };
    window.addEventListener("pointerup", up);
    window.addEventListener("keydown", key);
    return () => {
      window.removeEventListener("pointerup", up);
      window.removeEventListener("keydown", key);
    };
  }, []);

  if (isLoading) return <LoadingText />;
  if (isError || !meta) {
    return <FieldError>표를 불러오지 못했어요.</FieldError>;
  }

  // 너비를 지정한 열은 고정 px, 안 한 열은 기존대로 남는 폭을 나눠 갖는다.
  // 드래그 중인 열은 아직 저장 전이므로 진행 중 너비로 그린다.
  const gridTemplateColumns = meta.columns
    .map((col) => {
      const width = resizing?.key === col.key ? resizing.width : col.width;
      return width != null ? `${width}px` : "minmax(120px, 1fr)";
    })
    .join(" ");

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
    // 핸들의 부모 셀 실제 렌더 폭에서 시작한다. 너비 미지정(1fr) 열도 이 값으로 잡히므로
    // 첫 드래그가 기본 폭에서 자연스럽게 이어진다.
    const cell = event.currentTarget.parentElement;
    if (!cell) return;
    const startWidth = cell.getBoundingClientRect().width;
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
    // 편집에 들어가면 선택·툴바는 접는다(더블클릭이 선택→편집 순으로 오므로).
    setSel(null);
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

  // ---------- 선택(selection) ----------
  /** 선택을 정규화한 사각 범위(행·열 인덱스 경계). null이면 선택 없음. */
  const selRect = ((): {
    r0: number;
    c0: number;
    r1: number;
    c1: number;
  } | null => {
    if (!sel) return null;
    if (sel.kind === "table")
      return { r0: 0, c0: 0, r1: rowCount - 1, c1: colCount - 1 };
    if (sel.kind === "rows")
      return {
        r0: Math.min(sel.a, sel.b),
        c0: 0,
        r1: Math.max(sel.a, sel.b),
        c1: colCount - 1,
      };
    if (sel.kind === "cols")
      return {
        r0: 0,
        c0: Math.min(sel.a, sel.b),
        r1: rowCount - 1,
        c1: Math.max(sel.a, sel.b),
      };
    return {
      r0: Math.min(sel.a[0], sel.b[0]),
      c0: Math.min(sel.a[1], sel.b[1]),
      r1: Math.max(sel.a[0], sel.b[0]),
      c1: Math.max(sel.a[1], sel.b[1]),
    };
  })();

  /** (row,col)이 현재 선택 안에 드는가 — 셀 하이라이트에 쓴다. */
  const inSel = (row: number, col: number) =>
    selRect != null &&
    row >= selRect.r0 &&
    row <= selRect.r1 &&
    col >= selRect.c0 &&
    col <= selRect.c1;

  // 클릭=선택, 드래그=범위, shift+클릭=확장. (더블클릭은 편집으로 따로 간다)
  const startCellSelect = (row: number, col: number, shift: boolean) => {
    setEditing(null);
    setPalette(null);
    if (shift && sel?.kind === "cells") {
      setSel({ kind: "cells", a: sel.a, b: [row, col] });
    } else {
      selDrag.current = "cells";
      setSel({ kind: "cells", a: [row, col], b: [row, col] });
    }
  };
  const extendCellSelect = (row: number, col: number) => {
    if (selDrag.current !== "cells") return;
    setSel((s) => (s?.kind === "cells" ? { ...s, b: [row, col] } : s));
  };
  const startRowSelect = (row: number) => {
    setEditing(null);
    setPalette(null);
    selDrag.current = "rows";
    setSel({ kind: "rows", a: row, b: row });
  };
  const extendRowSelect = (row: number) => {
    if (selDrag.current !== "rows") return;
    setSel((s) => (s?.kind === "rows" ? { ...s, b: row } : s));
  };
  const startColSelect = (col: number) => {
    setEditing(null);
    setPalette(null);
    selDrag.current = "cols";
    setSel({ kind: "cols", a: col, b: col });
  };
  const extendColSelect = (col: number) => {
    if (selDrag.current !== "cols") return;
    setSel((s) => (s?.kind === "cols" ? { ...s, b: col } : s));
  };

  /** 선택에 포함된 (행 index, 열 key) 목록. 서식 일괄 적용에 쓴다. */
  const selCellRefs = (): Array<{ row: number; colKey: string }> => {
    if (!selRect) return [];
    const refs: Array<{ row: number; colKey: string }> = [];
    for (let r = selRect.r0; r <= selRect.r1; r++) {
      for (let c = selRect.c0; c <= selRect.c1; c++) {
        refs.push({ row: r, colKey: meta.columns[c].key });
      }
    }
    return refs;
  };
  // 서식 적용은 선택 전체를 한 요청으로 보낸다(표 전체도 1회). 각 셀은 통째 교체라
  // 바꾸지 않는 속성(정렬/배경)은 그 셀의 현재값을 채워 보존한다.
  const applyBgSel = (bg: CellBgToken | null) => {
    const cells = selCellRefs().map(({ row, colKey }) => ({
      rowIndex: row,
      colKey,
      style: { bg: bg ?? undefined, align: getStyles(row)[colKey]?.align },
    }));
    if (cells.length) bulkStyleMut.mutate(cells);
  };
  const applyAlignSel = (align: CellAlign | null) => {
    const cells = selCellRefs().map(({ row, colKey }) => ({
      rowIndex: row,
      colKey,
      style: { bg: getStyles(row)[colKey]?.bg, align: align ?? undefined },
    }));
    if (cells.length) bulkStyleMut.mutate(cells);
  };
  const clearFormatSel = () => {
    const cells = selCellRefs().map(({ row, colKey }) => ({
      rowIndex: row,
      colKey,
      style: {} as CellStyle,
    }));
    if (cells.length) bulkStyleMut.mutate(cells);
  };
  /** 셀 사각 범위를 하나의 병합으로(2칸 이상일 때만). */
  const mergeSel = () => {
    if (!selRect || sel?.kind !== "cells") return;
    const rowSpan = selRect.r1 - selRect.r0 + 1;
    const colSpan = selRect.c1 - selRect.c0 + 1;
    if (rowSpan * colSpan < 2) return;
    mergeMut.mutate({
      row: selRect.r0,
      colKey: meta.columns[selRect.c0].key,
      span: { rowSpan, colSpan },
    });
  };
  /** 선택한 행들을 뒤 인덱스부터 순서대로 지운다(인덱스가 밀리지 않게). */
  const deleteRowsSel = async () => {
    if (!selRect || sel?.kind !== "rows") return;
    for (let r = selRect.r1; r >= selRect.r0; r--) {
      await deleteDatasetRow(datasetId, r);
    }
    reset();
    void invalidateMeta();
    void invalidateMerges();
    setSel(null);
  };
  /** 선택한 열들을 지운다(key 기준이라 순서 무관). 최소 한 열은 남긴다. */
  const deleteColsSel = async () => {
    if (!selRect || sel?.kind !== "cols") return;
    const keys = meta.columns
      .slice(selRect.c0, selRect.c1 + 1)
      .map((c) => c.key);
    const toDelete =
      keys.length >= colCount ? keys.slice(0, colCount - 1) : keys;
    let last: DatasetMeta | null = null;
    for (const key of toDelete) {
      last = await deleteDatasetColumn(datasetId, key);
    }
    if (last) queryClient.setQueryData(datasetKeys.meta(datasetId), last);
    reset();
    void invalidateMerges();
    setSel(null);
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
            // pointerdown까지 막아야 셀 선택이 시작되지 않는다.
            onPointerDown={(e) => e.stopPropagation()}
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
            onPointerDown={(e) => e.stopPropagation()}
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
            )}
            title={formula}
          >
            {cells === undefined ? "…" : (cells[c] ?? "")}
          </div>
        )}
      </>
    );
  };

  return (
    <div
      // overflow-hidden을 두지 않는다 — 선택 툴바가 선택 위/아래로 표 밖까지 떠야 해서다.
      // 가로 넘침은 안쪽 스크롤 div(overflow-x-auto)가 자르고, 코너는 bg-card라 티가 안 난다.
      className="border-border bg-card group/grid relative my-2 flex flex-col rounded-md border"
      data-testid="dataset-grid"
    >
      {/* 값 셀만(제목 행 없음). 가로는 열이 넘치면 스크롤하고(overflow-x-auto),
          세로는 뷰포트를 두지 않아 행이 늘수록 페이지 흐름대로 자란다.
          행/열 추가 버튼은 공간을 차지하지 않게 절대 위치로 띄우고 표 호버 시에만 보인다. */}
      <div className="overflow-x-auto">
        {/* 본문 (윈도우 가상화) */}
        <div
          ref={listRef}
          style={{ height: virtualizer.getTotalSize(), position: "relative" }}
        >
          {/* 열 선택 핸들 — 상단 얇은 바(열별). 표 호버 시 나타난다. 클릭=열 선택, 드래그=여러 열. */}
          <div
            className="pointer-events-none absolute top-0 left-0 z-20 grid h-1.5 w-full"
            style={{ gridTemplateColumns }}
          >
            {meta.columns.map((col, c) => (
              <div
                key={col.key}
                role="button"
                aria-label={`${c + 1}열 선택`}
                title="클릭·드래그해 열 선택"
                onPointerDown={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                  startColSelect(c);
                }}
                onPointerEnter={() => extendColSelect(c)}
                className={cn(
                  "hover:bg-primary/60 border-border pointer-events-auto cursor-pointer touch-none border-r opacity-0 transition-opacity group-hover/grid:opacity-100",
                  sel?.kind === "cols" &&
                    inSel(0, c) &&
                    "bg-primary/60 opacity-100",
                )}
              />
            ))}
          </div>
          {/* 표 전체 선택 코너 — 좌상단 작은 사각. */}
          <div
            role="button"
            aria-label="표 전체 선택"
            title="표 전체 선택"
            onPointerDown={(e) => {
              e.preventDefault();
              e.stopPropagation();
              setEditing(null);
              setPalette(null);
              selDrag.current = null;
              setSel({ kind: "table" });
            }}
            className={cn(
              "hover:bg-primary/60 absolute top-0 left-0 z-30 size-2 cursor-pointer opacity-0 transition-opacity group-hover/grid:opacity-100",
              sel?.kind === "table" && "bg-primary/60 opacity-100",
            )}
          />
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
                // 행 드래그 선택 중이면 지나가는 행까지 범위를 넓힌다(다른 드래그엔 무영향).
                onPointerEnter={() => extendRowSelect(vi.index)}
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
                      // 클릭=선택, 드래그=범위, shift+클릭=확장, 더블클릭=편집.
                      onPointerDown={(e) => {
                        if (e.button === 0)
                          startCellSelect(vi.index, c, e.shiftKey);
                      }}
                      onPointerEnter={() => extendCellSelect(vi.index, c)}
                      onDoubleClick={() => startEdit(vi.index, c)}
                      onContextMenu={(e) => openContextMenu(e, vi.index, c)}
                    >
                      {renderCellInner(vi.index, c)}
                      {/* 선택 하이라이트 — 셀 배경 위에 얹는 반투명 오버레이. */}
                      {inSel(vi.index, c) && (
                        <div className="bg-primary/15 pointer-events-none absolute inset-0 z-[5]" />
                      )}
                      {/* 열 너비 조절 — 셀 오른쪽 경계 드래그(제목 행이 없어 셀로 옮겼다).
                          셀 호버 시 나타난다. 가로 병합 앵커엔 경계가 모호해 달지 않는다. */}
                      {!anchor && (
                        <div
                          role="separator"
                          aria-orientation="vertical"
                          aria-label={`${c + 1}열 너비 조절`}
                          title="드래그해 너비 조절 · 더블클릭해 기본 폭으로"
                          onPointerDown={(e) =>
                            startResize(meta.columns[c].key, e)
                          }
                          onPointerMove={moveResize}
                          onPointerUp={endResize}
                          onPointerCancel={endResize}
                          onClick={(e) => e.stopPropagation()}
                          onDoubleClick={(e) => {
                            e.stopPropagation();
                            resetColWidthMut.mutate(meta.columns[c].key);
                          }}
                          className={cn(
                            "hover:bg-primary/60 absolute top-0 right-0 z-10 -mr-[3px] h-full w-[6px] cursor-col-resize touch-none opacity-0 group-hover/cell:opacity-100",
                            resizing?.key === meta.columns[c].key &&
                              "bg-primary/60 opacity-100",
                          )}
                        />
                      )}
                    </div>
                  );
                })}
                {/* 행 선택 핸들 — 좌측 얇은 바. 표 호버 시 나타난다. 클릭=행 선택,
                    드래그=여러 행. 셀 뒤(마지막 자식)에 둬 셀 nth-child를 밀지 않는다. */}
                <div
                  role="button"
                  aria-label={`${vi.index + 1}행 선택`}
                  title="클릭·드래그해 행 선택"
                  onPointerDown={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    startRowSelect(vi.index);
                  }}
                  className={cn(
                    "hover:bg-primary/60 absolute top-0 left-0 z-20 h-full w-1.5 cursor-pointer touch-none opacity-0 transition-opacity group-hover/grid:opacity-100",
                    sel?.kind === "rows" &&
                      inSel(vi.index, 0) &&
                      "bg-primary/60 opacity-100",
                  )}
                />
              </div>
            );
          })}

          {/* 세로·블록 병합(rowSpan>1) 오버레이 — 가상화가 앵커 행을 안 그려도 덮인 영역을 그린다.
              같은 gridTemplateColumns를 쓰는 grid에 열 배치로 x·폭을 맞추고(유연폭 대응),
              고정 행 높이라 세로는 top·height를 px로 계산한다(측정 불필요). */}
          <div
            className="pointer-events-none absolute top-0 left-0 grid w-full"
            style={{
              gridTemplateColumns,
              height: virtualizer.getTotalSize(),
            }}
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
                      onPointerDown={(e) => {
                        if (e.button === 0)
                          startCellSelect(m.rowIndex, ai, e.shiftKey);
                      }}
                      onDoubleClick={() => startEdit(m.rowIndex, ai)}
                      onContextMenu={(e) => openContextMenu(e, m.rowIndex, ai)}
                    >
                      {renderCellInner(m.rowIndex, ai)}
                      {inSel(m.rowIndex, ai) && (
                        <div className="bg-primary/15 pointer-events-none absolute inset-0 z-[5]" />
                      )}
                    </div>
                  </div>
                );
              })}
          </div>
        </div>
      </div>

      {/* 선택 위 플로팅 툴바 — 선택 범위(표/행/열/셀)에 맞는 옵션을 보여준다.
          세로는 선택을 따라붙는다: 기본은 선택 바로 위, 위 공간이 부족하면(표·뷰포트 상단)
          아래로 뒤집는다. 가로는 표 중앙 유지. */}
      {sel &&
        selRect &&
        (() => {
          const area =
            (selRect.r1 - selRect.r0 + 1) * (selRect.c1 - selRect.c0 + 1);
          const scopeLabel =
            sel.kind === "table"
              ? "표 전체"
              : sel.kind === "rows"
                ? `${selRect.r1 - selRect.r0 + 1}행`
                : sel.kind === "cols"
                  ? `${selRect.c1 - selRect.c0 + 1}열`
                  : `셀 ${area}개`;
          const divider = <div className="bg-border mx-0.5 h-5 w-px" />;
          // 선택 첫 행 위(뷰포트 기준)에 툴바가 들어갈 자리가 있으면 위, 없으면 아래.
          // scrollMargin=본문의 문서 오프셋, ROW_HEIGHT=고정 행 높이.
          const selTopViewport =
            scrollMargin + selRect.r0 * ROW_HEIGHT - window.scrollY;
          const above = selTopViewport >= 48;
          const anchorTop = above
            ? selRect.r0 * ROW_HEIGHT
            : (selRect.r1 + 1) * ROW_HEIGHT;
          return (
            <div
              role="toolbar"
              aria-label="선택 도구"
              onPointerDown={(e) => e.stopPropagation()}
              style={{
                top: anchorTop,
                transform: above
                  ? "translate(-50%, calc(-100% - 6px))"
                  : "translate(-50%, 6px)",
              }}
              className="border-border bg-popover absolute left-1/2 z-30 flex items-center gap-1 rounded-md border p-1 whitespace-nowrap shadow-md"
            >
              <span className="text-muted-foreground px-1 text-xs whitespace-nowrap">
                {scopeLabel}
              </span>
              {divider}
              {/* 배경색 — 모든 선택 공통 */}
              {CELL_BG_TOKENS.map((token) => (
                <button
                  key={token}
                  type="button"
                  aria-label={`배경색 ${token}`}
                  onClick={() => applyBgSel(token)}
                  className="border-border size-4 rounded-full border"
                  style={{ background: `var(--cell-bg-${token})` }}
                />
              ))}
              <button
                type="button"
                aria-label="배경색 지우기"
                onClick={() => applyBgSel(null)}
                className="text-muted-foreground hover:text-foreground border-border flex size-5 items-center justify-center rounded border"
              >
                <X className="size-3" />
              </button>
              {divider}
              {/* 정렬 */}
              {ALIGN_ORDER.map((value) => {
                const Icon = ALIGN_ICONS[value];
                return (
                  <button
                    key={value}
                    type="button"
                    aria-label={`정렬 ${ALIGN_LABELS[value]}`}
                    onClick={() => applyAlignSel(value)}
                    className="text-muted-foreground hover:bg-accent hover:text-foreground flex size-5 items-center justify-center rounded"
                  >
                    <Icon className="size-3.5" />
                  </button>
                );
              })}
              {/* 서식 지우기 */}
              <button
                type="button"
                aria-label="서식 지우기"
                title="배경·정렬 초기화"
                onClick={clearFormatSel}
                className="text-muted-foreground hover:bg-accent hover:text-foreground flex size-5 items-center justify-center rounded"
              >
                <Eraser className="size-3.5" />
              </button>
              {/* 병합 — 셀 사각 2칸 이상 */}
              {sel.kind === "cells" && area > 1 && (
                <button
                  type="button"
                  aria-label="병합"
                  title="선택 범위 병합"
                  onClick={mergeSel}
                  className="text-muted-foreground hover:bg-accent hover:text-foreground flex size-5 items-center justify-center rounded"
                >
                  <TableCellsMerge className="size-3.5" />
                </button>
              )}
              {/* 행 옵션 */}
              {sel.kind === "rows" && (
                <>
                  {divider}
                  <button
                    type="button"
                    onClick={() => addRow(selRect.r0)}
                    className="hover:bg-accent rounded px-1.5 py-0.5 text-xs whitespace-nowrap"
                  >
                    위 삽입
                  </button>
                  <button
                    type="button"
                    onClick={() => addRow(selRect.r1 + 1)}
                    className="hover:bg-accent rounded px-1.5 py-0.5 text-xs whitespace-nowrap"
                  >
                    아래 삽입
                  </button>
                  <button
                    type="button"
                    aria-label="행 삭제"
                    onClick={() => void deleteRowsSel()}
                    className="text-destructive hover:bg-accent flex size-5 items-center justify-center rounded"
                  >
                    <Trash2 className="size-3.5" />
                  </button>
                </>
              )}
              {/* 열 옵션 */}
              {sel.kind === "cols" && (
                <>
                  {divider}
                  <button
                    type="button"
                    onClick={() => addColumn(selRect.c0)}
                    className="hover:bg-accent rounded px-1.5 py-0.5 text-xs whitespace-nowrap"
                  >
                    왼쪽 삽입
                  </button>
                  <button
                    type="button"
                    onClick={() => addColumn(selRect.c1 + 1)}
                    className="hover:bg-accent rounded px-1.5 py-0.5 text-xs whitespace-nowrap"
                  >
                    오른쪽 삽입
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      for (let c = selRect.c0; c <= selRect.c1; c++)
                        resetColWidthMut.mutate(meta.columns[c].key);
                    }}
                    className="hover:bg-accent rounded px-1.5 py-0.5 text-xs whitespace-nowrap"
                  >
                    너비 초기화
                  </button>
                  {colCount > selRect.c1 - selRect.c0 + 1 && (
                    <button
                      type="button"
                      aria-label="열 삭제"
                      onClick={() => void deleteColsSel()}
                      className="text-destructive hover:bg-accent flex size-5 items-center justify-center rounded"
                    >
                      <Trash2 className="size-3.5" />
                    </button>
                  )}
                </>
              )}
            </div>
          );
        })()}

      {/* 행/열 추가 — notion식 가장자리 바. 공간을 차지하지 않게 절대 위치로 두고
          평소엔 숨겼다가 표 호버(또는 포커스) 시 나타난다. 둘 다 아이콘만(글자 없음):
          행=하단 가로 바, 열=우측 세로 바. 같은 스타일로 통일. */}
      <button
        type="button"
        aria-label="행 추가"
        title="행 추가"
        onClick={() => addRow()}
        disabled={insertMut.isPending}
        className="bg-muted/60 text-muted-foreground hover:bg-muted hover:text-foreground absolute inset-x-0 bottom-0 z-20 flex h-5 items-center justify-center opacity-0 transition-opacity group-hover/grid:opacity-100 focus-visible:opacity-100 disabled:opacity-50"
      >
        <Plus className="size-3.5" />
      </button>
      <button
        type="button"
        aria-label="열 추가"
        title="열 추가"
        onClick={() => addColumn()}
        disabled={addColMut.isPending}
        className="bg-muted/60 text-muted-foreground hover:bg-muted hover:text-foreground absolute inset-y-0 right-0 z-20 flex w-5 items-center justify-center opacity-0 transition-opacity group-hover/grid:opacity-100 focus-visible:opacity-100 disabled:opacity-50"
      >
        <Plus className="size-3.5" />
      </button>

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

/** 정렬 값 → 아이콘. 셀 정렬 팔레트와 렌더에 공용. */
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
