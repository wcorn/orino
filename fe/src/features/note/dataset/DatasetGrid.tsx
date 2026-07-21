import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useWindowVirtualizer } from "@tanstack/react-virtual";
import {
  AlignCenter,
  AlignLeft,
  AlignRight,
  AlignVerticalJustifyCenter,
  AlignVerticalJustifyEnd,
  AlignVerticalJustifyStart,
  Eraser,
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
  type CellValign,
  type DatasetMeta,
  deleteCellMerge,
  deleteDatasetColumn,
  deleteDatasetRow,
  fillCells,
  insertDatasetRow,
  MAX_COLUMN_WIDTH,
  type MergeSpan,
  type MergeView,
  MIN_COLUMN_WIDTH,
  resetDatasetColumnWidth,
  resizeDatasetColumn,
  setCellMerge,
  setCellStylesBulk,
  updateDatasetRow,
} from "./api/datasets";
import { DATASET_CELLS_MIME } from "./cellClipboard";
import type { FormulaContext, ValueSource } from "./formula";
import { evaluateFormula } from "./formula";
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
  /** 표 블록(노드) 자체를 문서에서 제거한다. 우클릭 메뉴의 '표 삭제'에서 쓴다. */
  onDeleteBlock?: () => void;
  /** 에디터에서 표 블록이 선택된 상태(NodeSelection)인지. 첫 셀 자동 선택 트리거. */
  blockSelected?: boolean;
}

/**
 * notion 스타일 값-중심 표. 제목(헤더) 행 없이 값 셀만 그린다 + 윈도우 가상화(행) +
 * 지연 로드 + 셀 편집. 세로는 노트 페이지 흐름을 따라 무한히 자라고(자체 스크롤 뷰포트 없음),
 * 가로는 열이 화면을 넘으면 스크롤한다. 열 너비는 셀 오른쪽 경계 드래그로 조절한다.
 */
