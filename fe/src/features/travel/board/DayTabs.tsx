import { useDroppable } from "@dnd-kit/core";
import { Archive } from "lucide-react";

import type { BoardDay } from "@/features/travel/api/activities";
import type { DailyWeather } from "@/features/travel/api/tools";
import { formatShortDate } from "@/features/travel/lib/tripStatus";
import { iconFor, needsUmbrella } from "@/features/travel/tools/weatherIcon";
import { cn } from "@/lib/utils";

interface DayTabsProps {
  days: BoardDay[];
  archiveCount: number;
  /** 선택된 날짜. null이면 보관함 칩이 활성이다. */
  selectedDate: string | null;
  onSelectDate: (date: string) => void;
  onSelectArchive: () => void;
  /** 드래그 중이면 칩이 드롭 대상이 된다 — 여기로 떨어뜨리면 그 날짜로 옮긴다. */
  droppable?: boolean;
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
  droppable = false,
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
          dropId={droppable ? `day:${day.date}` : undefined}
          active={selectedDate === day.date}
          label={`${day.dayIndex}일차`}
          sub={`${formatShortDate(day.date)} ${day.weekday}`}
          weather={day.weather}
          onClick={() => onSelectDate(day.date)}
        />
      ))}
      <Chip
        dropId={droppable ? "day:archive" : undefined}
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
  /** 예보 범위(16일) 밖이면 없다 — 그 자리를 비운다. */
  weather?: DailyWeather | null;
  icon?: React.ReactNode;
  onClick: () => void;
  /** 있으면 드롭 대상으로 등록한다. */
  dropId?: string;
}

function Chip({
  active,
  label,
  sub,
  weather,
  icon,
  onClick,
  dropId,
}: ChipProps) {
  const WeatherGlyph = weather
    ? iconFor(weather.icon, weather.precipProbability)
    : null;
  const alert = weather ? needsUmbrella(weather.precipProbability) : false;
  const { setNodeRef, isOver } = useDroppable({
    id: dropId ?? "",
    disabled: !dropId,
  });

  return (
    <button
      ref={dropId ? setNodeRef : undefined}
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={cn(
        "flex min-w-[78px] shrink-0 flex-col items-start gap-0.5 rounded-lg border px-2.5 py-2 transition-colors",
        active
          ? "bg-accent text-accent-foreground border-primary"
          : "bg-card border-border hover:bg-muted",
        // 이 칩 위에 떠 있다 — 놓으면 이 날짜로 간다는 걸 미리 보여준다.
        isOver && "border-primary ring-primary bg-accent ring-2",
      )}
    >
      <span className="flex items-center gap-1 text-[13px] font-semibold">
        {icon}
        {label}
      </span>
      <span className="text-muted-foreground text-[11px]">{sub}</span>
      {/* 예보 범위(16일) 밖이면 이 줄이 아예 없다 — 빈 자리를 남기지 않는다. */}
      {weather && WeatherGlyph && (
        <span
          className={cn(
            "flex items-center gap-1 text-[11px] tabular-nums",
            alert ? "text-warning" : "text-muted-foreground",
          )}
        >
          <WeatherGlyph className="size-3" />
          {weather.tempMax ?? "–"}°/{weather.tempMin ?? "–"}°
        </span>
      )}
    </button>
  );
}
