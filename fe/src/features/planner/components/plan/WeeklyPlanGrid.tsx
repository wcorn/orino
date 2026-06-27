import { useRef, useState } from "react";

import { cn } from "@/lib/utils";

import { blockColorClass } from "./planColors";
import {
  blockAtHour,
  blockFromRange,
  DAY_LABELS,
  DAY_LABELS_LONG,
  type EditableBlock,
  heightRatio,
  HOUR_PX,
  layoutDay,
  topRatio,
  yToMinutes,
} from "./planGrid";

const HOURS = Array.from({ length: 24 }, (_, i) => i);
const COLUMN_HEIGHT = 24 * HOUR_PX;
const DAY_MINUTES = 24 * 60;

interface WeeklyPlanGridProps {
  blocks: EditableBlock[];
  /** 표시할 요일 인덱스(데스크탑 0~6, 모바일 단일). */
  days: number[];
  /** 드래그/클릭 생성 스냅 단위(분). */
  snapMinutes: number;
  /** 빈 영역 클릭(1시간)/드래그(구간)로 새 블록 생성. */
  onCreate: (block: EditableBlock) => void;
  /** 블록 클릭 → 편집. */
  onSelect: (block: EditableBlock) => void;
}

interface Draft {
  day: number;
  startMin: number;
  endMin: number;
}

/** 일~토 × 시간축 그리드. 빈 영역 세로 드래그=구간 생성(연속·스냅), 칸 클릭=1시간, 블록 클릭=편집. */
export function WeeklyPlanGrid({
  blocks,
  days,
  snapMinutes,
  onCreate,
  onSelect,
}: WeeklyPlanGridProps) {
  const dragRef = useRef<{
    day: number;
    startMin: number;
    moved: boolean;
  } | null>(null);
  const suppressClickRef = useRef(false);
  const [draft, setDraft] = useState<Draft | null>(null);

  const minuteAt = (e: React.PointerEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    return yToMinutes(e.clientY - rect.top, rect.height, snapMinutes);
  };

  const handlePointerDown = (
    day: number,
    e: React.PointerEvent<HTMLDivElement>,
  ) => {
    // 블록 위에서 시작한 포인터는 편집(클릭)이므로 드래그 생성 시작 안 함
    if ((e.target as HTMLElement).closest("[data-plan-block]")) return;
    const startMin = minuteAt(e);
    dragRef.current = { day, startMin, moved: false };
    setDraft({ day, startMin, endMin: startMin });
    if (e.currentTarget.setPointerCapture) {
      e.currentTarget.setPointerCapture(e.pointerId);
    }
  };

  const handlePointerMove = (
    day: number,
    e: React.PointerEvent<HTMLDivElement>,
  ) => {
    const drag = dragRef.current;
    if (!drag || drag.day !== day) return;
    const endMin = minuteAt(e);
    if (Math.abs(endMin - drag.startMin) >= snapMinutes) drag.moved = true;
    setDraft({ day, startMin: drag.startMin, endMin });
  };

  const handlePointerUp = (
    day: number,
    e: React.PointerEvent<HTMLDivElement>,
  ) => {
    const drag = dragRef.current;
    dragRef.current = null;
    setDraft(null);
    if (!drag || drag.day !== day) return;
    suppressClickRef.current = drag.moved;
    if (drag.moved) {
      onCreate(blockFromRange(day, drag.startMin, minuteAt(e), snapMinutes));
    }
  };

  const handleHourClick = (day: number, hour: number) => {
    // 드래그로 생성한 경우 뒤따르는 click은 무시(중복 방지)
    if (suppressClickRef.current) {
      suppressClickRef.current = false;
      return;
    }
    onCreate(blockAtHour(day, hour));
  };

  return (
    <div className="overflow-x-auto">
      <div
        className="grid min-w-fit"
        style={{
          gridTemplateColumns: `3rem repeat(${days.length}, minmax(5rem, 1fr))`,
        }}
      >
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
            className="border-border relative touch-none border-l"
            style={{ height: COLUMN_HEIGHT }}
            onPointerDown={(e) => handlePointerDown(day, e)}
            onPointerMove={(e) => handlePointerMove(day, e)}
            onPointerUp={(e) => handlePointerUp(day, e)}
          >
            {/* 시간 칸(클릭=1시간 생성) */}
            {HOURS.map((hour) => (
              <button
                key={hour}
                type="button"
                aria-label={`${DAY_LABELS_LONG[day]} ${hour}시 추가`}
                className="border-border/40 hover:bg-muted/40 absolute inset-x-0 border-t"
                style={{ top: hour * HOUR_PX, height: HOUR_PX }}
                onClick={() => handleHourClick(day, hour)}
              />
            ))}

            {/* 드래그 미리보기 */}
            {draft && draft.day === day && (
              <div
                className="bg-primary/30 border-primary pointer-events-none absolute inset-x-0 rounded border"
                style={{
                  top:
                    (Math.min(draft.startMin, draft.endMin) / DAY_MINUTES) *
                    COLUMN_HEIGHT,
                  height:
                    (Math.abs(draft.endMin - draft.startMin) / DAY_MINUTES) *
                    COLUMN_HEIGHT,
                }}
              />
            )}

            {/* 블록 */}
            {layoutDay(blocks.filter((b) => b.dayOfWeek === day)).map((b) => (
              <button
                key={b.key}
                type="button"
                data-plan-block
                aria-label={`${b.label || "(라벨 없음)"} ${b.startTime}~${b.endTime}`}
                onClick={() => onSelect(b)}
                className={cn(
                  // border-background로 인접(같은 색) 블록 사이에 seam을 만든다
                  "border-background absolute overflow-hidden rounded border px-1 py-0.5 text-left text-[11px] leading-tight text-white shadow-sm",
                  blockColorClass(b.color),
                )}
                style={{
                  top: topRatio(b.startTime) * COLUMN_HEIGHT,
                  height: heightRatio(b.startTime, b.endTime) * COLUMN_HEIGHT,
                  left: `${b.left * 100}%`,
                  width: `${b.width * 100}%`,
                }}
              >
                <span className="block font-semibold">
                  {b.startTime}–{b.endTime}
                </span>
                <span className="block truncate">{b.label}</span>
              </button>
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}
