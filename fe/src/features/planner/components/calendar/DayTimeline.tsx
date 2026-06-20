import { Repeat } from "lucide-react";
import { useEffect, useRef } from "react";

import { cn } from "@/lib/utils";

import type { PlannerEvent } from "../../api/feed";
import { eventTimeParts, sortDayEvents } from "../../calendar";
import { HOURS, layoutDayEvents } from "../../weekLayout";
import { RoutineCheckCircle } from "../routine/RoutineCheckCircle";
import { EVENT_DOT } from "./sourceStyles";

const HOUR_PX = 44;
const MIN_PER_DAY = 24 * 60;

interface Props {
  isoDate: string;
  events: PlannerEvent[];
  onEventClick?: (event: PlannerEvent) => void;
  onRoutineCheck?: (event: PlannerEvent) => void;
  onSlotClick?: (isoDate: string, hour: number) => void;
}

/**
 * Google Calendar식 하루 24시간 타임라인. 종일/습관은 상단 줄에, 시간 일정은
 * 시작·길이에 비례한 블럭으로 시간축에 배치한다(겹침은 컬럼 분할). 진입 시 가장 이른
 * 일정으로 스크롤한다.
 */
export function DayTimeline({
  isoDate,
  events,
  onEventClick,
  onRoutineCheck,
  onSlotClick,
}: Props) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const allDayEvents = sortDayEvents(events).filter((e) => e.allDay);
  const positioned = layoutDayEvents(events);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const firstMin = positioned.length
      ? Math.min(...positioned.map((p) => p.top * MIN_PER_DAY))
      : 8 * 60;
    el.scrollTop = Math.max(0, (firstMin / 60) * HOUR_PX - HOUR_PX);
    // 날짜가 바뀔 때만 재스크롤
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isoDate]);

  return (
    <div className="flex flex-col gap-2">
      {allDayEvents.length > 0 && (
        <ul className="flex flex-col gap-1">
          {allDayEvents.map((event) => {
            const isHabit = event.routine?.type === "habit";
            const done = event.routine?.done ?? false;
            return (
              <li
                key={event.id}
                className="border-border bg-card flex items-center gap-2 rounded-md border px-2 py-1.5 text-sm"
              >
                {isHabit ? (
                  <RoutineCheckCircle
                    checked={done}
                    label={`${event.title ?? "(제목 없음)"} 완료 토글`}
                    onToggle={() => onRoutineCheck?.(event)}
                  />
                ) : (
                  <span className="text-muted-foreground shrink-0 text-[10px]">
                    종일
                  </span>
                )}
                <button
                  type="button"
                  onClick={() => onEventClick?.(event)}
                  className={cn(
                    "min-w-0 flex-1 truncate text-left",
                    done && "text-muted-foreground line-through opacity-70",
                  )}
                >
                  {event.title ?? "(제목 없음)"}
                </button>
              </li>
            );
          })}
        </ul>
      )}

      <div
        ref={scrollRef}
        className="relative max-h-[60vh] overflow-y-auto rounded-md border"
      >
        <div
          className="grid"
          style={{ gridTemplateColumns: "2.75rem minmax(0, 1fr)" }}
        >
          {/* 시간 라벨 */}
          <div>
            {HOURS.map((h) => (
              <div
                key={h}
                style={{ height: HOUR_PX }}
                className="text-muted-foreground pr-1.5 text-right text-[10px] tabular-nums"
              >
                {h}시
              </div>
            ))}
          </div>

          {/* 시간축 + 블럭 */}
          <div
            className="border-border/60 relative border-l"
            style={{ height: HOUR_PX * 24 }}
          >
            {HOURS.map((h) => (
              <button
                key={h}
                type="button"
                disabled={!onSlotClick}
                onClick={() => onSlotClick?.(isoDate, h)}
                style={{ height: HOUR_PX }}
                aria-label={`${isoDate} ${h}시 일정 추가`}
                className="border-border/40 enabled:hover:bg-muted/40 block w-full border-t"
              />
            ))}
            {positioned.map((p) => {
              const time = eventTimeParts(p.event);
              const isSchedule = p.event.routine?.type === "schedule";
              return (
                <button
                  key={p.event.id}
                  type="button"
                  onClick={() => onEventClick?.(p.event)}
                  style={{
                    top: `${p.top * 100}%`,
                    height: `${p.height * 100}%`,
                    left: `calc(${p.left * 100}% + 2px)`,
                    width: `calc(${p.width * 100}% - 4px)`,
                  }}
                  className={cn(
                    "absolute flex flex-col overflow-hidden rounded border border-blue-600 px-1.5 py-0.5 text-left text-[11px] leading-tight text-white",
                    EVENT_DOT,
                  )}
                >
                  <span className="flex items-center gap-1 truncate font-medium">
                    {isSchedule && (
                      <Repeat
                        className="size-3 shrink-0"
                        aria-label="반복 일정"
                      />
                    )}
                    {p.event.title ?? "(제목 없음)"}
                  </span>
                  <span className="truncate tabular-nums opacity-90">
                    {time.start}
                    {time.end ? `–${time.end}` : ""}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
