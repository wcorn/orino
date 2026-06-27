import { cn } from "@/lib/utils";

import { blockColorClass } from "./planColors";
import {
  DAY_LABELS,
  type EditableBlock,
  heightRatio,
  HOUR_PX,
  layoutDay,
  topRatio,
} from "./planGrid";

const HOURS = Array.from({ length: 24 }, (_, i) => i);
const COLUMN_HEIGHT = 24 * HOUR_PX;

interface WeeklyPlanGridProps {
  blocks: EditableBlock[];
  /** 표시할 요일 인덱스(데스크탑 0~6, 모바일 단일). */
  days: number[];
  /** 블록 클릭 → 편집. */
  onSelect: (block: EditableBlock) => void;
}

/** 일~토 × 시간축 그리드. 표시 + 블록 클릭 편집(생성은 상단 [+ 추가] 버튼). */
export function WeeklyPlanGrid({
  blocks,
  days,
  onSelect,
}: WeeklyPlanGridProps) {
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
            className="border-border relative border-l"
            style={{ height: COLUMN_HEIGHT }}
          >
            {/* 시간 격자선 */}
            {HOURS.map((hour) => (
              <div
                key={hour}
                className="border-border/40 absolute inset-x-0 border-t"
                style={{ top: hour * HOUR_PX }}
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
