import { useRef } from "react";

import { cn } from "@/lib/utils";

import { blockColorClass, DEFAULT_COLOR } from "./planColors";
import {
  DAY_LABELS,
  DAY_LABELS_LONG,
  type EditableBlock,
  heightRatio,
  HOUR_PX,
  layoutDay,
  MAX_MINUTE,
  minutesToTime,
  nextKey,
  topRatio,
} from "./planGrid";

const HOURS = Array.from({ length: 24 }, (_, i) => i);
const COLUMN_HEIGHT = 24 * HOUR_PX;

interface WeeklyPlanGridProps {
  blocks: EditableBlock[];
  /** 표시할 요일 인덱스(데스크탑 0~6, 모바일 단일). */
  days: number[];
  /** 빈 영역 클릭/드래그로 새 블록 생성. */
  onCreate: (block: EditableBlock) => void;
  /** 블록 클릭 → 편집. */
  onSelect: (block: EditableBlock) => void;
}

/** 일~토 × 시간축 그리드. 시간 칸 클릭=1시간 생성, 세로 드래그=구간 생성, 블록 클릭=편집. */
export function WeeklyPlanGrid({
  blocks,
  days,
  onCreate,
  onSelect,
}: WeeklyPlanGridProps) {
  const dragRef = useRef<{ day: number; start: number; end: number } | null>(
    null,
  );

  const startDrag = (day: number, hour: number) => {
    dragRef.current = { day, start: hour, end: hour };
  };
  const extendDrag = (day: number, hour: number) => {
    if (dragRef.current && dragRef.current.day === day) {
      dragRef.current.end = hour;
    }
  };
  const endDrag = () => {
    const drag = dragRef.current;
    dragRef.current = null;
    if (!drag) return;
    const lo = Math.min(drag.start, drag.end);
    const hi = Math.max(drag.start, drag.end);
    onCreate({
      key: nextKey(),
      id: null,
      dayOfWeek: drag.day,
      startTime: minutesToTime(lo * 60),
      endTime: minutesToTime(Math.min((hi + 1) * 60, MAX_MINUTE)),
      label: "",
      color: DEFAULT_COLOR,
    });
  };

  return (
    <div className="overflow-x-auto">
      <div
        className="grid min-w-fit"
        style={{
          gridTemplateColumns: `3rem repeat(${days.length}, minmax(6rem, 1fr))`,
        }}
      >
        {/* 헤더: 빈 코너 + 요일 */}
        <div className="border-border bg-background sticky top-0 z-10 border-b" />
        {days.map((day) => (
          <div
            key={day}
            className="border-border bg-background sticky top-0 z-10 border-b border-l py-1.5 text-center text-sm font-medium"
          >
            {DAY_LABELS[day]}
          </div>
        ))}

        {/* 시간 눈금 거터 */}
        <div className="relative" style={{ height: COLUMN_HEIGHT }}>
          {HOURS.map((hour) => (
            <div
              key={hour}
              className="text-muted-foreground absolute right-1 -translate-y-1/2 text-[10px]"
              style={{ top: hour * HOUR_PX }}
            >
              {hour > 0 ? `${String(hour).padStart(2, "0")}:00` : ""}
            </div>
          ))}
        </div>

        {/* 요일 컬럼 */}
        {days.map((day) => (
          <div
            key={day}
            className="border-border relative border-l"
            style={{ height: COLUMN_HEIGHT }}
            onPointerUp={endDrag}
          >
            {/* 시간 칸(생성용) */}
            {HOURS.map((hour) => (
              <button
                key={hour}
                type="button"
                aria-label={`${DAY_LABELS_LONG[day]} ${hour}시 추가`}
                className="border-border/40 hover:bg-muted/40 absolute inset-x-0 border-t"
                style={{ top: hour * HOUR_PX, height: HOUR_PX }}
                onPointerDown={() => startDrag(day, hour)}
                onPointerEnter={() => extendDrag(day, hour)}
              />
            ))}

            {/* 블록 */}
            {layoutDay(blocks.filter((b) => b.dayOfWeek === day)).map((b) => (
              <button
                key={b.key}
                type="button"
                aria-label={`${b.label || "(라벨 없음)"} ${b.startTime}~${b.endTime}`}
                onClick={() => onSelect(b)}
                className={cn(
                  "absolute overflow-hidden rounded px-1 py-0.5 text-left text-[11px] leading-tight text-white shadow-sm",
                  blockColorClass(b.color),
                )}
                style={{
                  top: topRatio(b.startTime) * COLUMN_HEIGHT,
                  height: heightRatio(b.startTime, b.endTime) * COLUMN_HEIGHT,
                  left: `${b.left * 100}%`,
                  width: `${b.width * 100}%`,
                }}
              >
                <span className="font-semibold">{b.startTime}</span> {b.label}
              </button>
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}
