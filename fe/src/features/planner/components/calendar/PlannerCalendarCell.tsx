import {
  BUCKET_DOT,
  BUCKET_ORDER,
} from "@/features/review/components/calendar/bucketStyles";
import { cn } from "@/lib/utils";

import type { PlannerEvent, PlannerReview, PlannerTask } from "../../api/feed";
import {
  classifyFeedReview,
  eventTimeLabel,
  sortDayEvents,
} from "../../calendar";
import { ColorDot } from "./ColorDot";
import { EVENT_DOT, TASK_DOT } from "./sourceStyles";

interface Props {
  date: Date;
  isoDate: string;
  inMonth: boolean;
  isToday: boolean;
  isSelected: boolean;
  events: PlannerEvent[];
  tasks: PlannerTask[];
  reviews: PlannerReview[];
  today: Date;
  /** 공휴일이면 이름(빨간날 표시). 아니면 undefined. */
  holidayName?: string;
  onSelect: (isoDate: string) => void;
}

/** 셀에 한 줄로 보여줄 최대 항목 수. 초과분은 "+N개"로 접는다. */
const MAX_LINES = 3;

export function PlannerCalendarCell({
  date,
  isoDate,
  inMonth,
  isToday,
  isSelected,
  events,
  tasks,
  reviews,
  today,
  holidayName,
  onSelect,
}: Props) {
  const reviewDot =
    BUCKET_ORDER.map((bucket) =>
      reviews.some((r) => classifyFeedReview(r, today) === bucket)
        ? BUCKET_DOT[bucket]
        : null,
    ).find(Boolean) ?? BUCKET_DOT[BUCKET_ORDER[0]];

  // 점 + (시작 시각) + 제목 한 줄. 일정 → 할 일 → 복습(묶음) 순.
  const lines = [
    ...sortDayEvents(events).map((event) => (
      <span
        key={`e-${event.id}`}
        className="flex items-center gap-1 text-[10px] leading-tight"
      >
        <ColorDot size="xs" className={EVENT_DOT} />
        {!event.allDay && (
          <span className="text-muted-foreground shrink-0 tabular-nums">
            {eventTimeLabel(event)}
          </span>
        )}
        <span className="truncate">{event.title ?? "(제목 없음)"}</span>
      </span>
    )),
    ...tasks.map((task) => (
      <span
        key={`t-${task.id}`}
        className="flex items-center gap-1 text-[10px] leading-tight"
      >
        <ColorDot size="xs" className={TASK_DOT} />
        <span
          className={cn(
            "truncate",
            task.completed && "line-through opacity-60",
          )}
        >
          {task.title}
        </span>
      </span>
    )),
    ...(reviews.length > 0
      ? [
          <span
            key="reviews"
            className="flex items-center gap-1 text-[10px] leading-tight"
          >
            <ColorDot size="xs" className={reviewDot} />
            <span className="truncate">복습 {reviews.length}</span>
          </span>,
        ]
      : []),
  ];
  const shown = lines.slice(0, MAX_LINES);
  const hidden = lines.length - shown.length;
  const total = events.length + tasks.length + reviews.length;
  const holiday = holidayName;

  return (
    <button
      type="button"
      aria-label={
        `${isoDate}` +
        (holiday ? ` ${holiday}` : "") +
        (total > 0
          ? ` 일정 ${events.length} 할일 ${tasks.length} 복습 ${reviews.length}`
          : "")
      }
      aria-pressed={isSelected}
      onClick={() => onSelect(isoDate)}
      className={cn(
        "flex min-h-20 flex-col gap-0.5 rounded-md border p-1.5 text-left transition-colors sm:min-h-24 lg:min-h-28",
        inMonth ? "bg-card" : "bg-muted/30 text-muted-foreground",
        isToday ? "border-primary" : "border-border",
        isSelected && "ring-primary ring-2",
      )}
    >
      <span
        className={cn(
          "text-xs font-medium",
          holiday && "text-red-500",
          isToday && "text-primary font-semibold",
        )}
      >
        {date.getDate()}
      </span>
      <span className="flex min-w-0 flex-col gap-0.5">
        {holiday && (
          <span className="truncate text-[10px] leading-tight text-red-500">
            {holiday}
          </span>
        )}
        {shown}
        {hidden > 0 && (
          <span className="text-muted-foreground text-[10px]">+{hidden}개</span>
        )}
      </span>
    </button>
  );
}
