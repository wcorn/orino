import { CheckSquare, ChevronLeft, ChevronRight, Plus } from "lucide-react";
import { useMemo, useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  addDays,
  addMonths,
  monthGridDays,
  startOfDay,
  toIsoDate,
} from "@/features/review/calendar";

import type { EventWriteRequest } from "../../api/events";
import type { PlannerEvent } from "../../api/feed";
import type { TaskCreateRequest } from "../../api/tasks";
import { eventsByDate, reviewsByDate, tasksByDate } from "../../calendar";
import {
  useCreateEvent,
  useDeleteEvent,
  useUpdateEvent,
} from "../../hooks/useEventMutations";
import { usePlannerCalendar } from "../../hooks/usePlannerCalendar";
import {
  useCreateTask,
  useDeleteTask,
  useUpdateTask,
} from "../../hooks/useTaskMutations";
import { weekDays } from "../../weekLayout";
import { EventFormDialog } from "./EventFormDialog";
import { PlannerCalendarCell } from "./PlannerCalendarCell";
import { PlannerCalendarLegend } from "./PlannerCalendarLegend";
import { PlannerConnectionBanner } from "./PlannerConnectionBanner";
import { PlannerDayDetailPanel } from "./PlannerDayDetailPanel";
import { PlannerWeekView } from "./PlannerWeekView";
import { TaskFormDialog } from "./TaskFormDialog";

type CalendarView = "month" | "week" | "day";
type DialogState =
  | { mode: "create"; date?: string; startTime?: string }
  | { mode: "edit"; event: PlannerEvent };

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

