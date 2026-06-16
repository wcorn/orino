import { ChevronLeft, ChevronRight, Plus } from "lucide-react";
import { useMemo, useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { GoogleNotConnectedBanner } from "@/features/google/components/GoogleNotConnectedBanner";
import {
  addMonths,
  monthGridDays,
  startOfDay,
  toIsoDate,
} from "@/features/review/calendar";

import type { EventWriteRequest } from "../../api/events";
import type { PlannerEvent } from "../../api/feed";
import { eventsByDate, reviewsByDate, tasksByDate } from "../../calendar";
import {
  useCreateEvent,
  useDeleteEvent,
  useUpdateEvent,
} from "../../hooks/useEventMutations";
import { usePlannerCalendar } from "../../hooks/usePlannerCalendar";
import { EventFormDialog } from "./EventFormDialog";
import { PlannerCalendarCell } from "./PlannerCalendarCell";
import { PlannerCalendarLegend } from "./PlannerCalendarLegend";
import { PlannerDayDetailPanel } from "./PlannerDayDetailPanel";

type DialogState = { mode: "create" | "edit"; event?: PlannerEvent };

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

/** 통합 캘린더 월 뷰 — 일정(Google) + 할 일 + 복습(읽기 전용)을 한 그리드에 렌더한다. */
export function PlannerCalendar() {
  const today = useMemo(() => startOfDay(new Date()), []);
  const todayIso = toIsoDate(today);

  const [cursor, setCursor] = useState(
    () => new Date(today.getFullYear(), today.getMonth(), 1),
  );
  const [selected, setSelected] = useState<string>(todayIso);

  const gridDays = useMemo(
    () => monthGridDays(cursor.getFullYear(), cursor.getMonth()),
    [cursor],
  );
  const from = toIsoDate(gridDays[0]);
  const to = toIsoDate(gridDays[gridDays.length - 1]);

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
    if (dialog?.mode === "edit" && dialog.event) {
      updateEvent.mutate(
        { eventId: dialog.event.id, request: values },
        { onSuccess: () => setDialog(null) },
      );
    } else {
      createEvent.mutate(values, { onSuccess: () => setDialog(null) });
    }
  };

  const handleDelete = () => {
    if (dialog?.mode !== "edit" || !dialog.event) return;
    deleteEvent.mutate(dialog.event.id, { onSuccess: () => setDialog(null) });
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label="이전 달"
            onClick={() => setCursor((c) => addMonths(c, -1))}
          >
            <ChevronLeft className="size-4" />
          </Button>
          <h1 className="text-xl font-semibold">
            {cursor.getFullYear()}년 {cursor.getMonth() + 1}월
          </h1>
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label="다음 달"
            onClick={() => setCursor((c) => addMonths(c, 1))}
          >
            <ChevronRight className="size-4" />
          </Button>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setCursor(new Date(today.getFullYear(), today.getMonth(), 1));
              setSelected(todayIso);
            }}
          >
            오늘
          </Button>
          <Button size="sm" onClick={() => setDialog({ mode: "create" })}>
            <Plus className="size-3.5" />
            일정
          </Button>
        </div>
      </div>

      <GoogleNotConnectedBanner />

      {data?.partial && (
        <div
          role="alert"
          className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-700 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-300"
        >
          일부 일정을 불러오지 못했습니다.
        </div>
      )}

      <PlannerCalendarLegend />

      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
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
              <div className="grid grid-cols-7 gap-1" aria-label="불러오는 중">
                {Array.from({ length: 42 }, (_, i) => (
                  <div
                    key={i}
                    className="bg-muted/50 min-h-16 animate-pulse rounded-md"
                  />
                ))}
              </div>
            ) : (
              <div className="grid grid-cols-7 gap-1">
                {gridDays.map((date) => {
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
            />
          </CardContent>
        </Card>
      </div>

      {dialog && (
        <EventFormDialog
          open
          onOpenChange={(next) => {
            if (!next) setDialog(null);
          }}
          mode={dialog.mode}
          googleConnected={googleConnected}
          defaultDate={selected}
          event={dialog.event}
          pending={pending}
          onSubmit={handleSubmit}
          onDelete={dialog.mode === "edit" ? handleDelete : undefined}
        />
      )}
    </div>
  );
}
