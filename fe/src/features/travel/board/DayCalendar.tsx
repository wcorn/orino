import { useDroppable } from "@dnd-kit/core";
import {
  Archive,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Umbrella,
} from "lucide-react";
import { Fragment, useState } from "react";

import { Button } from "@/components/ui/button";
import type { BoardDay } from "@/features/travel/api/activities";
import { formatShortDate } from "@/features/travel/lib/tripStatus";
import { iconFor, needsUmbrella } from "@/features/travel/tools/weatherIcon";
import { cn } from "@/lib/utils";

import { TAB_HOLD_MS, useLongPress } from "./useLongPress";

interface DayCalendarProps {
  days: BoardDay[];
  archiveCount: number;
  /** 선택된 날짜. null이면 보관함이 활성이다. */
  selectedDate: string | null;
  /** 전 기간이 한 도시면 도시명을 감춘다 — 같은 이름의 반복은 정보가 아니다. */
  singleCity: boolean;
  onSelectDate: (date: string) => void;
  onSelectArchive: () => void;
  /** 450ms 길게 눌러 그 날짜의 기준 도시 시트를 연다. */
  onLongPressDay: (day: BoardDay) => void;
  /** 드래그 중이면 날짜 칸이 드롭 대상이 된다 — 여기로 떨어뜨리면 그 날짜로 옮긴다. */
  droppable?: boolean;
  /**
   * 접혀 있어도 강제로 펼친다. 드래그 모드에서 쓴다 — 접힌 달력에는 날짜 칸이 없어
   * 떨어뜨릴 곳이 사라진다.
   */
  forceOpen?: boolean;
}

/**
 * 날짜 선택 달력(§S-04).
 *
 * <p><b>왜 가로 칩 리스트를 버렸나(#1213).</b> 칩 하나가 78~132px이라 모바일 360px에서
 * 서너 개밖에 안 들어갔다. 8일차를 고르려면 좌우로 밀어야 했고, 미는 동안 여행 전체에서
 * 지금 어디쯤인지 감각을 잃었다. 게다가 제일 큰 글씨가 실제로는 거의 안 쓰는 `N일차`였다.
 *
 * <p>그래서 <b>날짜가 메인, 도시가 서브</b>다. 여행 기간 전체를 7열 요일 그리드로 접으면
 * 20일짜리도 스크롤 없이 한 화면에 들어오고, 선택일은 스크롤 위치와 무관하게 늘 맨 위에
 * 큰 글씨로 남는다. 도시는 날짜 아래 얇은 구간 바가 맡는다 — 매 칸에 도시명을 반복하는
 * 대신 <b>어디서 바뀌는지</b>만 경계로 보여준다.
 */
