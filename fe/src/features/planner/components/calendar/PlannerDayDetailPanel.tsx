import { Repeat, Trash2 } from "lucide-react";
import { Link } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { parseIsoDate } from "@/features/review/calendar";
import { cn } from "@/lib/utils";

import type { PlannerEvent, PlannerReview, PlannerTask } from "../../api/feed";
import { eventTimeParts, sortDayEvents } from "../../calendar";
import { RoutineCheckCircle } from "../routine/RoutineCheckCircle";

interface Props {
  isoDate: string;
  events: PlannerEvent[];
  tasks: PlannerTask[];
  reviews: PlannerReview[];
  onEventClick?: (event: PlannerEvent) => void;
  onTaskToggle?: (task: PlannerTask) => void;
  onTaskDelete?: (task: PlannerTask) => void;
  onRoutineCheck?: (event: PlannerEvent) => void;
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
  onEventClick,
  onTaskToggle,
  onTaskDelete,
  onRoutineCheck,
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
              <ul className="flex flex-col">
                {sortDayEvents(events).map((event, index, sorted) => {
                  const isHabit = event.routine?.type === "habit";
                  const isSchedule = event.routine?.type === "schedule";
                  const done = event.routine?.done ?? false;
                  const time = eventTimeParts(event);
                  const isLast = index === sorted.length - 1;
                  return (
                    <li key={event.id} className="flex gap-2.5">
                      {/* 시간 컬럼: 시작 위, 종료 아래 */}
                      <div className="w-11 shrink-0 pt-2 text-right text-[11px] leading-tight tabular-nums">
                        <div className="font-medium">{time.start}</div>
                        {time.end && (
                          <div className="text-muted-foreground">
                            {time.end}
                          </div>
                        )}
                      </div>

                      {/* 타임라인 레일: 점 + 연결선 */}
                      <div
                        className="flex flex-col items-center"
                        aria-hidden="true"
                      >
                        <span
                          className={cn(
                            "mt-2.5 size-2 shrink-0 rounded-full",
                            done ? "bg-primary" : "bg-blue-500",
                          )}
                        />
                        {!isLast && (
                          <span className="bg-border mt-0.5 w-px grow" />
                        )}
                      </div>

                      {/* 내용 카드 */}
                      <div className="min-w-0 flex-1 pb-1.5">
                        <div className="border-border bg-card flex items-center gap-2 rounded-md border p-2 text-sm">
                          {isHabit && (
                            <RoutineCheckCircle
                              checked={done}
                              label={`${event.title ?? "(제목 없음)"} 완료 토글`}
                              onToggle={() => onRoutineCheck?.(event)}
                            />
                          )}
                          <button
                            type="button"
                            onClick={() => onEventClick?.(event)}
                            className="hover:bg-muted/50 flex min-w-0 flex-1 flex-col rounded-sm text-left transition-colors"
                          >
                            <span
                              className={cn(
                                "flex items-center gap-1 truncate",
                                done &&
                                  "text-muted-foreground line-through opacity-70",
                              )}
                            >
                              {isSchedule && (
                                <Repeat
                                  className="size-3.5 shrink-0"
                                  aria-label="반복 일정"
                                />
                              )}
                              {event.title ?? "(제목 없음)"}
                            </span>
                            {event.location && (
                              <span className="text-muted-foreground truncate text-xs">
                                {event.location}
                              </span>
                            )}
                          </button>
                        </div>
                      </div>
                    </li>
                  );
                })}
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
