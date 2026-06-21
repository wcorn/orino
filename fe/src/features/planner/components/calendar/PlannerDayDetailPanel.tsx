import { Trash2 } from "lucide-react";
import { Link } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { parseIsoDate } from "@/features/review/calendar";
import { cn } from "@/lib/utils";

import type { PlannerEvent, PlannerReview, PlannerTask } from "../../api/feed";
import { DayTimeline } from "./DayTimeline";

interface Props {
  isoDate: string;
  events: PlannerEvent[];
  tasks: PlannerTask[];
  reviews: PlannerReview[];
  holidayName?: string;
  onEventClick?: (event: PlannerEvent) => void;
  onTaskToggle?: (task: PlannerTask) => void;
  onTaskDelete?: (task: PlannerTask) => void;
  onRoutineCheck?: (event: PlannerEvent) => void;
  onSlotClick?: (isoDate: string, hour: number) => void;
}

function formatHeading(isoDate: string): string {
  const d = parseIsoDate(isoDate);
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
}

export function PlannerDayDetailPanel({
  isoDate,
  events,
  tasks,
  reviews,
  holidayName,
  onEventClick,
  onTaskToggle,
  onTaskDelete,
  onRoutineCheck,
  onSlotClick,
}: Props) {
  const isEmpty = events.length + tasks.length + reviews.length === 0;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="text-base font-medium">
          {formatHeading(isoDate)}
          {holidayName && (
            <span className="ml-2 text-sm font-normal text-red-500">
              {holidayName}
            </span>
          )}
        </h2>
        {reviews.length > 0 && (
          <Link to="/planner/reviews/today">
            <Button size="sm">오늘 복습 하러가기</Button>
          </Link>
        )}
      </div>

      {isEmpty ? (
        <p className="text-muted-foreground text-sm">이 날 일정이 없어요.</p>
      ) : (
        <div className="flex flex-col gap-4">
          {events.length > 0 && (
            <section className="flex flex-col gap-2">
              <h3 className="text-muted-foreground text-xs font-medium">
                일정 ({events.length})
              </h3>
              <DayTimeline
                isoDate={isoDate}
                events={events}
                onEventClick={onEventClick}
                onRoutineCheck={onRoutineCheck}
                onSlotClick={onSlotClick}
              />
            </section>
          )}

          {tasks.length > 0 && (
            <section className="flex flex-col gap-2">
              <h3 className="text-muted-foreground text-xs font-medium">
                할 일 ({tasks.length})
              </h3>
              <ul className="flex flex-col gap-1.5">
                {tasks.map((task) => (
                  <li
                    key={task.id}
                    className="border-border bg-card flex items-center gap-2 rounded-md border p-2 text-sm"
                  >
                    <Checkbox
                      checked={task.completed}
                      onChange={() => onTaskToggle?.(task)}
                      aria-label={`${task.title} 완료`}
                    />
                    <span
                      className={cn(
                        "min-w-0 flex-1 truncate",
                        task.completed && "text-muted-foreground line-through",
                      )}
                    >
                      {task.title}
                    </span>
                    <button
                      type="button"
                      onClick={() => onTaskDelete?.(task)}
                      aria-label={`${task.title} 삭제`}
                      className="text-muted-foreground hover:text-destructive shrink-0"
                    >
                      <Trash2 className="size-3.5" />
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {reviews.length > 0 && (
            <section className="flex flex-col gap-2">
              <h3 className="text-muted-foreground text-xs font-medium">
                복습 (읽기 전용)
              </h3>
              <ul className="flex flex-col gap-1.5">
                {reviews.map((review) => (
                  <li
                    key={review.id}
                    className="border-border bg-card flex flex-col rounded-md border p-2 text-sm"
                  >
                    <span className="text-muted-foreground truncate text-xs">
                      {review.materialTitle}
                    </span>
                    <span className="truncate">{review.front}</span>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </div>
      )}
    </div>
  );
}