export function DayCalendar({
  days,
  archiveCount,
  selectedDate,
  singleCity,
  onSelectDate,
  onSelectArchive,
  onLongPressDay,
  droppable = false,
  forceOpen = false,
}: DayCalendarProps) {
  const [expanded, setExpanded] = useState(true);
  const open = expanded || forceOpen;

  const selectedIndex = days.findIndex((day) => day.date === selectedDate);
  const selectedDay = selectedIndex >= 0 ? days[selectedIndex] : null;
  const prev = selectedIndex > 0 ? days[selectedIndex - 1] : null;
  const next =
    selectedIndex >= 0 && selectedIndex < days.length - 1
      ? days[selectedIndex + 1]
      : null;

  const weeks = weeksOf(days);
  const tints = cityTints(days);

  return (
    <div role="tablist" aria-label="날짜" className="flex flex-col gap-1.5">
      <div className="flex items-center gap-1">
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label="이전 날짜"
          disabled={!prev}
          onClick={() => prev && onSelectDate(prev.date)}
        >
          <ChevronLeft className="size-4" />
        </Button>
        {/* 선택일은 달력을 접든 펴든 늘 여기 있다 — "지금 며칠을 보고 있나"에 답하는 자리다. */}
        <button
          type="button"
          aria-expanded={open}
          aria-label={open ? "달력 접기" : "달력 펼치기"}
          onClick={() => setExpanded(!expanded)}
          className="hover:bg-muted flex min-w-0 flex-1 items-center justify-center gap-1.5 rounded-lg px-1 py-1.5 transition-colors"
        >
          {selectedDay ? (
            <SelectedDayLabel day={selectedDay} singleCity={singleCity} />
          ) : (
            <span className="flex items-center gap-1.5 text-[15px] font-semibold">
              <Archive className="size-4" />
              미배정 보관함
            </span>
          )}
          <ChevronDown
            aria-hidden="true"
            className={cn(
              "text-muted-foreground size-4 shrink-0 transition-transform",
              open && "rotate-180",
            )}
          />
        </button>
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label="다음 날짜"
          disabled={!next}
          onClick={() => next && onSelectDate(next.date)}
        >
          <ChevronRight className="size-4" />
        </Button>
      </div>

      {open && (
        <div className="flex flex-col gap-1">
          <div
            aria-hidden="true"
            className="text-caption text-muted-foreground grid grid-cols-7 text-center"
          >
            {WEEKDAYS.map((label, index) => (
              <span
                key={label}
                className={cn(
                  index === 0 && "text-destructive/70",
                  index === 6 && "text-info/70",
                )}
              >
                {label}
              </span>
            ))}
          </div>
          {weeks.map((week, weekIndex) => (
            <Fragment key={weekIndex}>
              <div className="grid grid-cols-7 gap-0.5">
                {week.map((day, slot) =>
                  day ? (
                    <DayCell
                      key={day.date}
                      day={day}
                      selected={day.date === selectedDate}
                      dropId={droppable ? `day:${day.date}` : undefined}
                      onClick={() => onSelectDate(day.date)}
                      onLongPress={() => onLongPressDay(day)}
                    />
                  ) : (
                    // 여행 기간 밖. 자리는 지키되 아무것도 그리지 않는다.
                    <div key={`empty-${slot}`} aria-hidden="true" />
                  ),
                )}
              </div>
              {/* 도시가 하나뿐인 여행에서는 이 줄이 아예 없다 — 같은 이름의 긴 바는 소음이다. */}
              {!singleCity && (
                <div
                  aria-hidden="true"
                  data-city-band=""
                  className="grid grid-cols-7 gap-0.5"
                >
                  {citySegments(week).map((segment) => (
                    <span
                      key={segment.start}
                      style={{
                        gridColumn: `${segment.start + 1} / span ${segment.span}`,
                        background: `var(--cell-bg-${tints.get(segment.key)})`,
                      }}
                      className="truncate rounded-full px-1 text-center text-[10px] leading-4"
                    >
                      {segment.name}
                    </span>
                  ))}
                </div>
              )}
            </Fragment>
          ))}
        </div>
      )}

      {/* 보관함은 날짜가 아니라 "아직 날짜를 못 정한 것"이라 달력 밖에 따로 선다. */}
      <ArchiveTab
        count={archiveCount}
        active={selectedDate === null}
        dropId={droppable ? "day:archive" : undefined}
        onClick={onSelectArchive}
      />
    </div>
  );
}

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

/**
 * 도시 구간 바에 쓰는 색. 셀 배경 팔레트(`--cell-bg-*`)를 그대로 빌린다 — 라이트·다크 양쪽에
 * 이미 대비가 맞춰져 있는 유일한 연한 배경 묶음이다. 도시가 7개를 넘으면 색이 돌아온다.
 */
const CITY_TINTS = ["blue", "green", "orange", "purple", "yellow", "red"];

/**
 * 요일 인덱스(일=0). <b>UTC로 만든다</b> — 날짜 문자열은 벽시계 값이라 기기 타임존으로
 * 파싱하면 자정 근처에서 하루가 밀린다.
 */
function weekdayIndex(date: string): number {
  const [year, month, day] = date.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, day)).getUTCDay();
}

/** `2026-10-24` → `24`. 달력 칸에는 일(日)만 쓴다 — 월은 헤더가 말한다. */
function dayOfMonth(date: string): number {
  return Number(date.split("-")[2]);
}

