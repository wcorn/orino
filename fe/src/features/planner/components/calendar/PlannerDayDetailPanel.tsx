import { Link } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { parseIsoDate } from "@/features/review/calendar";
import { cn } from "@/lib/utils";

import type { PlannerEvent, PlannerReview, PlannerTask } from "../../api/feed";
import { eventTimeLabel } from "../../calendar";

interface Props {
  isoDate: string;
  events: PlannerEvent[];
  tasks: PlannerTask[];
  reviews: PlannerReview[];
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
}: Props) {
  const isEmpty = events.length + tasks.length + reviews.length === 0;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="text-base font-medium">{formatHeading(isoDate)}</h2>
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
              <ul className="flex flex-col gap-1.5">
                {events.map((event) => (
                  <li
                    key={event.id}
                    className="border-border bg-card flex items-start gap-2 rounded-md border p-2 text-sm"
                  >
                    <span className="text-muted-foreground shrink-0 text-xs tabular-nums">
                      {eventTimeLabel(event)}
                    </span>
                    <div className="flex min-w-0 flex-1 flex-col">
                      <span className="truncate">
                        {event.title ?? "(제목 없음)"}
                      </span>
                      {event.location && (
                        <span className="text-muted-foreground truncate text-xs">
                          {event.location}
                        </span>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
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
                    <span aria-hidden>{task.completed ? "☑" : "☐"}</span>
                    <span
                      className={cn(
                        "truncate",
                        task.completed && "text-muted-foreground line-through",
                      )}
                    >
                      {task.title}
                    </span>
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
