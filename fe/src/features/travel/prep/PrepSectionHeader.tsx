import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import {
  ChevronDown,
  ChevronRight,
  ChevronUp,
  GripVertical,
} from "lucide-react";
import type { PointerEvent as ReactPointerEvent } from "react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

import type { PrepSection } from "../api/prep";
import { PREP_NO_SECTION_LABEL } from "./categories";

interface PrepSectionHeaderProps {
  section: PrepSection;
  /** 드래그 식별자. 항목은 숫자 id라 겹치지 않는다. */
  sortableId: string;
  open: boolean;
  onToggleOpen: () => void;
  offline: boolean;
  dragMode: boolean;
  pointerFine: boolean;
  canMoveUp: boolean;
  canMoveDown: boolean;
  onMoveUp: () => void;
  onMoveDown: () => void;
}

/**
 * 묶음 소제목 한 줄. 헤더 전체가 접기/펼치기 버튼이고, 오른쪽이 순서 조작이다(#1366).
 *
 * <p><b>「묶음 없음」은 끌 수 없다.</b> 이름을 안 붙인 것이 분류의 기본 상태라 언제나 맨
 * 위다(#1358) — 손잡이도 이동 버튼도 주지 않는다. 그 자리를 옮길 수 있게 하면 「아무것도
 * 안 나눈 줄」이 묶음 사이에 끼어 이름 없는 묶음처럼 보인다.
 *
 * <p>손짓은 항목과 같다 — 마우스는 손잡이, 손가락은 정렬 모드. 한 화면에서 두 겹을 옮기는데
 * 손짓이 다르면 그때부터 「무엇을 어떻게 잡아야 하는지」를 매번 다시 생각하게 된다.
 */
export function PrepSectionHeader({
  section,
  sortableId,
  open,
  onToggleOpen,
  offline,
  dragMode,
  pointerFine,
  canMoveUp,
  canMoveDown,
  onMoveUp,
  onMoveDown,
}: PrepSectionHeaderProps) {
  const name = section.label ?? PREP_NO_SECTION_LABEL;
  /** 이름 없는 묶음은 자리가 고정이라 정렬 대상이 아니다. */
  const sortable = section.label !== null && !offline;
  const handleDrag = sortable && pointerFine && !dragMode;

  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({
    id: sortableId,
    disabled: !sortable || (!dragMode && !handleDrag),
  });

  /*
    정렬 모드에서도 헤더 자체는 <b>접기 버튼</b>으로 남는다 — 항목과 달리 헤더는 누르는 일이
    잦고(접었다 폈다), 모드에 들어갔다고 그게 막히면 긴 목록을 정리하는 동안 접을 수가 없다.
    그래서 끄는 곳은 손잡이 하나로 못박는다.
  */
  const handleProps = {
    ...attributes,
    ...listeners,
    onPointerDown: (event: ReactPointerEvent) => {
      listeners?.onPointerDown?.(event);
      event.stopPropagation();
    },
  };

  const stopDrag = {
    onPointerDown: (event: ReactPointerEvent) => event.stopPropagation(),
  };

  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={cn(
        "group/section border-foreground/10 flex items-center gap-2 border-t px-4 py-2",
        isDragging && "bg-card ring-primary z-10 rounded-lg shadow-lg ring-2",
      )}
    >
      {/* 이름을 「캐리어 3/8」로 못박는다 — 항목 줄과 같은 규칙이다. */}
      <button
        type="button"
        aria-expanded={open}
        aria-label={`${name} ${section.done}/${section.total}`}
        onClick={onToggleOpen}
        className="flex flex-1 items-center gap-2 text-left"
      >
        <span className="text-muted-foreground text-[13px] font-medium">
          {name}
        </span>
        <span className="text-muted-foreground text-xs tabular-nums">
          {section.done}/{section.total}
        </span>
      </button>

      {sortable && (dragMode || handleDrag) && (
        <span
          data-section-tools=""
          className={cn(
            "flex items-center",
            // 정렬 모드에서는 늘 보인다. 마우스에서는 올렸을 때만 — 줄마다 아이콘이
            // 늘어서 있으면 목록이 읽히지 않는다.
            !dragMode &&
              "opacity-0 transition-opacity group-hover/section:opacity-100 focus-within:opacity-100",
          )}
        >
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label={`${name} 묶음 위로`}
            disabled={!canMoveUp}
            onClick={onMoveUp}
            {...stopDrag}
          >
            <ChevronUp className="size-4" />
          </Button>
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label={`${name} 묶음 아래로`}
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
            aria-label={`${name} 묶음 순서 바꾸기`}
            className="text-muted-foreground hover:text-foreground focus-visible:ring-ring/50 flex size-8 cursor-grab items-center justify-center rounded-md focus-visible:ring-2 focus-visible:outline-none active:cursor-grabbing"
            {...handleProps}
          >
            <GripVertical className="size-4" />
          </button>
        </span>
      )}

      <span aria-hidden="true" className="text-muted-foreground">
        {open ? (
          <ChevronDown className="size-3.5 shrink-0" />
        ) : (
          <ChevronRight className="size-3.5 shrink-0" />
        )}
      </span>
    </div>
  );
}