/**
 * 여행 기간을 주 단위로 접는다. 첫 주 앞과 마지막 주 뒤의 빈 자리는 `null`이다 — 칸을
 * 비워 둬야 요일 열이 맞는다.
 */
function weeksOf(days: BoardDay[]): (BoardDay | null)[][] {
  if (days.length === 0) return [];
  const weeks: (BoardDay | null)[][] = [];
  let week: (BoardDay | null)[] = Array<BoardDay | null>(
    weekdayIndex(days[0].date),
  ).fill(null);
  for (const day of days) {
    week.push(day);
    if (week.length === 7) {
      weeks.push(week);
      week = [];
    }
  }
  if (week.length > 0) {
    weeks.push([
      ...week,
      ...Array<BoardDay | null>(7 - week.length).fill(null),
    ]);
  }
  return weeks;
}

/** 도시 식별자. 이름으로 묶지 않는다 — 같은 이름의 다른 도시가 있을 수 있다. */
function cityKeyOf(day: BoardDay): string | null {
  const city = day.baseCity;
  if (!city) return null;
  return city.cityPlaceRef ?? String(city.placeId);
}

/** 도시별 색을 <b>처음 등장한 순서</b>로 고정한다. 날짜를 넘겨도 같은 도시는 같은 색이다. */
function cityTints(days: BoardDay[]): Map<string, string> {
  const tints = new Map<string, string>();
  for (const day of days) {
    const key = cityKeyOf(day);
    if (key !== null && !tints.has(key)) {
      tints.set(key, CITY_TINTS[tints.size % CITY_TINTS.length]);
    }
  }
  return tints;
}

interface CitySegment {
  /** 그 주에서 시작하는 열(0=일요일). */
  start: number;
  span: number;
  key: string;
  name: string;
}

/**
 * 한 주 안에서 <b>연속으로 같은 도시인 날들</b>을 하나로 묶는다. 도시가 바뀌는 날은 도착한
 * 도시 쪽에 붙는다(`baseCity`) — 그래야 바의 경계가 "여기서 옮긴다"와 같은 자리에 선다.
 */
function citySegments(week: (BoardDay | null)[]): CitySegment[] {
  const segments: CitySegment[] = [];
  week.forEach((day, index) => {
    const key = day ? cityKeyOf(day) : null;
    if (!day || key === null) return;
    const last = segments[segments.length - 1];
    if (last && last.key === key && last.start + last.span === index) {
      last.span += 1;
      return;
    }
    segments.push({ start: index, span: 1, key, name: day.baseCity!.name });
  });
  return segments;
}

/**
 * 헤더의 선택일 — `10.24 (토) · 교토 ☀ 18°/9°`.
 *
 * <p>도시가 바뀌는 날은 두 도시를 다 쓰고 날씨를 뺀다 — 바로 아래 `CityMoveLine`이 두 도시의
 * 날씨를 각각 말한다. 여기서 도착 도시 날씨만 겹쳐 쓰면 오전에 뭘 입을지가 틀리게 읽힌다.
 */
function SelectedDayLabel({
  day,
  singleCity,
}: {
  day: BoardDay;
  singleCity: boolean;
}) {
  const moving = Boolean(day.arrivingFrom);
  const city = singleCity || !day.baseCity ? null : cityLabelOf(day);
  const weather = moving ? null : day.weather;
  const Glyph = weather
    ? iconFor(weather.icon, weather.precipProbability)
    : null;
  const alert = weather ? needsUmbrella(weather.precipProbability) : false;
  return (
    <>
      <span className="text-[15px] font-semibold tabular-nums">
        {formatShortDate(day.date)}
      </span>
      <span className="text-muted-foreground shrink-0 text-[13px]">
        ({day.weekday})
      </span>
      {city && (
        <span className="text-muted-foreground truncate text-[13px]">
          · {city}
        </span>
      )}
      {weather && Glyph && (
        <span
          className={cn(
            "flex shrink-0 items-center gap-0.5 text-[12px] tabular-nums",
            alert ? "text-warning" : "text-muted-foreground",
          )}
        >
          <Glyph className="size-3" />
          {weather.tempMax ?? "–"}°/{weather.tempMin ?? "–"}°
        </span>
      )}
    </>
  );
}

