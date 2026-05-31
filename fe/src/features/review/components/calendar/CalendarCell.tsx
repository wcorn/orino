import { cn } from "@/lib/utils";

import type { CalendarReview } from "../../api/calendar";
import { countBuckets } from "../../calendar";
import { BUCKET_DOT, BUCKET_ORDER } from "./bucketStyles";

interface Props {
  date: Date;
  isoDate: string;
  inMonth: boolean;
  isToday: boolean;
  isSelected: boolean;
  reviews: CalendarReview[];
  today: Date;
  onSelect: (isoDate: string) => void;
}

const MAX_DOTS = 4;

export function CalendarCell({
  date,
  isoDate,
  inMonth,
  isToday,
  isSelected,
  reviews,
  today,
  onSelect,
}: Props) {
  const counts = countBuckets(reviews, today);
  const dots = BUCKET_ORDER.flatMap((bucket) =>
    Array.from({ length: counts[bucket] }, () => bucket),
  );
  const shown = dots.slice(0, MAX_DOTS);
  const overflow = dots.length - shown.length;

  return (
    <button
      type="button"
      aria-label={`${isoDate}${reviews.length > 0 ? ` 복습 ${reviews.length}건` : ""}`}
      aria-pressed={isSelected}
      onClick={() => onSelect(isoDate)}
      className={cn(
        "flex min-h-16 flex-col gap-1 rounded-md border p-1.5 text-left transition-colors",
        inMonth ? "bg-card" : "bg-muted/30 text-muted-foreground",
        isToday ? "border-primary" : "border-border",
        isSelected && "ring-primary ring-2",
      )}
    >
      <span
        className={cn(
          "text-xs font-medium",
          isToday && "text-primary font-semibold",
        )}
      >
        {date.getDate()}
      </span>
      {shown.length > 0 && (
        <span className="flex flex-wrap items-center gap-0.5">
          {shown.map((bucket, i) => (
            <span
              key={i}
              className={cn("size-1.5 rounded-full", BUCKET_DOT[bucket])}
            />
          ))}
          {overflow > 0 && (
            <span className="text-muted-foreground text-[10px]">
              +{overflow}
            </span>
          )}
        </span>
      )}
    </button>
  );
}
