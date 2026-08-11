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
}: ActivityRowProps) {
  // 날짜 탭이 쓰는 것과 같은 이름으로 — 같은 도시가 화면마다 다른 글자면 안 된다.
  const cityLabel = cityLabelOf(activity.place, cities);
  // 드래그는 모드에 들어온 뒤에만 활성화한다 — 그 전에는 목록을 세로로 스크롤해야 한다.
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: activity.id, disabled: !dragMode });

  const longPress = useLongPress(onEnterDragMode, !dragMode && !offline);

  // 드래그 모드가 아니면 dnd 속성을 아예 붙이지 않는다. `disabled`인 sortable은
  // `role="button" aria-disabled="true"`를 남기는데, 그러면 행 전체가 "비활성 버튼"으로
  // 읽혀 안의 링크를 누를 수 없다(스크린리더에도 그렇게 들린다).
  const dragProps = dragMode ? { ...attributes, ...listeners } : {};

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
          "hover:bg-muted bg-background grid grid-cols-[52px_1fr_auto] items-start gap-2 rounded-lg px-2 py-2.5",
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