/** 도시가 바뀌는 날은 `오사카 → 교토` — 그 하루는 두 도시에 속한다(D-25). */
function cityLabelOf(day: BoardDay): string {
  const from = day.arrivingFrom;
  return from ? `${from.name} → ${day.baseCity!.name}` : day.baseCity!.name;
}

interface DayCellProps {
  day: BoardDay;
  selected: boolean;
  dropId?: string;
  onClick: () => void;
  onLongPress: () => void;
}

/**
 * 날짜 한 칸. 숫자 + 그날의 표시(일정 개수·비 예보)뿐이다.
 *
 * <p>칸이 모바일에서 48px 남짓이라 글자를 더 넣으면 전부 잘린다. 그래서 <b>개수는 점으로</b>
 * 센다 — "일정이 있나/많나"는 점으로 읽히고, 무엇인지는 어차피 아래 목록이 답한다.
 */
function DayCell({
  day,
  selected,
  dropId,
  onClick,
  onLongPress,
}: DayCellProps) {
  const { setNodeRef, isOver } = useDroppable({
    id: dropId ?? "",
    disabled: !dropId,
  });
  // 일정 행의 드래그 진입(400ms)과 같은 훅을 쓰되 반 박자 길게 잡는다. 날짜를 고르려던
  // 짧은 탭이 시트를 여는 일이 없어야 한다.
  const longPress = useLongPress(onLongPress, true, TAB_HOLD_MS);
  const rain = day.weather
    ? needsUmbrella(day.weather.precipProbability)
    : false;
  const dots = Math.min(day.activityCount, 3);

  return (
    <button
      ref={dropId ? setNodeRef : undefined}
      type="button"
      role="tab"
      aria-selected={selected}
      aria-label={ariaLabelOf(day)}
      onClick={onClick}
      {...longPress}
      className={cn(
        "flex min-h-11 flex-col items-center justify-center gap-1 rounded-lg border text-[13px] transition-colors",
        selected
          ? "bg-primary text-primary-foreground border-primary font-bold"
          : "border-transparent font-medium",
        // 이 칸 위에 떠 있다 — 놓으면 이 날짜로 간다는 걸 미리 보여준다.
        isOver && "border-primary ring-primary bg-accent ring-2",
      )}
    >
      <span className="tabular-nums">{dayOfMonth(day.date)}</span>
      <span className="flex h-2.5 items-center justify-center gap-[3px]">
        {rain && (
          <Umbrella
            aria-hidden="true"
            className={cn("size-2.5", !selected && "text-warning")}
          />
        )}
        {Array.from({ length: dots }, (_, index) => (
          <span
            key={index}
            className={cn(
              "size-[3px] rounded-full",
              selected ? "bg-primary-foreground" : "bg-muted-foreground",
            )}
          />
        ))}
      </span>
    </button>
  );
}

/** 칸에는 숫자만 남으니 나머지는 이름으로 읽힌다 — `10.24 토 · 교토 · 일정 2개`. */
function ariaLabelOf(day: BoardDay): string {
  const parts = [`${formatShortDate(day.date)} ${day.weekday}`];
  if (day.baseCity) parts.push(cityLabelOf(day));
  parts.push(`일정 ${day.activityCount}개`);
  return parts.join(" · ");
}

function ArchiveTab({
  count,
  active,
  dropId,
  onClick,
}: {
  count: number;
  active: boolean;
  dropId?: string;
  onClick: () => void;
}) {
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
        "flex items-center justify-between rounded-lg border px-3 py-2 text-[13px] transition-colors",
        active
          ? "bg-accent text-accent-foreground border-primary font-semibold"
          : "bg-card border-border hover:bg-muted",
        isOver && "border-primary ring-primary bg-accent ring-2",
      )}
    >
      <span className="flex items-center gap-1.5">
        <Archive className="size-3.5" />
        보관함
      </span>
      <span className="text-muted-foreground tabular-nums">{count}개</span>
    </button>
  );
}
