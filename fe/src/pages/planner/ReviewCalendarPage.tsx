import { ChevronLeft, ChevronRight } from "lucide-react";
import { useMemo, useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { GoogleNotConnectedBanner } from "@/features/google/components/GoogleNotConnectedBanner";
import {
  addMonths,
  groupByDate,
  monthGridDays,
  startOfDay,
  toIsoDate,
} from "@/features/review/calendar";
import { CalendarCell } from "@/features/review/components/calendar/CalendarCell";
import { CalendarLegend } from "@/features/review/components/calendar/CalendarLegend";
import { DayDetailPanel } from "@/features/review/components/calendar/DayDetailPanel";
import { useCalendarReviews } from "@/features/review/hooks/useCalendarReviews";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

export function ReviewCalendarPage() {
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

  const { data, isLoading } = useCalendarReviews(from, to);

  const byDate = useMemo(() => groupByDate(data?.reviews ?? []), [data]);

  const selectedReviews = byDate.get(selected) ?? [];

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
      </div>

      <GoogleNotConnectedBanner />

      <CalendarLegend />

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
            <div className="grid grid-cols-7 gap-1">
              {gridDays.map((date) => {
                const iso = toIsoDate(date);
                return (
                  <CalendarCell
                    key={iso}
                    date={date}
                    isoDate={iso}
                    inMonth={date.getMonth() === cursor.getMonth()}
                    isToday={iso === todayIso}
                    isSelected={iso === selected}
                    reviews={byDate.get(iso) ?? []}
                    today={today}
                    onSelect={setSelected}
                  />
                );
              })}
            </div>
            {isLoading && (
              <p className="text-muted-foreground text-xs">불러오는 중...</p>
            )}
            {!isLoading && (data?.reviews.length ?? 0) === 0 && (
              <p className="text-muted-foreground text-sm">
                이 달에는 복습 일정이 없어요.
              </p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <DayDetailPanel
              isoDate={selected}
              reviews={selectedReviews}
              today={today}
            />
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
