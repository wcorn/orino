import { ArrowLeftRight, CalendarDays } from "lucide-react";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import type { BoardDay } from "@/features/travel/api/activities";
import { formatDateWithWeekday } from "@/features/travel/lib/tripStatus";

interface SwapDaySheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 지금 보고 있는 날짜. 목록에서 빠지고, 설명 줄에 이름으로 들어간다. */
  from: BoardDay;
  /** 여행의 모든 날짜. 여기서 `from`을 걸러 그린다. */
  days: BoardDay[];
  onPick: (day: BoardDay) => void;
  pending?: boolean;
}

/** `10.26 (월) · 교토` — 어느 도시의 날짜인지가 날짜만큼 중요하다(다구간 여행). */
function dayLabel(day: BoardDay) {
  return `${formatDateWithWeekday(day.date)}${
    day.baseCity ? ` · ${day.baseCity.name}` : ""
  }`;
}

/**
 * 하루를 통째로 맞바꿀 날짜를 고르는 시트.
 *
 * <p><b>일정 수를 함께 보여준다.</b> 맞바꾸면 저쪽 일정이 이쪽으로 오는 것이라, 고르기 전에
 * 무엇이 건너오는지가 보여야 한다. 비어 있는 날짜는 `비어 있음`이라 적어 — 그 칸을 고르면
 * 교체가 아니라 통째 이동이 된다는 걸 미리 알린다.
 *
 * <p>보관함은 선택지에 없다. 거기엔 맞바꿀 하루가 없고, 날짜별 순서도 없다.
 */
export function SwapDaySheet({
  open,
  onOpenChange,
  from,
  days,
  onPick,
  pending = false,
}: SwapDaySheetProps) {
  return (
    <BottomSheet
      open={open}
      onOpenChange={onOpenChange}
      title="어느 날짜와 바꿀까요?"
      description={`${dayLabel(from)}의 일정이 고른 날짜로 통째로 옮겨가고, 그 날짜의 일정이 이리로 옵니다. 기준 도시와 숙소는 날짜에 그대로 남습니다.`}
    >
      <div className="flex flex-col gap-2">
        {days
          .filter((day) => day.date !== from.date)
          .map((day) => (
            <button
              key={day.date}
              type="button"
              onClick={() => onPick(day)}
              disabled={pending}
              className="border-border hover:bg-accent flex items-center gap-2.5 rounded-lg border px-3 py-2.5 text-left text-sm disabled:opacity-50"
            >
              <CalendarDays className="text-muted-foreground size-4 shrink-0" />
              <span className="flex-1 truncate">{dayLabel(day)}</span>
              <span className="text-muted-foreground text-xs">
                {day.activityCount > 0
                  ? `일정 ${day.activityCount}개`
                  : "비어 있음"}
              </span>
              <ArrowLeftRight className="text-muted-foreground size-4 shrink-0" />
            </button>
          ))}
      </div>
    </BottomSheet>
  );
}
