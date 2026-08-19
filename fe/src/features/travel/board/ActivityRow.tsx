import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import {
  Archive,
  Bell,
  CalendarPlus,
  ChevronDown,
  ChevronUp,
  GripVertical,
  MapPin,
  Star,
  Trash2,
} from "lucide-react";
import type { PointerEvent as ReactPointerEvent } from "react";
import { Link } from "react-router-dom";

import { Button } from "@/components/ui/button";
import type { Activity, BaseCity } from "@/features/travel/api/activities";
import { cityLabelOf } from "@/features/travel/lib/cityLabel";
import { cn } from "@/lib/utils";

import { useLongPress } from "./useLongPress";
import { useSwipeAction } from "./useSwipeAction";

interface ActivityRowProps {
  activity: Activity;
  /**
   * 이 여행에 등장하는 도시들. 도시 이탈 꼬리표를 <b>날짜 탭과 같은 이름</b>으로 쓰기 위해
   * 받는다 — 장소가 들고 온 이름은 다른 Google 필드에서 와서 표기가 갈린다.
   */
  cities: BaseCity[];
  /** 보관함을 보고 있으면 "보관함으로"를 감춘다(이미 거기 있다). */
  inArchive: boolean;
  dragMode: boolean;
  /** 오프라인이면 편집이 아예 없다(§4.6) — 실패할 요청을 보내지 않는다. */
  offline: boolean;
  /** 드래그 모드에서 한 칸씩 옮기기. 정밀하게 맞추기 어려운 손가락을 위한 대안이다. */
  onMoveUp: () => void;
  onMoveDown: () => void;
  canMoveUp: boolean;
  canMoveDown: boolean;
  onArchive: () => void;
  /** 보관함에서만 — 담을 날짜를 고르는 시트를 연다. */
  onPickDay?: () => void;
  onDelete: () => void;
  /** 400ms 길게 눌러 드래그 모드로 들어간다. */
  onEnterDragMode: () => void;
  /**
   * 마우스·트랙패드인가. 그러면 <b>모드 없이</b> 손잡이로 곧바로 끈다 —
   * 롱프레스는 스크롤과 구분해야 하는 손가락의 관용구지, 마우스가 배운 동작이 아니다.
   */
  pointerFine: boolean;
}

/**
 * 일정 한 줄. 평소엔 시각·본문·액션 3열이고, 드래그 모드에서는 우측이 이동 조작으로 바뀐다.
 *
 * <p>스와이프(좌=삭제, 우=보관함)와 우측 버튼은 <b>같은 동작</b>이다 — 터치에는 스와이프를,
 * 데스크톱에는 버튼을 주되 기능을 갈라놓지 않는다.
 */
