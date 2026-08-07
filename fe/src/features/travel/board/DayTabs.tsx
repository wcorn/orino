import { Archive } from "lucide-react";

import type { BoardDay } from "@/features/travel/api/activities";
import { formatShortDate } from "@/features/travel/lib/tripStatus";
import { cn } from "@/lib/utils";

interface DayTabsProps {
  days: BoardDay[];
  archiveCount: number;
  /** 선택된 날짜. null이면 보관함 칩이 활성이다. */
  selectedDate: string | null;
  onSelectDate: (date: string) => void;
  onSelectArchive: () => void;
}

/**
 * 날짜 탭. 여행 기간의 모든 날짜 + <b>맨 뒤에 항상 보관함</b>.
 *
 * <p>보관함이 마지막인 이유는 날짜가 시간 순서를 갖는 반면 보관함은 "아직 날짜를 못 정한 것"
 * 이라서다 — 중간에 끼우면 순서가 깨진 것처럼 보인다.
 */
export function DayTabs({
  days,
  archiveCount,
  selectedDate,
  onSelectDate,
  onSelectArchive,
}: DayTabsProps) {
  return (
    <div
      role="tablist"
      aria-label="날짜"
      className="flex gap-1.5 overflow-x-auto pb-1"
    >
      {days.map((day) => (
        <Chip
          key={day.date}
          active={selectedDate === day.date}
          label={`${day.dayIndex}일차`}
          sub={`${formatShortDate(day.date)} ${day.weekday}`}
          onClick={() => onSelectDate(day.date)}
        />
      ))}
      <Chip
        active={selectedDate === null}
        label="보관함"
        icon={<Archive className="size-3.5" />}
        sub={`${archiveCount}개`}
        onClick={onSelectArchive}
      />
    </div>
  );
}

interface ChipProps {
  active: boolean;
  label: string;
  sub: string;
  icon?: React.ReactNode;
  onClick: () => void;
}

function Chip({ active, label, sub, icon, onClick }: ChipProps) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={cn(
        "flex min-w-[78px] shrink-0 flex-col items-start gap-0.5 rounded-lg border px-2.5 py-2 transition-colors",
        active
          ? "bg-accent text-accent-foreground border-primary"
          : "bg-card border-border hover:bg-muted",
      )}
    >
      <span className="flex items-center gap-1 text-[13px] font-semibold">
        {icon}
        {label}
      </span>
      <span className="text-muted-foreground text-[11px]">{sub}</span>
      {/* 3행 날씨는 4단계. 예보가 없으면 그때도 이 자리를 비운다. */}
    </button>
  );
}
