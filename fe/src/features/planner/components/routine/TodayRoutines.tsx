import { Repeat } from "lucide-react";

import { Card, CardContent } from "@/components/ui/card";
import { startOfDay, toIsoDate } from "@/features/review/calendar";
import { cn } from "@/lib/utils";

import type { PlannerEvent } from "../../api/feed";
import { eventTimeLabel } from "../../calendar";
import { usePlannerCalendar } from "../../hooks/usePlannerCalendar";
import { useRoutineCheck } from "../../hooks/useRoutineCheck";
import { RoutineCheckCircle } from "./RoutineCheckCircle";

/** 오늘의 루틴 체크리스트. 습관은 체크 토글(낙관적), 고정 일정은 🔁 읽기 전용. */
export function TodayRoutines() {
  const todayIso = toIsoDate(startOfDay(new Date()));
  const { data } = usePlannerCalendar(todayIso, todayIso);
  const check = useRoutineCheck();

  const routineEvents = (data?.events ?? []).filter(
    (
      e,
    ): e is PlannerEvent & { routine: NonNullable<PlannerEvent["routine"]> } =>
      e.routine != null,
  );
  const habits = routineEvents.filter((e) => e.routine.type === "habit");
  const schedules = routineEvents.filter((e) => e.routine.type === "schedule");

  if (routineEvents.length === 0) return null;

  const doneCount = habits.filter((h) => h.routine.done).length;

  const toggle = (event: (typeof habits)[number]) =>
    check.mutate({
      recurringEventId: event.routine.recurringEventId,
      date: todayIso,
      done: !event.routine.done,
    });

  return (
    <Card>
      <CardContent className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold">오늘의 루틴</h2>
          {habits.length > 0 && (
            <span
              className="text-muted-foreground text-sm tabular-nums"
              aria-label={`완료 ${doneCount} / 전체 ${habits.length}`}
            >
              {doneCount}/{habits.length}
            </span>
          )}
        </div>

        {habits.length > 0 && (
          <ul className="flex flex-col gap-1.5">
            {habits.map((event) => (
              <li key={event.id} className="flex items-center gap-2.5">
                <RoutineCheckCircle
                  checked={event.routine.done}
                  label={`${event.title ?? "(제목 없음)"} 완료 토글`}
                  disabled={check.isPending}
                  onToggle={() => toggle(event)}
                />
                <span
                  className={cn(
                    "min-w-0 flex-1 truncate text-sm",
                    event.routine.done &&
                      "text-muted-foreground line-through opacity-70",
                  )}
                >
                  {event.title ?? "(제목 없음)"}
                </span>
              </li>
            ))}
          </ul>
        )}

        {schedules.length > 0 && (
          <ul className="flex flex-col gap-1.5">
            {schedules.map((event) => (
              <li
                key={event.id}
                className="text-muted-foreground flex items-center gap-2.5 text-sm"
              >
                <Repeat className="size-4 shrink-0" aria-label="반복 일정" />
                <span className="shrink-0 tabular-nums">
                  {eventTimeLabel(event)}
                </span>
                <span className="min-w-0 flex-1 truncate">
                  {event.title ?? "(제목 없음)"}
                </span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