export function ActivityRow({
  activity,
  cities,
  inArchive,
  dragMode,
  offline,
  onMoveUp,
  onMoveDown,
  canMoveUp,
  canMoveDown,
  onArchive,
  onPickDay,
  onDelete,
  onEnterDragMode,
  pointerFine,
}: ActivityRowProps) {
  // 날짜 탭이 쓰는 것과 같은 이름으로 — 같은 도시가 화면마다 다른 글자면 안 된다.
  const cityLabel = cityLabelOf(activity.place, cities);
  /**
   * 손가락은 <b>모드에 들어온 뒤에만</b> 끌 수 있다 — 그 전에는 목록을 세로로 스크롤해야 하고,
   * 두 손짓을 가를 방법이 롱프레스뿐이다. 마우스는 그 문제가 없어 언제나 끌 수 있고,
   * 대신 <b>손잡이에서만</b> 시작한다(본문 클릭은 상세로 가야 한다).
   */
  const handleDrag = pointerFine && !dragMode && !offline;
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: activity.id, disabled: !dragMode && !handleDrag });

  // 데스크톱에는 들어갈 모드가 없다.
  const longPress = useLongPress(
    onEnterDragMode,
    !dragMode && !offline && !pointerFine,
  );

  // 드래그 모드가 아니면 dnd 속성을 아예 붙이지 않는다. `disabled`인 sortable은
  // `role="button" aria-disabled="true"`를 남기는데, 그러면 행 전체가 "비활성 버튼"으로
  // 읽혀 안의 링크를 누를 수 없다(스크린리더에도 그렇게 들린다).
  const dragProps = dragMode ? { ...attributes, ...listeners } : {};

  /**
   * 손잡이에 붙는 것들. 행이 아니라 여기서만 드래그가 시작된다.
   *
   * <p>포인터를 행으로 흘려보내지 않는다 — 흘리면 스와이프(좌우)가 같이 깨어나 세로로 끄는
   * 동안 행이 옆으로 밀린다. 손잡이 자신의 리스너는 이미 이 시점에 실행된 뒤다.
   */
  const handleProps = {
    ...attributes,
    ...listeners,
    onPointerDown: (e: ReactPointerEvent) => {
      listeners?.onPointerDown?.(e);
      e.stopPropagation();
    },
  };

  /**
   * 버튼 위에서 시작한 포인터는 드래그로 넘기지 않는다.
   *
   * <p>드래그 리스너는 <b>항상 행 전체</b>에 있다 — 아무 데나 길게 눌러 모드에 들어가야 하기
   * 때문이다. 그러면 행 안의 버튼을 눌러도 센서가 먼저 집어가 클릭이 삼켜지므로, 버튼에서만
   * 전파를 끊는다. (모드 진입 시 리스너를 손잡이로 옮기는 방법도 있지만, 드래그 도중 활성
   * 노드가 바뀌어 dnd-kit이 드래그 종료를 놓친다.)
   */
  const stopDrag = {
    onPointerDown: (e: ReactPointerEvent) => e.stopPropagation(),
  };

  const swipe = useSwipeAction({
    // 드래그 모드에서는 세로 드래그가 주인이라 스와이프를 끈다.
    // 오프라인에서는 밀 수는 있어도 되돌릴 수 없는 요청이 나가면 안 된다.
    disabled: dragMode || offline,
    onSwipeLeft: onDelete,
    onSwipeRight: inArchive ? undefined : onArchive,
  });

  return (
    <li
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={cn("relative touch-pan-y", isDragging && "z-10")}
      {...dragProps}
    >
      <div
        style={{ transform: `translateX(${swipe.offset}px)` }}
        className={cn(
          "group/row hover:bg-muted bg-background grid grid-cols-[52px_1fr_auto] items-start gap-2 rounded-lg px-2 py-2.5",
          !swipe.dragging && "transition-transform",
          isDragging && "bg-card ring-primary scale-[1.01] shadow-lg ring-2",
        )}
        onPointerDown={(e) => {
          longPress.onPointerDown(e);
          swipe.onPointerDown(e);
        }}
        onPointerMove={(e) => {
          longPress.onPointerMove(e);
          swipe.onPointerMove(e);
        }}
        onPointerUp={() => {
          longPress.onPointerUp();
          swipe.onPointerUp();
        }}
        onPointerCancel={() => {
          longPress.onPointerCancel();
          swipe.onPointerUp();
        }}
      >
        <span
          className={
            activity.startTime
              ? "pt-px text-sm tabular-nums"
              : "text-muted-foreground pt-px text-sm"
          }
        >
          {activity.startTime ?? "──"}
        </span>

        {/* 드래그 모드에서는 행을 눌러도 상세로 가지 않는다 — 옮기려던 손짓이 이동이 된다. */}
        {dragMode ? (
          <span className="min-w-0">
            <span className="block text-[15px] leading-[1.4]">
              {activity.title}
            </span>
          </span>
        ) : (
          <Link
            to={`/travel/activities/${activity.id}`}
            className="min-w-0 no-underline"
          >
            <span className="block text-[15px] leading-[1.4]">
              {activity.title}
            </span>
            {activity.place && (
              <span className="text-muted-foreground flex items-center gap-1 text-xs">
                <MapPin className="size-3 shrink-0" />
                <span className="truncate">{activity.place.name}</span>
                {/* 그날 기준 도시와 다른 도시의 장소다. 막지 않고 알려만 준다 —
                    오사카 가게를 교토 날짜에 두는 건 사용자의 선택이다. */}
                {activity.outOfBaseCity && cityLabel && (
                  <span className="text-warning shrink-0">· {cityLabel}</span>
                )}
              </span>
            )}
          </Link>
        )}

        <span className="flex items-center gap-0.5">
          {dragMode ? (
            <>
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={`${activity.title} 위로`}
                disabled={!canMoveUp}
                onClick={onMoveUp}
                {...stopDrag}
              >
                <ChevronUp className="size-4" />
              </Button>
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={`${activity.title} 아래로`}
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
              {/* 데스크톱 전용 순서 조작. 평소엔 숨어 있다가 행에 마우스를 올리거나
                  키보드 포커스가 들어오면 나온다 — 늘 떠 있으면 줄마다 아이콘이 다섯 개다.
                  위/아래 버튼은 장식이 아니라 <b>끌기의 대안</b>이다(WCAG 2.2 SC 2.5.7):
                  끌기로 되는 일은 단일 포인터로도 돼야 한다. */}
              {handleDrag && (
                <span
                  data-row-tools=""
                  className="flex items-center opacity-0 transition-opacity group-hover/row:opacity-100 focus-within:opacity-100"
                >
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label={`${activity.title} 위로`}
                    disabled={!canMoveUp}
                    onClick={onMoveUp}
                    {...stopDrag}
                  >
                    <ChevronUp className="size-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label={`${activity.title} 아래로`}
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
                    aria-label={`${activity.title} 순서 바꾸기`}
                    className="text-muted-foreground hover:text-foreground focus-visible:ring-ring/50 flex size-8 cursor-grab items-center justify-center rounded-md focus-visible:ring-2 focus-visible:outline-none active:cursor-grabbing"
                    {...handleProps}
                  >
                    <GripVertical className="size-4" />
                  </button>
                </span>
              )}
              {activity.notifyEnabled && (
                <Bell
                  aria-label="알림 켜짐"
                  className="text-primary size-3.5"
                />
              )}
              {activity.hasLog && (
                <Star
                  aria-label="기록 있음"
                  className="text-muted-foreground size-3.5"
                />
              )}
              {/* 보관함 일정은 "언제 갈지"가 남은 유일한 질문이라 그 버튼을 준다. */}
              {!offline && inArchive && onPickDay && (
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={`${activity.title} 날짜에 담기`}
                  onClick={onPickDay}
                  {...stopDrag}
                >
                  <CalendarPlus className="size-4" />
                </Button>
              )}
              {/* 오프라인이면 액션을 감춘다 — 눌러도 실패할 버튼을 두지 않는다. */}
              {!offline && !inArchive && (
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={`${activity.title} 보관함으로`}
                  onClick={onArchive}
                  {...stopDrag}
                >
                  <Archive className="size-4" />
                </Button>
              )}
              {!offline && (
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={`${activity.title} 삭제`}
                  onClick={onDelete}
                  {...stopDrag}
                >
                  <Trash2 className="size-4" />
                </Button>
              )}
            </>
          )}
        </span>
      </div>
    </li>
  );
}
