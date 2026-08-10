import { useDroppable } from "@dnd-kit/core";
import { Archive } from "lucide-react";
import { Fragment } from "react";

import type { BoardDay } from "@/features/travel/api/activities";
import type { DailyWeather } from "@/features/travel/api/tools";
import { formatShortDate } from "@/features/travel/lib/tripStatus";
import { iconFor, needsUmbrella } from "@/features/travel/tools/weatherIcon";
import { cn } from "@/lib/utils";

import { TAB_HOLD_MS, useLongPress } from "./useLongPress";

interface DayTabsProps {
  days: BoardDay[];
  archiveCount: number;
  /** 선택된 날짜. null이면 보관함 칩이 활성이다. */
  selectedDate: string | null;
  /** 전 기간이 한 도시면 도시명을 감추고 `N일차`로 그린다. */
  singleCity: boolean;
  onSelectDate: (date: string) => void;
  onSelectArchive: () => void;
  /** 450ms 길게 눌러 그 날짜의 기준 도시 시트를 연다. */
  onLongPressDay: (day: BoardDay) => void;
  /** 드래그 중이면 칩이 드롭 대상이 된다 — 여기로 떨어뜨리면 그 날짜로 옮긴다. */
  droppable?: boolean;
}

/**
 * 날짜 탭. 여행 기간의 모든 날짜 + <b>맨 뒤에 항상 보관함</b>.
 *
 * <p>보관함이 마지막인 이유는 날짜가 시간 순서를 갖는 반면 보관함은 "아직 날짜를 못 정한 것"
 * 이라서다 — 중간에 끼우면 순서가 깨진 것처럼 보인다.
 *
 * <p><b>v2.1 — 탭이 도시를 말한다.</b> 어느 날 어느 도시에 있는지가 여기서 바로 읽혀야
 * 다구간 일정에서 길을 잃지 않는다. 다만 <b>도시가 하나뿐인 여행에서는 감춘다</b> — 같은
 * 이름을 열 번 반복하면 정보가 아니라 소음이다.
 */
export function DayTabs({
  days,
  archiveCount,
  selectedDate,
  singleCity,
  onSelectDate,
  onSelectArchive,
  onLongPressDay,
  droppable = false,
}: DayTabsProps) {
  return (
    <div
      role="tablist"
      aria-label="날짜"
      className="flex gap-1.5 overflow-x-auto pb-1"
    >
      {days.map((day) => (
        <Fragment key={day.date}>
          {/* 도시가 바뀌는 자리에 선을 하나 세운다 — 며칠째인지보다 "여기서 옮긴다"가 먼저
              읽혀야 한다. 첫날은 비교할 앞 날짜가 없어 서지 않는다. */}
          {day.cityChanged && (
            <div aria-hidden="true" className="bg-border my-1 w-px shrink-0" />
          )}
          <Chip
            dropId={droppable ? `day:${day.date}` : undefined}
            active={selectedDate === day.date}
            label={
              singleCity || !day.baseCity
                ? `${day.dayIndex}일차`
                : `${day.dayIndex} ${day.baseCity.name}`
            }
            wide={!singleCity && day.baseCity !== null}
            sub={`${formatShortDate(day.date)} ${day.weekday}`}
            weather={day.weather}
            onClick={() => onSelectDate(day.date)}
            onLongPress={() => onLongPressDay(day)}
          />
        </Fragment>
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
  /** 도시명이 붙는 칩은 조금 넓다. 짧은 도시명에서 칸이 들쭉날쭉해지지 않게. */
  wide?: boolean;
  /** 예보 범위(16일) 밖이면 없다 — 그 자리를 비운다. */
  weather?: DailyWeather | null;
  icon?: React.ReactNode;
  onClick: () => void;
  /** 없으면 롱프레스를 받지 않는다(보관함 칩에는 기준 도시가 없다). */
  onLongPress?: () => void;
  /** 있으면 드롭 대상으로 등록한다. */
  dropId?: string;
}

function Chip({
  active,
  label,
  sub,
  wide = false,
  weather,
  icon,
  onClick,
  onLongPress,
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
  // 일정 행의 드래그 진입(400ms)과 같은 훅을 쓰되 임계값만 다르다. 탭과 행은 서로 다른
  // 제스처 대상이라 두 판정이 동시에 돌 일이 없다.
  const longPress = useLongPress(
    onLongPress ?? (() => {}),
    onLongPress !== undefined,
    TAB_HOLD_MS,
  );

  return (
    <button
      ref={dropId ? setNodeRef : undefined}
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      {...longPress}
      className={cn(
        "flex shrink-0 flex-col items-start gap-0.5 rounded-lg border px-2.5 py-2 transition-colors",
        wide ? "min-w-[92px]" : "min-w-[78px]",
        active
          ? "bg-accent text-accent-foreground border-primary"
          : "bg-card border-border hover:bg-muted",
        // 이 칩 위에 떠 있다 — 놓으면 이 날짜로 간다는 걸 미리 보여준다.
        isOver && "border-primary ring-primary bg-accent ring-2",
      )}
    >
      <span className="flex max-w-[110px] items-center gap-1 truncate text-[13px] font-semibold">
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
