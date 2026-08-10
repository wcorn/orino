import { ChevronDown, ChevronUp, Minus, Plus, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import type { LegDates } from "@/features/travel/lib/legPlan";
import { formatShortDate } from "@/features/travel/lib/tripStatus";

interface LegRowProps {
  index: number;
  cityName: string;
  days: number;
  /** 이 구간이 차지할 날짜. 기간을 넘겨 잘리면 `null`이다. */
  dates: LegDates | null;
  onPickCity: () => void;
  onChangeDays: (days: number) => void;
  onMove: (direction: -1 | 1) => void;
  onRemove: () => void;
  canMoveUp: boolean;
  canMoveDown: boolean;
  canRemove: boolean;
}

/**
 * 구간 한 줄 — 번호 · 도시 · 일수 · 순서/삭제.
 *
 * <p>도시 아래 <b>자동 계산된 날짜 범위</b>를 보여준다. 구간은 날짜가 아니라 일수로 입력하는데
 * (기간이 움직여도 뒤 구간을 다시 고치지 않아도 되니까), 그러면 "이 도시가 며칠인지"는 알아도
 * "언제인지"를 모른다. 그 답을 여기서 바로 준다.
 */
export function LegRow({
  index,
  cityName,
  days,
  dates,
  onPickCity,
  onChangeDays,
  onMove,
  onRemove,
  canMoveUp,
  canMoveDown,
  canRemove,
}: LegRowProps) {
  return (
    <li className="bg-card grid grid-cols-[26px_1fr_auto_auto] items-center gap-2 rounded-lg border px-2.5 py-2">
      <span className="bg-muted flex size-[22px] items-center justify-center rounded-full text-xs font-semibold tabular-nums">
        {index + 1}
      </span>

      <div className="min-w-0">
        <button
          type="button"
          onClick={onPickCity}
          className="flex max-w-full items-center gap-1 text-[15px] font-medium"
          aria-label={`${index + 1}번째 구간 도시`}
        >
          <span className="truncate">{cityName || "도시 선택"}</span>
          <ChevronDown className="size-[13px] shrink-0" />
        </button>
        <p className="text-muted-foreground text-xs tabular-nums">
          {dates
            ? `${formatShortDate(dates.startDate)} – ${formatShortDate(dates.endDate)}`
            : "기간을 넘겨 잘려요"}
        </p>
      </div>

      <div className="flex items-center gap-0.5">
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label={`${cityName || "이 구간"} 일수 줄이기`}
          disabled={days <= 1}
          onClick={() => onChangeDays(days - 1)}
        >
          <Minus className="size-4" />
        </Button>
        <span className="w-9 text-center text-sm tabular-nums">{days}일</span>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label={`${cityName || "이 구간"} 일수 늘리기`}
          onClick={() => onChangeDays(days + 1)}
        >
          <Plus className="size-4" />
        </Button>
      </div>

      <div className="flex items-center gap-0.5">
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label={`${cityName || "이 구간"} 위로`}
          disabled={!canMoveUp}
          onClick={() => onMove(-1)}
        >
          <ChevronUp className="size-4" />
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label={`${cityName || "이 구간"} 아래로`}
          disabled={!canMoveDown}
          onClick={() => onMove(1)}
        >
          <ChevronDown className="size-4" />
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label={`${cityName || "이 구간"} 삭제`}
          disabled={!canRemove}
          onClick={onRemove}
        >
          <Trash2 className="size-4" />
        </Button>
      </div>
    </li>
  );
}
