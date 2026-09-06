import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import {
  ChevronDown,
  ChevronUp,
  GripVertical,
  Link,
  TriangleAlert,
  X,
} from "lucide-react";
import type { PointerEvent as ReactPointerEvent } from "react";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { cn } from "@/lib/utils";

import type { PrepItemView } from "../api/prep";
import { useLongPress } from "../board/useLongPress";

interface PrepItemRowProps {
  item: PrepItemView;
  /** 오프라인이면 체크·삭제를 막는다. 보는 것은 그대로 된다(§13). */
  offline: boolean;
  /** 손가락으로 길게 눌러 들어온 정렬 모드. 그 안에서는 행 전체가 손잡이다. */
  dragMode: boolean;
  /**
   * 마우스·트랙패드인가. 그러면 <b>모드 없이</b> 손잡이로 곧바로 끈다 — 롱프레스는 스크롤과
   * 구분해야 하는 손가락의 관용구지, 마우스가 배운 동작이 아니다(일정 보드와 같다).
   */
  pointerFine: boolean;
  canMoveUp: boolean;
  canMoveDown: boolean;
  onMoveUp: () => void;
  onMoveDown: () => void;
  /** 400ms 길게 눌러 정렬 모드로 들어간다. */
  onEnterDragMode: () => void;
  onToggle: (item: PrepItemView, done: boolean) => void;
  onOpen: (item: PrepItemView) => void;
  onDelete: (item: PrepItemView) => void;
}

/**
 * 준비 항목 한 줄.
 *
 * <p><b>체크해도 자리를 옮기지 않는다.</b> 취소선과 흐리게만 걸린다 — 짐 싸기는 목록을
 * 위에서 아래로 훑는 작업이라, 방금 누른 줄이 눈앞에서 사라지면 어디까지 했는지 잃는다.
 * 다 끝나고 보기 싫으면 「완료 숨기기」를 켠다(§13).
 *
 * <p>기한 지남에 「무시」를 두지 않는다. 체크하거나 기한을 옮겨야 사라진다 — 끌 수 있는
 * 경고는 곧 아무도 안 보는 경고가 된다.
 *
 * <p><b>정렬은 일정 보드와 같은 손짓이다</b>(#1364). 마우스는 행에 올리면 나오는 손잡이로
 * 곧바로 끌고, 손가락은 400ms 길게 눌러 모드에 들어간 뒤 끈다. 위/아래 버튼은 장식이 아니라
 * <b>끌기의 대안</b>이다(WCAG 2.5.7) — 끌기로 되는 일은 단일 포인터로도 돼야 한다.
 */
