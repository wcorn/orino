import { toIsoDate } from "@/features/review/calendar";
import { cn } from "@/lib/utils";

import type { PlannerEvent, PlannerReview, PlannerTask } from "../../api/feed";
import { eventTimeLabel } from "../../calendar";
import { HOURS, layoutDayEvents } from "../../weekLayout";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];
const HOUR_PX = 40;
const GRID_COLS = "grid-cols-[3rem_repeat(7,minmax(0,1fr))]";

interface Props {
  days: Date[];
  eventsMap: Map<string, PlannerEvent[]>;
  tasksMap: Map<string, PlannerTask[]>;
  reviewsMap: Map<string, PlannerReview[]>;
  todayIso: string;
  onEventClick: (event: PlannerEvent) => void;
  onSlotClick: (isoDate: string, hour: number) => void;
}

export function PlannerWeekView({
  days,
  eventsMap,
  tasksMap,
  reviewsMap,
  todayIso,
  onEventClick,
  onSlotClick,
}: Props) {
  return (
    <div className="overflow-x-auto">
      <div className="min-w-[44rem]">
        {/* 요일/날짜 헤더 */}
        <div className={cn("grid", GRID_COLS)}>
          <div />
          {days.map((day) => {
            const iso = toIsoDate(day);
            return (
              <div
                key={iso}
                className={cn(
                  "py-1 text-center text-xs font-medium",
                  iso === todayIso && "text-primary font-semibold",
                  day.getDay() === 0 && "text-red-500",
                )}
              >
                {WEEKDAYS[day.getDay()]} {day.getDate()}
              </div>
            );
          })}
        </div>

        {/* 종일/복습/할일 레인 */}
        <div className={cn("grid border-b", GRID_COLS)}>
          <div className="text-muted-foreground py-1 pr-1 text-right text-[10px]">
            종일
          </div>
          {days.map((day) => {
            const iso = toIsoDate(day);
            const allDayEvents = (eventsMap.get(iso) ?? []).filter(
              (e) => e.allDay,
            );
            const dayTasks = tasksMap.get(iso) ?? [];
            const reviewCount = (reviewsMap.get(iso) ?? []).length;
            return (
              <div
                key={iso}
                className="border-border/50 flex min-h-7 flex-col gap-0.5 border-l p-0.5"
              >
                {allDayEvents.map((e) => (
                  <button
                    key={e.id}
                    type="button"
                    onClick={() => onEventClick(e)}
                    className="truncate rounded bg-blue-500 px-1 text-left text-[10px] text-white"
                  >
                    {e.title ?? "(제목 없음)"}
                  </button>
                ))}
                {dayTasks.map((t) => (
                  <span
                    key={t.id}
                    className="truncate rounded bg-emerald-500 px-1 text-[10px] text-white"
                  >
                    ☑ {t.title}
                  </span>
                ))}
                {reviewCount > 0 && (
                  <span className="bg-primary text-primary-foreground truncate rounded px-1 text-[10px]">
                    복습 {reviewCount}
                  </span>
                )}
              </div>
            );
          })}
        </div>

        {/* 시간축 그리드 */}
        <div className={cn("grid", GRID_COLS)}>
          {/* 시간 라벨 */}
          <div>
            {HOURS.map((h) => (
              <div
                key={h}
                style={{ height: HOUR_PX }}
                className="text-muted-foreground pr-1 text-right text-[10px]"
              >
                {h}시
              </div>
            ))}
          </div>

          {/* 요일별 컬럼 */}
          {days.map((day) => {
            const iso = toIsoDate(day);
            const positioned = layoutDayEvents(eventsMap.get(iso) ?? []);
            return (
              <div
                key={iso}
                className="border-border/50 relative border-l"
                style={{ height: HOUR_PX * 24 }}
              >
                {HOURS.map((h) => (
                  <button
                    key={h}
                    type="button"
                    onClick={() => onSlotClick(iso, h)}
                    style={{ height: HOUR_PX }}
                    aria-label={`${iso} ${h}시 일정 추가`}
                    className="border-border/40 hover:bg-muted/40 block w-full border-t"
                  />
                ))}
                {positioned.map((p) => (
                  <button
                    key={p.event.id}
                    type="button"
                    onClick={() => onEventClick(p.event)}
                    style={{
                      top: `${p.top * 100}%`,
                      height: `${p.height * 100}%`,
                      left: `${p.left * 100}%`,
                      width: `${p.width * 100}%`,
                    }}
                    className="absolute overflow-hidden rounded border border-blue-600 bg-blue-500 px-1 py-0.5 text-left text-[10px] leading-tight text-white"
                  >
                    <span className="block truncate font-medium">
                      {p.event.title ?? "(제목 없음)"}
                    </span>
                    <span className="block truncate opacity-90">
                      {eventTimeLabel(p.event)}
                    </span>
                  </button>
                ))}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
