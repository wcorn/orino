import { Alert } from "@/components/ui/alert";
import { LoadingText } from "@/components/ui/loading-text";
import type { CalendarDay } from "@/features/ledger/api/ledger";
import { useLedgerCalendar } from "@/features/ledger/hooks/useLedgerQueries";
import { formatAmount, MINUS } from "@/features/ledger/lib/money";
import { cn } from "@/lib/utils";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

/**
 * 캘린더 뷰(`LDG-021`).
 *
 * <p><b>과거는 실제 지출, 미래는 예정을 연하게.</b> 25일 급여와 14일 카드 대금 뭉텅이가
 * 달력만 봐도 보여야 한다(확정 명세 §8.3).
 *
 * <p>예정 쪽은 서버가 <b>예정 목록과 같은 4출처</b>에서 계산해 준다 — 직접 예약만 세면
 * 예정 화면에는 있는 카드 대금이 캘린더에는 없게 되고, 두 화면이 서로 다른 말을 하는 것이
 * 이 모듈에서 가장 나쁜 종류의 버그다.
 */
export function LedgerCalendar({ month }: { month: string }) {
  const { data, isPending, isError } = useLedgerCalendar(month);

  if (isPending) {
    return <LoadingText />;
  }
  if (isError || !data) {
    return <Alert variant="destructive">캘린더를 불러오지 못했어요.</Alert>;
  }

  const byDate = new Map(data.days.map((day) => [day.date, day]));
  const first = new Date(`${month}-01T00:00:00`);
  const lastDay = new Date(
    first.getFullYear(),
    first.getMonth() + 1,
    0,
  ).getDate();
  // 1일이 무슨 요일인지에 따라 앞을 비운다. 달력은 요일이 맞아야 달력이다.
  const leading = first.getDay();

  return (
    <div className="flex flex-col gap-1">
      <div className="grid grid-cols-7 gap-1">
        {WEEKDAYS.map((label, index) => (
          <span
            key={label}
            className={cn(
              "text-caption py-1 text-center font-medium",
              index === 0 && "text-destructive",
              index === 6 && "text-info",
            )}
          >
            {label}
          </span>
        ))}
      </div>
      <div
        className="grid grid-cols-7 gap-1"
        style={{ gridAutoRows: "minmax(84px,auto)" }}
      >
        {Array.from({ length: leading }, (_, index) => (
          <span key={`lead-${index}`} aria-hidden />
        ))}
        {Array.from({ length: lastDay }, (_, index) => {
          const date = `${month}-${String(index + 1).padStart(2, "0")}`;
          return (
            <DayCell
              key={date}
              date={date}
              day={byDate.get(date)}
              isToday={date === data.todayLine}
            />
          );
        })}
      </div>
    </div>
  );
}

function DayCell({
  date,
  day,
  isToday,
}: {
  date: string;
  day: CalendarDay | undefined;
  isToday: boolean;
}) {
  const scheduled = day ? day.scheduledExpense + day.scheduledTransfer : 0;

  return (
    <div
      className={cn(
        "border-border flex flex-col gap-0.5 rounded-md border p-1.5",
        isToday && "shadow-[inset_0_0_0_2px_var(--primary)]",
      )}
    >
      <span
        className={cn(
          "text-caption tabular-nums",
          isToday ? "text-primary font-semibold" : "text-muted-foreground",
        )}
      >
        {Number(date.slice(8))}
      </span>
      {day && day.income > 0 && (
        <span className="text-success text-caption tabular-nums">
          +{formatAmount(day.income)}
        </span>
      )}
      {day && day.expense > 0 && (
        <span className="text-caption tabular-nums">
          {MINUS}
          {formatAmount(day.expense)}
        </span>
      )}
      {/* 예정은 연하게. 이미 쓴 돈과 같은 굵기로 보이면 두 가지가 섞인다. */}
      {scheduled > 0 && (
        <span className="text-muted-foreground text-caption tabular-nums">
          예정 {formatAmount(scheduled)}
        </span>
      )}
      {day && day.scheduledIncome > 0 && (
        <span className="text-muted-foreground text-caption tabular-nums">
          예정 +{formatAmount(day.scheduledIncome)}
        </span>
      )}
    </div>
  );
}
