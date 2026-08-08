import { Archive, CalendarDays } from "lucide-react";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import type { BoardDay } from "@/features/travel/api/activities";

interface PickDaySheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 담을 대상 이름. 시트 설명에 그대로 들어간다. */
  placeName: string | null;
  days: BoardDay[];
  /** 날짜를 고르면 그 날짜, 보관함을 고르면 null. */
  onPick: (date: string | null) => void;
  pending?: boolean;
}

/**
 * 담을 날짜를 고르는 시트(§S-06). `1일차`~`N일차` 또는 `보관함`.
 *
 * <p>보관함이 선택지에 있어야 하는 이유: 여행 계획은 "가고 싶다"가 "언제 갈지"보다 먼저 정해진다.
 * 날짜를 강제하면 정하지 못한 곳을 아예 담지 못하게 된다.
 */
export function PickDaySheet({
  open,
  onOpenChange,
  placeName,
  days,
  onPick,
  pending = false,
}: PickDaySheetProps) {
  return (
    <BottomSheet
      open={open}
      onOpenChange={onOpenChange}
      title="어느 날에 담을까요?"
      description={placeName ?? undefined}
    >
      <div className="flex flex-col gap-2">
        {days.map((day) => (
          <button
            key={day.date}
            type="button"
            onClick={() => onPick(day.date)}
            disabled={pending}
            className="border-border hover:bg-accent flex items-center gap-2.5 rounded-lg border px-3 py-2.5 text-left text-sm disabled:opacity-50"
          >
            <CalendarDays className="text-muted-foreground size-4 shrink-0" />
            <span className="flex-1">{day.dayIndex}일차</span>
            <span className="text-muted-foreground text-xs">
              {day.date.slice(5)} ({day.weekday})
            </span>
          </button>
        ))}

        <button
          type="button"
          onClick={() => onPick(null)}
          disabled={pending}
          className="border-border hover:bg-accent mt-1 flex items-center gap-2.5 rounded-lg border px-3 py-2.5 text-left text-sm disabled:opacity-50"
        >
          <Archive className="text-muted-foreground size-4 shrink-0" />
          <span className="flex-1">보관함</span>
          <span className="text-muted-foreground text-xs">날짜는 나중에</span>
        </button>
      </div>
    </BottomSheet>
  );
}