/** 통합 캘린더 — 월/주/일 뷰 전환. 일정(Google) + 할 일 + 복습(읽기 전용). */
export function PlannerCalendar() {
  const today = useMemo(() => startOfDay(new Date()), []);
  const todayIso = toIsoDate(today);

  const [view, setView] = useState<CalendarView>("month");
  const [cursor, setCursor] = useState<Date>(today);
  const [selected, setSelected] = useState<string>(todayIso);

  const monthDays = useMemo(
    () => monthGridDays(cursor.getFullYear(), cursor.getMonth()),
    [cursor],
  );
  const week = useMemo(() => weekDays(cursor), [cursor]);
  const single = useMemo(() => [startOfDay(cursor)], [cursor]);
  const days = view === "month" ? monthDays : view === "week" ? week : single;
  const from = toIsoDate(days[0]);
  const to = toIsoDate(days[days.length - 1]);

  const { data, isLoading } = usePlannerCalendar(from, to);

  const eventsMap = useMemo(() => eventsByDate(data?.events ?? []), [data]);
  const tasksMap = useMemo(() => tasksByDate(data?.tasks ?? []), [data]);
  const reviewsMap = useMemo(() => reviewsByDate(data?.reviews ?? []), [data]);

  const googleConnected = data?.googleConnected ?? false;

  const [dialog, setDialog] = useState<DialogState | null>(null);
  const createEvent = useCreateEvent();
  const updateEvent = useUpdateEvent();
  const deleteEvent = useDeleteEvent();
  const pending =
    createEvent.isPending || updateEvent.isPending || deleteEvent.isPending;

  const handleSubmit = (values: EventWriteRequest) => {
    if (dialog?.mode === "edit") {
      updateEvent.mutate(
        { eventId: dialog.event.id, request: values },
        { onSuccess: () => setDialog(null) },
      );
    } else {
      createEvent.mutate(values, { onSuccess: () => setDialog(null) });
    }
  };

  const handleDelete = () => {
    if (dialog?.mode !== "edit") return;
    deleteEvent.mutate(dialog.event.id, { onSuccess: () => setDialog(null) });
  };

  const [taskDialogOpen, setTaskDialogOpen] = useState(false);
  const createTask = useCreateTask();
  const updateTask = useUpdateTask();
  const deleteTask = useDeleteTask();

  const handleTaskCreate = (values: TaskCreateRequest) => {
    createTask.mutate(values, { onSuccess: () => setTaskDialogOpen(false) });
  };

  const step = view === "month" ? 0 : view === "week" ? 7 : 1;
  const goPrev = () =>
    setCursor((c) => (view === "month" ? addMonths(c, -1) : addDays(c, -step)));
  const goNext = () =>
    setCursor((c) => (view === "month" ? addMonths(c, 1) : addDays(c, step)));

  let periodTitle: string;
  if (view === "month") {
    periodTitle = `${cursor.getFullYear()}년 ${cursor.getMonth() + 1}월`;
  } else if (view === "week") {
    periodTitle =
      `${week[0].getMonth() + 1}월 ${week[0].getDate()}일 – ` +
      `${week[6].getMonth() + 1}월 ${week[6].getDate()}일`;
  } else {
    periodTitle =
      `${single[0].getMonth() + 1}월 ${single[0].getDate()}일 ` +
      `(${WEEKDAYS[single[0].getDay()]})`;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label="이전 기간"
            onClick={goPrev}
          >
            <ChevronLeft className="size-4" />
          </Button>
          <h1 className="text-xl font-semibold">{periodTitle}</h1>
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label="다음 기간"
            onClick={goNext}
          >
            <ChevronRight className="size-4" />
          </Button>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <div className="flex gap-1">
            {(
              [
                ["month", "월"],
                ["week", "주"],
                ["day", "일"],
              ] as const
            ).map(([value, label]) => (
              <Button
                key={value}
                variant={view === value ? "default" : "outline"}
                size="sm"
                aria-pressed={view === value}
                onClick={() => setView(value)}
              >
                {label}
              </Button>
            ))}
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setCursor(today);
              setSelected(todayIso);
            }}
          >
            오늘
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setTaskDialogOpen(true)}
          >
            <CheckSquare className="size-3.5" />할 일
          </Button>
          <Button size="sm" onClick={() => setDialog({ mode: "create" })}>
            <Plus className="size-3.5" />
            일정
          </Button>
        </div>
      </div>

      <PlannerConnectionBanner feed={data} />

      <PlannerCalendarLegend />

      {view !== "month" ? (
        <Card>
          <CardContent>
            <PlannerWeekView
              days={view === "week" ? week : single}
              eventsMap={eventsMap}
              tasksMap={tasksMap}
              reviewsMap={reviewsMap}
              todayIso={todayIso}
              onEventClick={(event) => setDialog({ mode: "edit", event })}
              onSlotClick={(isoDate, hour) =>
                setDialog({
                  mode: "create",
                  date: isoDate,
                  startTime: `${String(hour).padStart(2, "0")}:00`,
                })
              }
            />
          </CardContent>
        </Card>
      ) : (
        <div className="mx-auto grid w-full max-w-6xl gap-6 lg:grid-cols-[1fr_24rem]">
          <Card>
            <CardContent className="flex flex-col gap-2">
              <div className="grid grid-cols-7 gap-1">
                {WEEKDAYS.map((w, i) => (
                  <div
                    key={w}
                    className={
                      "text-muted-foreground py-1 text-center text-xs font-medium" +
                      (i === 0 ? " text-red-500" : "")
                    }
                  >
                    {w}
                  </div>
                ))}
              </div>
              {isLoading ? (
                <div
                  className="grid grid-cols-7 gap-1"
                  aria-label="불러오는 중"
                >
                  {Array.from({ length: 42 }, (_, i) => (
                    <div
                      key={i}
                      className="bg-muted/50 aspect-square animate-pulse rounded-md"
                    />
                  ))}
                </div>
              ) : (
                <div className="grid grid-cols-7 gap-1">
                  {monthDays.map((date) => {
                    const iso = toIsoDate(date);
                    return (
                      <PlannerCalendarCell
                        key={iso}
                        date={date}
                        isoDate={iso}
                        inMonth={date.getMonth() === cursor.getMonth()}
                        isToday={iso === todayIso}
                        isSelected={iso === selected}
                        events={eventsMap.get(iso) ?? []}
                        tasks={tasksMap.get(iso) ?? []}
                        reviews={reviewsMap.get(iso) ?? []}
                        today={today}
                        onSelect={setSelected}
                      />
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardContent>
              <PlannerDayDetailPanel
                isoDate={selected}
                events={eventsMap.get(selected) ?? []}
                tasks={tasksMap.get(selected) ?? []}
                reviews={reviewsMap.get(selected) ?? []}
                onEventClick={(event) => setDialog({ mode: "edit", event })}
                onTaskToggle={(task) =>
                  updateTask.mutate({
                    taskId: task.id,
                    request: { completed: !task.completed },
                  })
                }
                onTaskDelete={(task) => deleteTask.mutate(task.id)}
              />
            </CardContent>
          </Card>
        </div>
      )}

      {dialog && (
        <EventFormDialog
          open
          onOpenChange={(next) => {
            if (!next) setDialog(null);
          }}
          mode={dialog.mode}
          googleConnected={googleConnected}
          defaultDate={
            dialog.mode === "create" ? (dialog.date ?? selected) : selected
          }
          defaultStartTime={
            dialog.mode === "create" ? dialog.startTime : undefined
          }
          event={dialog.mode === "edit" ? dialog.event : undefined}
          pending={pending}
          onSubmit={handleSubmit}
          onDelete={dialog.mode === "edit" ? handleDelete : undefined}
        />
      )}

      <TaskFormDialog
        open={taskDialogOpen}
        onOpenChange={setTaskDialogOpen}
        googleConnected={googleConnected}
        defaultDue={selected}
        pending={createTask.isPending}
        onSubmit={handleTaskCreate}
      />
    </div>
  );
}
