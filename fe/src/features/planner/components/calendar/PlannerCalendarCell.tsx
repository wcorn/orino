import {
  BUCKET_DOT,
  BUCKET_ORDER,
} from "@/features/review/components/calendar/bucketStyles";
import { cn } from "@/lib/utils";

import type { PlannerEvent, PlannerReview, PlannerTask } from "../../api/feed";
import { classifyFeedReview } from "../../calendar";
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
  onSelect: (isoDate: string) => void;
}

const MAX_DOTS = 5;

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
  onSelect,
}: Props) {
  const reviewDots = BUCKET_ORDER.flatMap((bucket) =>
    reviews
      .filter((r) => classifyFeedReview(r, today) === bucket)
      .map(() => BUCKET_DOT[bucket]),
  );
  const dots = [
    ...events.map(() => EVENT_DOT),
    ...tasks.map(() => TASK_DOT),
    ...reviewDots,
  ];
  const shown = dots.slice(0, MAX_DOTS);
  const overflow = dots.length - shown.length;
  const total = events.length + tasks.length + reviews.length;

  return (
    <button
      type="button"
      aria-label={
        `${isoDate}` +
        (total > 0
          ? ` 일정 ${events.length} 할일 ${tasks.length} 복습 ${reviews.length}`
          : "")
      }
      aria-pressed={isSelected}
      onClick={() => onSelect(isoDate)}
      className={cn(
        "flex min-h-20 flex-col gap-1 rounded-md border p-1.5 text-left transition-colors sm:min-h-24 lg:min-h-28",
        inMonth ? "bg-card" : "bg-muted/30 text-muted-foreground",
        isToday ? "border-primary" : "border-border",
        isSelected && "ring-primary ring-2",
      )}
    >
      <span
        className={cn(
          "text-xs font-medium",
          isToday && "text-primary font-semibold",
        )}
      >
        {date.getDate()}
      </span>
      {shown.length > 0 && (
        <span className="flex flex-wrap items-center gap-0.5">
          {shown.map((dot, i) => (
            <span key={i} className={cn("size-1.5 rounded-full", dot)} />
          ))}
          {overflow > 0 && (
            <span className="text-muted-foreground text-[10px]">
              +{overflow}
            </span>
          )}
        </span>
      )}
    </button>
  );
}