export function PrepItemRow({
  item,
  offline,
  dragMode,
  pointerFine,
  canMoveUp,
  canMoveDown,
  onMoveUp,
  onMoveDown,
  onEnterDragMode,
  onToggle,
  onOpen,
  onDelete,
}: PrepItemRowProps) {
  // 오프라인에서는 순서도 못 바꾼다 — 큐잉하지 않기로 했으므로 실패할 요청을 만들지 않는다.
  const handleDrag = pointerFine && !dragMode && !offline;
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: item.id, disabled: !dragMode && !handleDrag });

  // 데스크톱에는 들어갈 모드가 없다.
  const longPress = useLongPress(
    onEnterDragMode,
    !dragMode && !offline && !pointerFine,
  );

  /*
    모드가 아니면 dnd 속성을 아예 붙이지 않는다. `disabled`인 sortable은
    `role="button" aria-disabled="true"`를 남기는데, 그러면 줄 전체가 「비활성 버튼」으로
    읽혀 안의 체크박스를 누를 수 없다(스크린리더에도 그렇게 들린다).
  */
  const dragProps = dragMode ? { ...attributes, ...listeners } : {};

  /** 손잡이에 붙는 것들. 행이 아니라 여기서만 드래그가 시작된다. */
  const handleProps = {
    ...attributes,
    ...listeners,
    onPointerDown: (event: ReactPointerEvent) => {
      listeners?.onPointerDown?.(event);
      event.stopPropagation();
    },
  };

  /**
   * 버튼·체크박스 위에서 시작한 포인터는 드래그로 넘기지 않는다. 모드에서는 리스너가
   * <b>행 전체</b>에 있어서(아무 데나 잡아 끌 수 있어야 한다) 그대로 두면 센서가 먼저
   * 집어가 클릭이 삼켜진다.
   */
  const stopDrag = {
    onPointerDown: (event: ReactPointerEvent) => event.stopPropagation(),
  };

  return (
    <li
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={cn(
        "group/row flex min-h-11 touch-pan-y items-center gap-2.5 rounded-lg px-4 py-2.5",
        isDragging && "bg-card ring-primary z-10 shadow-lg ring-2",
      )}
      onPointerDown={longPress.onPointerDown}
      onPointerMove={longPress.onPointerMove}
      onPointerUp={longPress.onPointerUp}
      onPointerCancel={longPress.onPointerCancel}
      {...dragProps}
    >
      <Checkbox
        checked={item.done}
        disabled={offline}
        aria-label={item.title}
        onChange={(event) => onToggle(item, event.currentTarget.checked)}
        {...stopDrag}
      />

      {/*
        제목을 누르면 편집 시트가 열린다. 행 전체를 누르게 하면 체크박스를 노리다 빗나간
        손가락이 매번 시트를 연다 — 이 화면에서 가장 자주 하는 동작이 체크다.

        여기서는 포인터를 막지 않는다 — 길게 눌러 정렬 모드에 들어가는 손짓이 대개 제목에서
        시작한다. 모드에 들어가면 이 자리가 버튼이 아니라 글자로 바뀌므로, 손을 뗄 때 시트가
        열리지도 않는다.
      */}
      {dragMode ? (
        <span className="flex-1 truncate text-left text-sm">{item.title}</span>
      ) : (
        <button
          type="button"
          onClick={() => onOpen(item)}
          className={cn(
            "flex-1 truncate text-left text-sm",
            item.done && "text-muted-foreground line-through",
          )}
        >
          {item.title}
        </button>
      )}

      {item.quantity !== null && (
        <span className="text-muted-foreground text-xs tabular-nums">
          {item.quantity}
        </span>
      )}

      {item.dueDaysBefore !== null && (
        <span
          className={cn(
            "flex items-center gap-1 text-xs tabular-nums",
            item.overdue
              ? "text-destructive font-semibold"
              : "text-muted-foreground",
          )}
          title={item.dueDate ?? undefined}
        >
          {item.overdue && <TriangleAlert className="size-3.5 shrink-0" />}
          D-{item.dueDaysBefore}
        </span>
      )}

      {item.url && !dragMode && (
        <a
          href={item.url}
          target="_blank"
          rel="noreferrer noopener"
          aria-label={`${item.title} 링크 열기`}
          className="text-muted-foreground hover:text-foreground shrink-0"
          {...stopDrag}
        >
          <Link className="size-3.5" />
        </a>
      )}

      <span className="ml-auto flex shrink-0 items-center gap-0.5">
        {dragMode ? (
          <>
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={`${item.title} 위로`}
              disabled={!canMoveUp}
              onClick={onMoveUp}
              {...stopDrag}
            >
              <ChevronUp className="size-4" />
            </Button>
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={`${item.title} 아래로`}
              disabled={!canMoveDown}
              onClick={onMoveDown}
              {...stopDrag}
            >
              <ChevronDown className="size-4" />
            </Button>
            {/* 잡아끌 수 있다는 표시. 실제 활성 영역은 행 전체다. */}
            <GripVertical
              aria-hidden="true"
              className="text-muted-foreground size-4 cursor-grab"
            />
          </>
        ) : (
          <>
            {/*
              평소엔 투명하다가 마우스를 올리거나 키보드 포커스가 들어오면 나온다 —
              줄마다 아이콘이 늘어서 있으면 목록이 읽히지 않는다. 감추지 않고 투명하게 두는
              이유는 포커스로도 닿아야 하기 때문이다.
            */}
            {handleDrag && (
              <span
                data-row-tools=""
                className="flex items-center opacity-0 transition-opacity group-hover/row:opacity-100 focus-within:opacity-100"
              >
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={`${item.title} 위로`}
                  disabled={!canMoveUp}
                  onClick={onMoveUp}
                  {...stopDrag}
                >
                  <ChevronUp className="size-4" />
                </Button>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={`${item.title} 아래로`}
                  disabled={!canMoveDown}
                  onClick={onMoveDown}
                  {...stopDrag}
                >
                  <ChevronDown className="size-4" />
                </Button>
                {/* 손잡이는 버튼이다 — 키보드로 잡고(Space) 화살표로 옮길 수 있어야 한다. */}
                <button
                  ref={setActivatorNodeRef}
                  type="button"
                  aria-label={`${item.title} 순서 바꾸기`}
                  className="text-muted-foreground hover:text-foreground focus-visible:ring-ring/50 flex size-8 cursor-grab items-center justify-center rounded-md focus-visible:ring-2 focus-visible:outline-none active:cursor-grabbing"
                  {...handleProps}
                >
                  <GripVertical className="size-4" />
                </button>
              </span>
            )}

            {/*
              삭제는 데스크톱에만 둔다(§10.1). 좁은 화면에서 체크박스 옆에 X를 붙이면 누르려던
              것과 지우는 것이 손가락 하나 차이가 된다 — 모바일은 시트 안에서 지운다.
            */}
            <button
              type="button"
              disabled={offline}
              aria-label={`${item.title} 삭제`}
              onClick={() => onDelete(item)}
              className="text-muted-foreground hover:text-destructive hidden shrink-0 disabled:opacity-40 sm:block"
              {...stopDrag}
            >
              <X className="size-3.5" />
            </button>
          </>
        )}
      </span>
    </li>
  );
}