export function DatasetGrid({
  datasetId,
  onDeleteBlock,
  blockSelected,
}: Props) {
  const queryClient = useQueryClient();
  const { data: meta, isLoading, isError, refetch } = useDatasetMeta(datasetId);
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
  // 표 컨테이너 — 셀을 한 번 클릭하면 여기로 포커스를 옮겨(ProseMirror 대신) 키 입력을 직접 받는다.
  // 이래야 선택된 셀 위에서 글자를 누르면 편집이 시작되고, 표를 지우는 등의 에디터 단축키가 안 튄다.
  const gridBoxRef = useRef<HTMLDivElement>(null);
  // 활성 셀 입력창 — 셀을 클릭하면 여기로 명시적으로 포커스를 옮긴다. autoFocus만으론
  // 실제 클릭 시 ProseMirror가 포커스를 도로 가져가(글자가 표 위 문단에 쳐짐), 클릭이
  // 끝난 뒤(useEffect) 명시적 focus로 확실히 잡아야 한다.
  const activeInputRef = useRef<HTMLInputElement>(null);
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
  // 채우기 핸들 드래그 중인 대상 행(세로). null이면 채우기 중 아님. window pointerup에서 확정한다.
  const [fillTo, setFillTo] = useState<number | null>(null);
  const fillDragging = useRef(false);
  // 최신 commitFill을 담아 둔다 — window pointerup(한 번만 등록)이 stale 클로저 없이 부른다.
  const commitFillRef = useRef<() => void>(() => {});

  const colCount = meta?.columns.length ?? 0;
  const rowCount = meta?.rowCount ?? 0;

  const invalidateMeta = () =>
    queryClient.invalidateQueries({ queryKey: datasetKeys.meta(datasetId) });
  // 행/열 구조가 바뀌면 병합의 행 번호가 밀리거나(행 삽입·삭제) 병합이 해제될 수 있어(열 삭제·순서변경)
  // 병합을 다시 받는다.
  const invalidateMerges = () =>
    queryClient.invalidateQueries({ queryKey: datasetKeys.merges(datasetId) });

  // 행별 저장 버전. 저장을 보낼 때마다 올리고, 응답이 오면 그 사이 같은 행을 또 고쳤는지
  // (버전 불일치) 확인한다. 오래된 응답이 최신 편집을 덮어써 깜빡이던 문제를 없앤다.
  const rowVersion = useRef<Map<number, number>>(new Map());
  // 편집 중 자동저장 디바운스 타이머. 엔터/blur 없이도 타이핑이 멎으면 저장한다.
  const autoSaveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  // 아직 저장 안 한 최신 편집값(셀·값). onChange가 동기로 갱신하고, blur/Enter가 이걸 flush한다.
  // (setEditing/setDraft 상태는 blur 시점에 아직 반영 전일 수 있어 stale — ref로 확실히 잡는다.)
  const pendingEdit = useRef<{
    row: number;
    col: number;
    value: string;
  } | null>(null);
  /**
   * 편집 저장 — 낙관적 반영은 호출 전에 하고, 여기선 백그라운드로 PATCH만 보낸다.
   * useMutation을 안 써 매 저장마다 그리드가 리렌더되지 않는다(연속 편집이 매끄럽게).
   * 성공: 최신 편집일 때만 서버 계산값·수식으로 맞춘다(수식 전파 반영). 오래된 응답은 무시.
   * 실패: 전체 리셋 대신 이 행만 직전 상태로 되돌리고 사유를 토스트한다.
   */
  const saveRow = (
    index: number,
    cells: string[],
    prev: { cells: string[]; formulas: Record<string, string> },
    // 타이핑 중 자동저장이면 true — 실패해도 토스트·되돌림을 하지 않는다(미완성 수식 등은
    // 조용히 넘기고, 최종 커밋(엔터·blur)에서 정식으로 처리한다).
    silent = false,
  ) => {
    const version = (rowVersion.current.get(index) ?? 0) + 1;
    rowVersion.current.set(index, version);
    updateDatasetRow(datasetId, index, cells)
      .then((res) => {
        if (rowVersion.current.get(index) !== version) return;
        // 편집 행 + 전파로 값이 바뀐 교차 행(집계 등)을 서버 확정값으로 맞춘다. affected 행은
        // 로드 범위 밖일 수 있지만 setRowLocal은 인덱스 단위라 보이는 행만 실제로 갱신된다.
        for (const row of [res.edited, ...res.affected]) {
          setRowLocal(row.rowIndex, row.cells);
          setFormulasLocal(row.rowIndex, row.formulas ?? {});
        }
      })
      .catch((e) => {
        if (silent) return;
        // 수식 문법 오류·순환 참조는 서버가 무엇이 틀렸는지 알려준다. 그대로 보여준다.
        const message = serverMessage(e);
        if (message) toast(message, "error");
        if (rowVersion.current.get(index) !== version) return;
        setRowLocal(index, prev.cells);
        setFormulasLocal(index, prev.formulas);
      });
  };
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
      // 채우기 핸들 드래그였다면 확정한다(대상 없으면 조용히 끝냄).
      if (fillDragging.current) commitFillRef.current();
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

  // 언마운트 시 예약된 자동저장 타이머를 정리한다(사라진 뒤 저장 시도 방지).
  useEffect(() => {
    return () => {
      if (autoSaveTimer.current) clearTimeout(autoSaveTimer.current);
    };
  }, []);

  // 셀을 선택하면 표 컨테이너로 포커스를 가져온다 — 그래야 키 입력이 에디터(ProseMirror)로
  // 새지 않고 여기서 처리돼, 글자를 누르면 편집이 시작되고 단축키가 표를 건드리지 않는다.
  // 편집 중엔 입력창이 포커스를 가지므로 건드리지 않는다.
  useEffect(() => {
    // 단일 셀 선택(또는 편집)일 땐 셀 입력창이 autoFocus로 포커스를 갖는다(한글 IME도
    // 첫 자모부터 그 입력창에서 조합). 그 외 선택(행/열/범위/표)만 컨테이너로 포커스를
    // 가져와 Delete·타이핑을 처리한다.
    const singleActive =
      !editing &&
      sel?.kind === "cells" &&
      sel.a[0] === sel.b[0] &&
      sel.a[1] === sel.b[1];
    if (editing || singleActive) {
      // 활성 셀 입력창으로 포커스를 확실히 가져온다(실제 클릭 시 ProseMirror가 도로
      // 가져가는 걸 이긴다). autoFocus만으론 못 잡히는 경우가 있어 명시적으로 한다.
      activeInputRef.current?.focus({ preventScroll: true });
      return;
    }
    if (sel) gridBoxRef.current?.focus({ preventScroll: true });
  }, [sel, editing]);

  // 표 블록이 선택되면(한 번 클릭·키보드 이동 등) 첫 셀을 자동으로 잡는다 → 이어서 키를 누르면
  // 그 글자로 곧바로 편집이 시작된다(블록만 선택돼 타이핑이 먹통이던 문제 해소). 이미 셀을 고른
  // 상태(범위·특정 셀)나 편집 중이면 건드리지 않는다.
  useEffect(() => {
    if (!blockSelected || sel || editing) return;
    if (rowCount < 1 || colCount < 1) return; // 메타 로드 전.
    const first = getRow(0);
    if (!first) return; // 첫 행 데이터 로드 전이면, 로드되며(getRow 갱신) 다시 시도한다.
    setSel({ kind: "cells", a: [0, 0], b: [0, 0] });
    setDraft(first[0] ?? "");
    // 의존성은 blockSelected(선택 진입)·로드 상태(rowCount/colCount/getRow)만 둔다. sel/editing을
    // 넣으면 Esc로 해제(sel→null)할 때 다시 첫 셀을 잡아 버리므로 뺀다(가드로만 읽는다).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [blockSelected, rowCount, colCount, getRow]);

  if (isLoading) return <LoadingText />;
  if (isError || !meta) {
    // 표를 못 불러오는 경우: 일시적 오류면 '다시 시도', 이미 삭제된 표(고아 블록)면
    // '표 블록 제거'로 대응한다. (되돌리기로 되살아난 삭제된 표 등)
    return (
      <div className="border-border bg-card my-2 flex flex-col gap-2 rounded-md border p-3">
        <FieldError>표를 불러오지 못했어요.</FieldError>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => void refetch()}
            className="border-border hover:bg-accent rounded-md border px-2 py-1 text-sm"
          >
            다시 시도
          </button>
          {onDeleteBlock && (
            <button
              type="button"
              onClick={onDeleteBlock}
              className="text-destructive border-border hover:bg-accent rounded-md border px-2 py-1 text-sm"
            >
              표 블록 제거
            </button>
          )}
        </div>
      </div>
    );
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

  // initial을 주면 그 값으로 편집을 연다(셀 선택 상태에서 글자를 눌러 바로 덮어쓰기 시작).
  // 안 주면 기존 값/수식을 보여준다(더블클릭·Enter로 이어 편집).
  const startEdit = (row: number, col: number, initial?: string) => {
    const cells = getRow(row);
    if (!cells) return;
    // 편집에 들어가면 선택·툴바는 접는다(더블클릭이 선택→편집 순으로 오므로).
    setSel(null);
    setEditing({ row, col });
    if (initial !== undefined) {
      setDraft(initial);
      return;
    }
    // 수식 셀을 고를 땐 계산된 값이 아니라 수식을 보여준다 — 값을 보여주면
    // 자기가 쓴 수식을 다시 볼 방법이 없다.
    setDraft(getFormulas(row)[meta.columns[col].key] ?? cells[col] ?? "");
  };

  /**
   * 편집을 낙관적으로 미리 계산한 표시 배열을 만든다(Epic #892 반응성). 서버 확정 전까지
   * 원본 {@code =…}가 보이지 않게, FE 경량 평가기로 편집 행의 수식을 그 자리서 계산한다.
   *
   * 범위는 편집 행 한 줄이다 — 편집 셀과 같은 행의 의존 수식만. 다른 행으로 번지는 집계는
   * 서버 응답의 affected 행이 채운다. 열 전체 참조는 로드된 행만 봐서 근사이며 서버가 확정한다.
   * 미완성·문법오류 수식은 계산을 건너뛰고 친 그대로 둔다(계속 타이핑 중일 수 있다).
   */
  const previewRow = (
    row: number,
    editedCol: number,
    value: string,
    cells: string[],
    rowFormulas: Record<string, string>,
  ): string[] => {
    const editedKey = meta.columns[editedCol].key;
    const editedIsFormula = value.startsWith("=");

    // 이 행의 수식 맵(편집 반영). 편집 셀이 수식이면 넣고, 리터럴이면 뺀다.
    const formulas: Record<string, string> = { ...rowFormulas };
    if (editedIsFormula) formulas[editedKey] = value;
    else delete formulas[editedKey];

    // 작업 중 표시값. 리터럴 편집은 즉시 반영, 수식 셀은 아래서 계산해 덮는다.
    const values = Array.from({ length: colCount }, (_, c) =>
      c === editedCol && !editedIsFormula ? value : (cells[c] ?? ""),
    );

    const ctx: FormulaContext = {
      columnKeys: () => meta.columns.map((c) => c.key),
      keyByLabel: (label) => meta.columns.find((c) => c.label === label)?.key,
      labelByKey: (key) => meta.columns.find((c) => c.key === key)?.label,
      // 행 번호(1-base) ↔ 행 인덱스(0-base)를 그대로 가상 id로 쓴다 — 미리보기 안에서만 일관되면 된다.
      rowIdByNumber: (n) => (n >= 1 && n <= rowCount ? n - 1 : undefined),
      rowNumberById: (id) => (id >= 0 && id < rowCount ? id + 1 : undefined),
    };
    // 현재 행은 작업 중 값(values)을, 다른 행은 캐시를 본다.
    const cellsOf = (r: number): string[] | undefined =>
      r === row ? values : getRow(r);
    const source: ValueSource = {
      sameRow: (colKey) => {
        const i = colOf(colKey);
        return i < 0 ? undefined : values[i];
      },
      absolute: (rowId, colKey) => {
        const i = colOf(colKey);
        return i < 0 ? undefined : cellsOf(rowId)?.[i];
      },
      column: (colKey) => {
        const i = colOf(colKey);
        if (i < 0) return undefined;
        const out: string[] = [];
        for (let r = 0; r < rowCount; r++) {
          const rc = cellsOf(r);
          if (rc) out.push(rc[i] ?? "");
        }
        return out;
      },
    };

    // 열 순서로 계산해 뒤 수식이 앞 결과를 보게 한다(서버의 인라인 패스와 같은 순서).
    for (let c = 0; c < colCount; c++) {
      const f = formulas[meta.columns[c].key];
      if (f === undefined) continue;
      try {
        values[c] = evaluateFormula(f, ctx, source);
      } catch {
        // 미완성/문법오류: 편집 셀은 친 그대로, 나머지는 기존 값 유지.
        if (c === editedCol) values[c] = value;
      }
    }
    return values;
  };

  /**
   * 한 셀의 편집값을 저장한다(다른 셀은 값·수식을 보존). editing 상태는 건드리지 않아,
   * 자동저장(타이핑 중)과 최종 커밋이 같은 로직을 쓴다. silent면 실패해도 조용히 넘긴다.
   */
  const persistCellEdit = (
    row: number,
    col: number,
    value: string,
    silent = false,
  ) => {
    const cells = getRow(row);
    if (!cells) return;
    const rowFormulas = getFormulas(row);
    // 서버로는 수식 셀에 원본 수식을 돌려준다 — 계산된 값을 돌려주면 서버가
    // 사용자가 직접 입력한 것으로 보고 수식을 지운다.
    const sent = Array.from({ length: colCount }, (_, c) =>
      c === col ? value : (rowFormulas[meta.columns[c].key] ?? cells[c] ?? ""),
    );
    // 화면엔 낙관적으로 계산한 값을 보여준다(수식 원본이 잠깐 보이면 안 된다).
    const shown = previewRow(row, col, value, cells, rowFormulas);
    setRowLocal(row, shown);
    saveRow(row, sent, { cells, formulas: rowFormulas }, silent);
  };

  /** 예약된 자동저장을 취소한다(커밋·해제 직전에 중복 저장을 막는다). */
  const cancelAutoSave = () => {
    if (autoSaveTimer.current) clearTimeout(autoSaveTimer.current);
    autoSaveTimer.current = null;
  };

  /** 아직 저장 안 한 편집값이 있으면 저장한다(ref 기반이라 stale 상태 영향 없음). */
  const flushPendingEdit = (silent: boolean) => {
    const p = pendingEdit.current;
    if (!p) return;
    persistCellEdit(p.row, p.col, p.value, silent);
  };

  /** 편집 확정(Enter·blur) — 최신 값을 즉시 저장하고 편집을 닫는다. */
  const commitEdit = () => {
    cancelAutoSave();
    flushPendingEdit(false);
    pendingEdit.current = null;
    setEditing(null);
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
  };

  /** 병합 해제. 덮여 있던 셀 값이 그 자리에 되살아난다. */
  const unmerge = (row: number, col: number) => {
    unmergeMut.mutate({ row, colKey: meta.columns[col].key });
  };

  /**
   * 셀 우클릭 → 그 자리에 컨텍스트 메뉴를 연다(선택 범위에 맞춰 서식·병합·행/열 옵션을 모두 담는다).
   * 클릭한 셀이 현재 선택 밖이면 그 셀을 단일 선택으로 잡고(엑셀식), 선택 안이면 선택을 유지한다.
   */
  const openContextMenu = (
    event: React.MouseEvent,
    row: number,
    col: number,
  ) => {
    event.preventDefault();
    if (!inSel(row, col)) {
      setEditing(null);
      selDrag.current = null;
      setSel({ kind: "cells", a: [row, col], b: [row, col] });
    }
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
    if (shift && sel?.kind === "cells") {
      setSel({ kind: "cells", a: sel.a, b: [row, col] });
    } else {
      selDrag.current = "cells";
      setSel({ kind: "cells", a: [row, col], b: [row, col] });
      // 단일 선택 즉시 타이핑 대비: 셀 표시값으로 draft를 채워 둔다(입력창이 값을 전체
      // 선택한 채 뜨므로, 글자를 치면 덮어써지고 한글은 그 입력창에서 조합된다).
      setDraft(getRow(row)?.[col] ?? "");
    }
  };

  /**
   * 활성 셀 입력창의 키 처리. 선택만 한 상태에서 Enter/F2는 기존 값을 이어 편집,
   * Delete/Backspace는 셀을 즉시 비운다. 글자/IME는 input 기본 동작(전체선택 덮어쓰기·조합)에
   * 맡긴다. 어떤 키든 상위 에디터로 전파를 막아 단축키(표 삭제 등)가 튀지 않게 한다.
   */
  const onCellInputKeyDown = (
    e: React.KeyboardEvent<HTMLInputElement>,
    r: number,
    c: number,
  ) => {
    e.stopPropagation();
    // 선택-만-한 상태에선 방향키가 셀 이동, Cmd/Ctrl+A가 표 전체 선택이다(편집 중엔 입력창
    // 기본 동작 유지 — 커서 이동·텍스트 전체선택).
    if (!editing && handleNavKey(e)) return;
    if (e.key === "Escape") {
      e.preventDefault();
      // 이미 자동저장된 값은 남지만, 예약된 저장·미저장 편집은 버리고 편집을 닫는다.
      cancelAutoSave();
      pendingEdit.current = null;
      setEditing(null);
      setSel(null);
      return;
    }
    if (e.key === "Enter" || e.key === "F2") {
      e.preventDefault();
      if (editing) {
        commitEdit();
        return;
      }
      // 선택만 한 상태 → 덮어쓰기가 아니라 기존 값/수식을 이어 편집한다.
      setEditing({ row: r, col: c });
      setDraft(getFormulas(r)[meta.columns[c].key] ?? getRow(r)?.[c] ?? "");
      return;
    }
    if (!editing && (e.key === "Delete" || e.key === "Backspace")) {
      e.preventDefault();
      clearSelValues();
      setDraft("");
    }
  };
  const extendCellSelect = (row: number, col: number) => {
    if (selDrag.current !== "cells") return;
    setSel((s) => (s?.kind === "cells" ? { ...s, b: [row, col] } : s));
  };
  const startRowSelect = (row: number, shift = false) => {
    setEditing(null);
    // shift+클릭: 기존 행 선택의 앵커를 유지하고 여기까지 확장(셀 범위 shift 확장과 같은 결).
    if (shift && sel?.kind === "rows") {
      setSel({ kind: "rows", a: sel.a, b: row });
      return;
    }
    selDrag.current = "rows";
    setSel({ kind: "rows", a: row, b: row });
  };
  const extendRowSelect = (row: number) => {
    if (selDrag.current !== "rows") return;
    setSel((s) => (s?.kind === "rows" ? { ...s, b: row } : s));
  };
  const startColSelect = (col: number, shift = false) => {
    setEditing(null);
    if (shift && sel?.kind === "cols") {
      setSel({ kind: "cols", a: sel.a, b: col });
      return;
    }
    selDrag.current = "cols";
    setSel({ kind: "cols", a: col, b: col });
  };
  const extendColSelect = (col: number) => {
    if (selDrag.current !== "cols") return;
    setSel((s) => (s?.kind === "cols" ? { ...s, b: col } : s));
  };

  /** 지금 키 입력이 향할 셀 — 셀 선택이면 포커스 지점(sel.b), 아니면 선택 사각의 좌상단. */
  const activeCell = ((): [number, number] | null => {
    if (!selRect) return null;
    if (sel?.kind === "cells") return sel.b;
    return [selRect.r0, selRect.c0];
  })();

  /**
   * 활성 셀을 (dr,dc)만큼 옮긴다(경계 안으로 클램프). extend면 셀 범위를 확장한다(앵커 유지,
   * 셀 선택이 아니었으면 현재 활성점을 앵커로 새 범위 시작). 가상화로 렌더 밖 행이면 스크롤해
   * 들여, 옮긴 셀의 입력창이 렌더·포커스되게 한다(포커스 이동은 sel 변경 effect가 맡는다).
   */
  const moveActive = (dr: number, dc: number, extend: boolean) => {
    if (!activeCell || rowCount < 1 || colCount < 1) return;
    const [r, c] = activeCell;
    const nr = Math.min(rowCount - 1, Math.max(0, r + dr));
    const nc = Math.min(colCount - 1, Math.max(0, c + dc));
    if (nr === r && nc === c && !extend) return; // 경계에서 제자리면 그대로.
    setEditing(null);
    if (extend) {
      setSel((s) =>
        s?.kind === "cells"
          ? { kind: "cells", a: s.a, b: [nr, nc] }
          : { kind: "cells", a: [r, c], b: [nr, nc] },
      );
    } else {
      setSel({ kind: "cells", a: [nr, nc], b: [nr, nc] });
      setDraft(getRow(nr)?.[nc] ?? "");
    }
    virtualizer.scrollToIndex(nr);
  };

  /**
   * 방향키 이동·Ctrl/Cmd+A(표 전체)를 처리한다. 처리했으면 true.
   * 편집 중이 아닐 때(선택-만-한 상태)만 호출해야 한다 — 편집 중엔 입력창 기본 동작
   * (커서 이동·텍스트 전체선택)을 그대로 둔다.
   */
  const handleNavKey = (e: React.KeyboardEvent): boolean => {
    if ((e.metaKey || e.ctrlKey) && (e.key === "a" || e.key === "A")) {
      e.preventDefault();
      setEditing(null);
      setSel({ kind: "table" });
      return true;
    }
    const d: [number, number] | null =
      e.key === "ArrowUp"
        ? [-1, 0]
        : e.key === "ArrowDown"
          ? [1, 0]
          : e.key === "ArrowLeft"
            ? [0, -1]
            : e.key === "ArrowRight"
              ? [0, 1]
              : null;
    if (d && !e.metaKey && !e.ctrlKey && !e.altKey) {
      e.preventDefault();
      moveActive(d[0], d[1], e.shiftKey);
      return true;
    }
    return false;
  };

  /**
   * 채우기 대상 행 범위(세로). 핸들을 소스 아래로 끌면 소스 다음 행부터, 위로 끌면 소스 앞
   * 행까지. 소스 안이면 null(채울 게 없음).
   */
  const fillTargetRows = ((): { from: number; to: number } | null => {
    if (fillTo === null || !selRect) return null;
    if (fillTo > selRect.r1) return { from: selRect.r1 + 1, to: fillTo };
    if (fillTo < selRect.r0) return { from: fillTo, to: selRect.r0 - 1 };
    return null;
  })();

  /** 채우기 프리뷰(드래그로 정해진 대상)에 든 셀인지 — 소스 열 범위 안이고 대상 행 범위 안. */
  const inFillTarget = (row: number, col: number): boolean =>
    !!fillTargetRows &&
    !!selRect &&
    row >= fillTargetRows.from &&
    row <= fillTargetRows.to &&
    col >= selRect.c0 &&
    col <= selRect.c1;

  /** 채우기 핸들을 눌렀다 — 셀 위로 포인터가 지나가면 {@link updateFill}이 대상 행을 정한다. */
  const startFill = (e: React.PointerEvent) => {
    e.preventDefault();
    e.stopPropagation();
    fillDragging.current = true;
    setFillTo(null);
  };

  /** 채우기 드래그 중 포인터가 지나간 행을 대상으로 잡는다. */
  const updateFill = (row: number) => {
    if (fillDragging.current) setFillTo(row);
  };

  /**
   * 채우기 확정(핸들 놓음) — 소스 블록을 대상 행들에 채우고, 응답 행들로 캐시를 맞춘다.
   * 채운 뒤 소스+대상을 감싸는 범위를 선택으로 남긴다(엑셀식). 대상이 없으면 조용히 끝낸다.
   */
  const commitFill = () => {
    const target = fillTargetRows;
    fillDragging.current = false;
    setFillTo(null);
    if (!selRect || !target) return;
    const cols: string[] = [];
    for (let c = selRect.c0; c <= selRect.c1; c++)
      cols.push(meta.columns[c].key);
    const { r0, c0, c1 } = selRect;
    fillCells(datasetId, {
      cols,
      srcR0: r0,
      srcR1: selRect.r1,
      dstR0: target.from,
      dstR1: target.to,
    })
      .then((rows) => {
        for (const row of rows) {
          setRowLocal(row.rowIndex, row.cells);
          setFormulasLocal(row.rowIndex, row.formulas ?? {});
        }
        setSel({
          kind: "cells",
          a: [Math.min(r0, target.from), c0],
          b: [Math.max(selRect.r1, target.to), c1],
        });
      })
      .catch((e) => {
        const message = serverMessage(e);
        if (message) toast(message, "error");
      });
  };
  // window pointerup이 최신 commitFill을 부르도록 매 렌더 동기화한다.
  commitFillRef.current = commitFill;

  /** 선택 범위의 셀 값을 모두 비운다(Delete/Backspace). 수식·값은 지우고 서식은 둔다. */
  const clearSelValues = () => {
    if (!selRect) return;
    for (let r = selRect.r0; r <= selRect.r1; r++) {
      const cells = getRow(r);
      if (!cells) continue;
      const rowFormulas = getFormulas(r);
      const inCol = (c: number) => c >= selRect.c0 && c <= selRect.c1;
      const sent = Array.from({ length: colCount }, (_, c) =>
        inCol(c) ? "" : (rowFormulas[meta.columns[c].key] ?? cells[c] ?? ""),
      );
      const shown = Array.from({ length: colCount }, (_, c) =>
        inCol(c) ? "" : (cells[c] ?? ""),
      );
      // 이미 빈 행이면 헛요청을 보내지 않는다.
      if (shown.every((v, c) => v === (cells[c] ?? ""))) continue;
      setRowLocal(r, shown);
      saveRow(r, sent, { cells, formulas: rowFormulas });
    }
  };

  /** 선택 범위를 TSV(탭 구분·줄바꿈)로 만든다 — 엑셀·구글시트와 호환되는 클립보드 형식. */
  const selectionToTSV = (): string => {
    if (!selRect) return "";
    const lines: string[] = [];
    for (let r = selRect.r0; r <= selRect.r1; r++) {
      const cells = getRow(r);
      const cols: string[] = [];
      for (let c = selRect.c0; c <= selRect.c1; c++)
        cols.push(cells?.[c] ?? "");
      lines.push(cols.join("\t"));
    }
    return lines.join("\n");
  };

  /** 선택 범위 복사(Cmd/Ctrl+C). 편집 중엔 입력창 기본 복사에 맡긴다. */
  const onGridCopy = (e: React.ClipboardEvent) => {
    if (editing || !selRect) return;
    e.preventDefault();
    const tsv = selectionToTSV();
    e.clipboardData.setData("text/plain", tsv);
    // 표식도 함께 담는다 — 노트 본문에 붙여넣으면 이 조각으로 새 표를 만든다.
    e.clipboardData.setData(DATASET_CELLS_MIME, tsv);
  };

  /** 잘라내기(Cmd/Ctrl+X) — 복사 후 선택 값을 비운다. */
  const onGridCut = (e: React.ClipboardEvent) => {
    if (editing || !selRect) return;
    e.preventDefault();
    const tsv = selectionToTSV();
    e.clipboardData.setData("text/plain", tsv);
    e.clipboardData.setData(DATASET_CELLS_MIME, tsv);
    clearSelValues();
  };

  /**
   * 붙여넣기(Cmd/Ctrl+V) — 클립보드 TSV를 선택 좌상단부터 채운다(엑셀식). 표 밖으로 넘치는
   * 만큼은 자른다(행·열 자동 추가는 하지 않는다). 편집 중엔 입력창 기본 붙여넣기에 맡긴다.
   */
  const onGridPaste = (e: React.ClipboardEvent) => {
    if (editing || !selRect) return;
    const text = e.clipboardData.getData("text/plain");
    if (!text) return;
    e.preventDefault();
    const grid = text
      .replace(/\r\n?/g, "\n")
      .replace(/\n$/, "")
      .split("\n")
      .map((line) => line.split("\t"));
    const baseR = selRect.r0;
    const baseC = selRect.c0;
    let lastR = baseR;
    let lastC = baseC;
    for (let i = 0; i < grid.length; i++) {
      const r = baseR + i;
      if (r >= rowCount) break; // 표 아래로 넘치면 자른다.
      const cells = getRow(r);
      if (!cells) continue;
      const rowFormulas = getFormulas(r);
      const next = Array.from({ length: colCount }, (_, c) => {
        const pc = c - baseC;
        if (pc >= 0 && pc < grid[i].length) {
          lastR = Math.max(lastR, r);
          lastC = Math.max(lastC, c);
          return grid[i][pc];
        }
        // 붙여넣기 밖 셀은 값·수식을 그대로 보존한다.
        return rowFormulas[meta.columns[c].key] ?? cells[c] ?? "";
      });
      setRowLocal(r, next);
      saveRow(r, next, { cells, formulas: rowFormulas });
    }
    // 붙여넣은 범위를 선택으로 표시한다(엑셀식). 1칸이면 활성 입력창 값도 맞춘다.
    setEditing(null);
    setDraft(grid[0]?.[0] ?? "");
    setSel({ kind: "cells", a: [baseR, baseC], b: [lastR, lastC] });
  };

  /**
   * 표에 포커스가 있을 때의 키 처리. 선택된 셀 위에서 글자를 누르면 그 글자로 편집을 시작하고,
   * Enter/F2는 기존 값을 이어 편집, Delete/Backspace는 선택 셀을 비운다. 처리한 키는
   * 에디터(ProseMirror)로 새지 않게 막아, 표를 지우는 등의 단축키가 튀지 않게 한다.
   */
  const onGridKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (editing) return; // 편집 중엔 입력창이 처리한다.
    // 방향키 이동·Cmd/Ctrl+A(표 전체)를 먼저 처리한다.
    if (handleNavKey(e)) {
      e.stopPropagation();
      return;
    }
    if (!activeCell) return;
    const [r, c] = activeCell;
    // 한글 등 IME 조합키는 e.key가 "Process"로 오고 첫 자모를 keydown에서 얻을 수 없다.
    // 조합키/Enter/F2는 빈 편집으로 열어 입력창(진짜 <input>)에서 이어 조합하게 한다.
    if (e.key === "Enter" || e.key === "F2" || e.key === "Process") {
      e.preventDefault();
      e.stopPropagation();
      startEdit(r, c, e.key === "Process" ? "" : undefined);
      return;
    }
    if (e.key === "Backspace" || e.key === "Delete") {
      e.preventDefault();
      e.stopPropagation();
      clearSelValues();
      return;
    }
    // 순수 문자/숫자/기호 한 글자(수정키 없음) → 그 글자로 바로 편집 시작.
    if (
      e.key.length === 1 &&
      !e.ctrlKey &&
      !e.metaKey &&
      !e.altKey &&
      !e.nativeEvent.isComposing
    ) {
      e.preventDefault();
      e.stopPropagation();
      startEdit(r, c, e.key);
    }
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
    const cells = selCellRefs().map(({ row, colKey }) => {
      const cur = getStyles(row)[colKey];
      return {
        rowIndex: row,
        colKey,
        style: { bg: bg ?? undefined, align: cur?.align, valign: cur?.valign },
      };
    });
    if (cells.length) bulkStyleMut.mutate(cells);
  };
  const applyAlignSel = (align: CellAlign | null) => {
    const cells = selCellRefs().map(({ row, colKey }) => {
      const cur = getStyles(row)[colKey];
      return {
        rowIndex: row,
        colKey,
        style: { bg: cur?.bg, align: align ?? undefined, valign: cur?.valign },
      };
    });
    if (cells.length) bulkStyleMut.mutate(cells);
  };
  const applyValignSel = (valign: CellValign | null) => {
    const cells = selCellRefs().map(({ row, colKey }) => {
      const cur = getStyles(row)[colKey];
      return {
        rowIndex: row,
        colKey,
        style: { bg: cur?.bg, align: cur?.align, valign: valign ?? undefined },
      };
    });
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
  /** 선택이 걸친 행들을 뒤 인덱스부터 순서대로 지운다(인덱스가 밀리지 않게). */
  const deleteRowsSel = async () => {
    if (!selRect) return;
    for (let r = selRect.r1; r >= selRect.r0; r--) {
      await deleteDatasetRow(datasetId, r);
    }
    reset();
    void invalidateMeta();
    void invalidateMerges();
    setSel(null);
  };
  /** 선택이 걸친 열들을 지운다(key 기준이라 순서 무관). 최소 한 열은 남긴다. */
  const deleteColsSel = async () => {
    if (!selRect) return;
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
    // 단일 셀만 선택된 상태(범위/행/열/표 아님)이고 이 셀이 그 셀이면 "활성"이다.
    // 활성 셀엔 편집 전에도 입력창을 띄워 두고(값 전체선택), 글자를 치면 그대로 편집으로
    // 넘어간다 — 같은 <input>이라 IME 조합이 끊기지 않는다.
    const isSelectActive =
      !editing &&
      sel?.kind === "cells" &&
      sel.a[0] === sel.b[0] &&
      sel.a[1] === sel.b[1] &&
      sel.a[0] === rowIndex &&
      sel.a[1] === c;
    const isActive = isEditing || isSelectActive;
    const cells = getRow(rowIndex);
    const formula = getFormulas(rowIndex)[colKey];
    const style = getStyles(rowIndex)[colKey];
    // 셀 정렬(override) > 열 기본 정렬 > 기본(left) (#828 D2).
    const align = style?.align ?? meta.columns[c].align ?? "left";
    // 세로 정렬은 셀 서식에만 있다. 없으면 위(top).
    const valign = style?.valign ?? "top";
    return (
      <>
        {/* 표시값은 항상 렌더한다(레이아웃·테스트 기준). 활성 셀이면 그 위에 입력창이 겹친다.
          셀 서식(배경색·정렬)은 우클릭 메뉴로 통일했다(셀별 팔레트 버튼 제거).
          기본(top)은 truncate 블록 그대로(말줄임 유지). 가운데/아래일 때만 세로 flex로 내려
          justify-*로 위치를 잡는다 — 세로 병합된 큰 셀에서 유용하다. */}
        <div
          className={cn(
            "h-full cursor-text px-2 py-1.5",
            valign === "top"
              ? "truncate"
              : cn(
                  "flex flex-col overflow-hidden whitespace-nowrap",
                  VALIGN_CLASS[valign],
                ),
            ALIGN_CLASS[align],
            cells === undefined && "text-muted-foreground/40",
            isCellError(cells?.[c]) && "text-destructive",
          )}
          title={formula}
        >
          {cells === undefined ? "…" : (cells[c] ?? "")}
        </div>
        {isActive && (
          <input
            ref={activeInputRef}
            autoFocus
            value={draft}
            // 선택만 한 상태(편집 전)에선 값을 통째로 선택해 둔다 — 글자를 치면 덮어써지고,
            // 한글도 첫 자모부터 이 입력창에서 조합된다(엘리먼트 교체가 없어 조합이 안 끊긴다).
            onFocus={(e) => {
              if (!isEditing) e.currentTarget.select();
            }}
            onChange={(e) => {
              const value = e.target.value;
              setDraft(value);
              // 첫 입력이 들어오면 편집 상태로 전환(같은 input이라 IME 조합 유지).
              if (!isEditing) setEditing({ row: rowIndex, col: c });
              // 최신 편집값을 ref에 동기로 기록 — blur/Enter/자동저장이 이걸 저장한다.
              pendingEdit.current = { row: rowIndex, col: c, value };
              // 엔터/blur 없이도 타이핑이 잠깐 멎으면 자동저장한다(계속 저장).
              if (autoSaveTimer.current) clearTimeout(autoSaveTimer.current);
              autoSaveTimer.current = setTimeout(
                () => flushPendingEdit(true),
                500,
              );
            }}
            onBlur={() => commitEdit()}
            onKeyDown={(e) => onCellInputKeyDown(e, rowIndex, c)}
            // 편집 상태에서만 편집용 라벨을 붙인다(선택-만-한 상태는 편집이 아니므로 구분).
            aria-label={
              isEditing
                ? `셀 ${rowIndex + 1}행 ${c + 1}열`
                : `${rowIndex + 1}행 ${c + 1}열 셀 (입력하면 편집)`
            }
            className={cn(
              // 활성(선택/편집) 셀은 얇은 안쪽 테두리로 강조해 어느 칸이 선택됐는지 보이게 한다.
              "ring-primary absolute inset-0 z-[6] h-full w-full px-2 py-1.5 ring-1 outline-none ring-inset",
              ALIGN_CLASS[align],
              // 뒤 표시값을 가리도록 셀 배경색(없으면 카드색)으로 불투명하게 덮는다.
              !style?.bg && "bg-card",
            )}
            style={
              style?.bg
                ? { background: `var(--cell-bg-${style.bg})` }
                : undefined
            }
          />
        )}
      </>
    );
  };

  return (
    // 바깥 래퍼: 표 아래·오른쪽에 얇은 여백(gutter)을 확보해 행/열 추가 바를 표 "밖"에 둔다.
    // → 추가 바가 가장자리 셀을 가리지 않고, 그 자리(아래/오른쪽)에 호버할 때만 각각 나타난다.
    <div className="relative my-2 pr-5 pb-5">
      <div
        ref={gridBoxRef}
        // 셀 선택 시 이 컨테이너로 포커스가 와 키 입력을 직접 받는다(에디터로 새지 않게).
        // tabIndex=-1이라 탭 순회엔 안 잡히고, outline은 표 테두리로 충분해 숨긴다.
        tabIndex={-1}
        onKeyDown={onGridKeyDown}
        // 클릭이 끝난 뒤(onClick은 mouseup 이후) 활성 셀 입력창으로 포커스를 확실히 가져온다.
        // 실제 클릭 땐 ProseMirror가 mousedown/up에서 포커스를 도로 가져가(글자가 표 위 문단에
        // 쳐짐), 그 뒤인 onClick 시점에 다시 잡아야 이긴다.
        onClick={() => activeInputRef.current?.focus({ preventScroll: true })}
        // 셀 범위 복사/잘라내기/붙여넣기(엑셀식). 입력창에서 올라온 이벤트도 여기서 받는다.
        onCopy={onGridCopy}
        onCut={onGridCut}
        onPaste={onGridPaste}
        // 글자가 있는 셀에서 드래그를 시작하면 브라우저가 그 텍스트(활성 입력창의 선택 영역)를
        // 네이티브로 끌어 셀 범위 선택 대신 텍스트가 복사됐다. 그리드 안에서 시작되는 네이티브
        // 드래그를 막아 드래그=범위 선택만 되게 한다. 블록 이동 핸들은 그리드 밖이라 영향 없다.
        onDragStart={(e) => e.preventDefault()}
        // overflow-hidden을 두지 않는다 — 선택 툴바가 선택 위/아래로 표 밖까지 떠야 해서다.
        // 가로 넘침은 안쪽 스크롤 div(overflow-x-auto)가 자르고, 코너는 bg-card라 티가 안 난다.
        className="border-border bg-card group/grid relative flex flex-col rounded-md border outline-none"
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
                    startColSelect(c, e.shiftKey);
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
                        onPointerEnter={() => {
                          // 채우기 드래그 중이면 이 행을 대상으로, 아니면 셀 범위 확장.
                          if (fillDragging.current) updateFill(vi.index);
                          else extendCellSelect(vi.index, c);
                        }}
                        onDoubleClick={() => startEdit(vi.index, c)}
                        onContextMenu={(e) => openContextMenu(e, vi.index, c)}
                      >
                        {renderCellInner(vi.index, c)}
                        {/* 선택 하이라이트 — 셀 배경 위에 얹는 반투명 오버레이. */}
                        {inSel(vi.index, c) && (
                          <div className="bg-primary/15 pointer-events-none absolute inset-0 z-[5]" />
                        )}
                        {/* 채우기 프리뷰 — 드래그로 정해진 대상 셀에 점선 테두리. */}
                        {inFillTarget(vi.index, c) && (
                          <div className="border-primary pointer-events-none absolute inset-0 z-[5] border border-dashed" />
                        )}
                        {/* 채우기 핸들 — 셀 선택의 우하단 모서리에 작은 사각. 세로로 끌어 채운다. */}
                        {sel?.kind === "cells" &&
                          !editing &&
                          selRect &&
                          vi.index === selRect.r1 &&
                          c === selRect.c1 && (
                            <div
                              role="button"
                              aria-label="채우기 핸들"
                              title="드래그해 값·수식을 아래로 채우기"
                              onPointerDown={startFill}
                              className="border-card bg-primary absolute -right-[3px] -bottom-[3px] z-[7] size-1.5 cursor-crosshair touch-none border"
                            />
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
                      startRowSelect(vi.index, e.shiftKey);
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
                        onContextMenu={(e) =>
                          openContextMenu(e, m.rowIndex, ai)
                        }
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
          {/* 열 푸터 요약 — 요약 함수가 걸린 열이 하나라도 있으면 데이터 밑에 한 줄 뜬다.
            같은 gridTemplateColumns로 열 폭에 맞추고, 가로 스크롤을 본문과 함께 탄다.
            값(summaries[key])이 아직 없으면(null) placeholder(—) — 집계 채우기는 #908. */}
          {meta.columns.some((col) => col.summary) && (
            <div
              className="border-border bg-muted/30 grid border-t text-sm"
              style={{ gridTemplateColumns }}
              aria-label="열 요약"
            >
              {meta.columns.map((col) => (
                <div
                  key={col.key}
                  className={cn(
                    "text-muted-foreground border-border truncate border-r px-2 py-1.5",
                    ALIGN_CLASS[col.align ?? "left"],
                  )}
                >
                  {col.summary ? (meta.summaries?.[col.key] ?? "—") : ""}
                </div>
              ))}
            </div>
          )}
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

        {/* 우클릭 컨텍스트 메뉴 — 선택 범위에 맞춰 서식·병합·행/열 옵션을 한 곳에 모은다.
          (별도 플로팅 툴바 없이 우클릭 하나로 통일한다.) */}
        {ctxMenu &&
          (() => {
            const { row, col } = ctxMenu;
            const cm = mergeAt(row, col);
            const colSpan = cm?.colSpan ?? 1;
            const rowSpan = cm?.rowSpan ?? 1;
            // 선택이 있으면 그 범위, 없으면 우클릭한 셀 1칸을 대상으로 한다.
            const rect = selRect ?? { r0: row, c0: col, r1: row, c1: col };
            const kind = sel?.kind ?? "cells";
            const rowN = rect.r1 - rect.r0 + 1;
            const colN = rect.c1 - rect.c0 + 1;
            const area = rowN * colN;
            const scopeLabel =
              kind === "table"
                ? "표 전체"
                : kind === "rows"
                  ? `${rowN}행`
                  : kind === "cols"
                    ? `${colN}열`
                    : `셀 ${area}개`;
            const singleCell = kind === "cells" && area === 1;
            const canMergeRight = col + colSpan < colCount;
            const canMergeDown = row + rowSpan < rowCount;
            const showMergeSection =
              (kind === "cells" && area > 1) ||
              (singleCell && (canMergeRight || canMergeDown || Boolean(cm)));
            const close = () => setCtxMenu(null);
            const run = (fn: () => void) => () => {
              fn();
              close();
            };
            const item = (
              label: string,
              onClick: () => void,
              opts: { icon?: React.ReactNode; destructive?: boolean } = {},
            ) => (
              <button
                type="button"
                role="menuitem"
                onClick={run(onClick)}
                className={cn(
                  "hover:bg-accent flex w-full items-center gap-2 rounded px-2 py-1.5 text-left text-sm",
                  opts.destructive && "text-destructive",
                )}
              >
                {opts.icon}
                {label}
              </button>
            );
            const divider = <div className="bg-border my-1 h-px" />;
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
                  className="border-border bg-popover fixed z-50 min-w-44 rounded-md border p-1 shadow-md"
                  // 뷰포트 오른쪽·아래로 넘치지 않게 대략 클램프한다(메뉴 크기 여유분).
                  style={{
                    left: Math.min(ctxMenu.x, window.innerWidth - 192),
                    top: Math.min(ctxMenu.y, window.innerHeight - 420),
                  }}
                >
                  {/* 선택 범위 표시 */}
                  <div className="text-muted-foreground px-2 py-1 text-xs">
                    {scopeLabel}
                  </div>
                  {/* 배경색 — 스와치 + 지우기 */}
                  <div className="flex items-center gap-1 px-2 py-1">
                    {CELL_BG_TOKENS.map((token) => (
                      <button
                        key={token}
                        type="button"
                        aria-label={`배경색 ${token}`}
                        onClick={run(() => applyBgSel(token))}
                        className="border-border size-5 rounded-full border"
                        style={{ background: `var(--cell-bg-${token})` }}
                      />
                    ))}
                    <button
                      type="button"
                      aria-label="배경색 지우기"
                      onClick={run(() => applyBgSel(null))}
                      className="text-muted-foreground hover:text-foreground border-border flex size-5 items-center justify-center rounded-full border"
                    >
                      <X className="size-3" />
                    </button>
                  </div>
                  {/* 정렬 + 서식 지우기 */}
                  <div className="flex items-center gap-1 px-2 py-1">
                    {ALIGN_ORDER.map((value) => {
                      const Icon = ALIGN_ICONS[value];
                      return (
                        <button
                          key={value}
                          type="button"
                          aria-label={`정렬 ${ALIGN_LABELS[value]}`}
                          onClick={run(() => applyAlignSel(value))}
                          className="text-muted-foreground hover:bg-accent hover:text-foreground flex size-6 items-center justify-center rounded"
                        >
                          <Icon className="size-4" />
                        </button>
                      );
                    })}
                    <span className="bg-border mx-0.5 h-4 w-px" />
                    {VALIGN_ORDER.map((value) => {
                      const Icon = VALIGN_ICONS[value];
                      return (
                        <button
                          key={value}
                          type="button"
                          aria-label={`세로 정렬 ${VALIGN_LABELS[value]}`}
                          onClick={run(() => applyValignSel(value))}
                          className="text-muted-foreground hover:bg-accent hover:text-foreground flex size-6 items-center justify-center rounded"
                        >
                          <Icon className="size-4" />
                        </button>
                      );
                    })}
                    <button
                      type="button"
                      aria-label="서식 지우기"
                      title="배경·정렬·세로정렬 초기화"
                      onClick={run(clearFormatSel)}
                      className="text-muted-foreground hover:bg-accent hover:text-foreground ml-auto flex size-6 items-center justify-center rounded"
                    >
                      <Eraser className="size-4" />
                    </button>
                  </div>
                  {divider}
                  {/* 병합 — 셀 범위(2칸+)는 통합 병합, 단일 셀은 방향 병합·해제 */}
                  {kind === "cells" &&
                    area > 1 &&
                    item("병합", mergeSel, {
                      icon: <TableCellsMerge className="size-3.5" />,
                    })}
                  {singleCell &&
                    canMergeRight &&
                    item("오른쪽과 병합", () => mergeRight(row, col), {
                      icon: <TableCellsMerge className="size-3.5" />,
                    })}
                  {singleCell &&
                    canMergeDown &&
                    item("아래와 병합", () => mergeDown(row, col), {
                      icon: <TableCellsMerge className="size-3.5 rotate-90" />,
                    })}
                  {singleCell &&
                    cm &&
                    item("병합 해제", () => unmerge(row, col), {
                      icon: <TableCellsSplit className="size-3.5" />,
                    })}
                  {showMergeSection && divider}
                  {/* 행/열 삽입 + 너비 초기화 */}
                  {item("위에 행 삽입", () => addRow(rect.r0), {
                    icon: <Plus className="size-3.5" />,
                  })}
                  {item("아래에 행 삽입", () => addRow(rect.r1 + 1), {
                    icon: <Plus className="size-3.5" />,
                  })}
                  {item("왼쪽에 열 삽입", () => addColumn(rect.c0), {
                    icon: <Plus className="size-3.5" />,
                  })}
                  {item("오른쪽에 열 삽입", () => addColumn(rect.c1 + 1), {
                    icon: <Plus className="size-3.5" />,
                  })}
                  {item("열 너비 초기화", () => {
                    for (let c = rect.c0; c <= rect.c1; c++)
                      resetColWidthMut.mutate(meta.columns[c].key);
                  })}
                  {divider}
                  {/* 내용 지우기(값만) */}
                  {item("내용 지우기", clearSelValues, {
                    icon: <Eraser className="size-3.5" />,
                  })}
                  {divider}
                  {/* 삭제 */}
                  {item("행 삭제", () => void deleteRowsSel(), {
                    icon: <Trash2 className="size-3.5" />,
                    destructive: true,
                  })}
                  {colCount > 1 &&
                    item(
                      "열 삭제",
                      () => {
                        // 한 열이면 데이터 손실 확인 다이얼로그, 여러 열이면 바로 지운다.
                        if (colN === 1)
                          setPendingColDelete(meta.columns[rect.c0].key);
                        else void deleteColsSel();
                      },
                      { icon: <X className="size-3.5" />, destructive: true },
                    )}
                  {/* 표 전체 삭제 — 키보드 단축키를 없앤 대신 유일한 표 삭제 경로.
                    블록을 제거하면 노트가 dataset 정리 확인 다이얼로그를 띄운다. */}
                  {onDeleteBlock &&
                    item("표 삭제", () => onDeleteBlock(), {
                      icon: <Trash2 className="size-3.5" />,
                      destructive: true,
                    })}
                </div>
              </>
            );
          })()}
      </div>

      {/* 행 추가 — 표 아래 가장자리 바(표 밖 gutter). 하단에 호버할 때만 나타난다. */}
      <button
        type="button"
        aria-label="행 추가"
        title="행 추가"
        onClick={() => addRow()}
        disabled={insertMut.isPending}
        className="bg-muted/60 text-muted-foreground hover:bg-muted hover:text-foreground absolute right-5 bottom-0 left-0 z-20 flex h-5 items-center justify-center opacity-0 transition-opacity hover:opacity-100 focus-visible:opacity-100 disabled:opacity-50"
      >
        <Plus className="size-3.5" />
      </button>
      {/* 열 추가 — 표 오른쪽 가장자리 바(표 밖 gutter). 오른쪽에 호버할 때만 나타난다. */}
      <button
        type="button"
        aria-label="열 추가"
        title="열 추가"
        onClick={() => addColumn()}
        disabled={addColMut.isPending}
        className="bg-muted/60 text-muted-foreground hover:bg-muted hover:text-foreground absolute top-0 right-0 bottom-5 z-20 flex w-5 items-center justify-center opacity-0 transition-opacity hover:opacity-100 focus-visible:opacity-100 disabled:opacity-50"
      >
        <Plus className="size-3.5" />
      </button>
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

/** 세로 정렬 값 → 아이콘. 셀 정렬 팔레트와 렌더에 공용. */
const VALIGN_ICONS = {
  top: AlignVerticalJustifyStart,
  middle: AlignVerticalJustifyCenter,
  bottom: AlignVerticalJustifyEnd,
} as const;
const VALIGN_LABELS: Record<CellValign, string> = {
  top: "위",
  middle: "가운데",
  bottom: "아래",
};
/** 세로 정렬 값 → Tailwind flex 정렬 클래스. 셀은 세로 flex라 justify-*로 위/아래를 잡는다. */
const VALIGN_CLASS: Record<CellValign, string> = {
  top: "justify-start",
  middle: "justify-center",
  bottom: "justify-end",
};
/** 세로 정렬 버튼 순서. */
const VALIGN_ORDER: CellValign[] = ["top", "middle", "bottom"];

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
