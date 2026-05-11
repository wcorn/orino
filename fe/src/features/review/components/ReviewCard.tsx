import { AlertTriangle, BookOpen, GraduationCap, NotebookPen, Video } from "lucide-react";

import { cn } from "@/lib/utils";

import type { Rating, TodayReview } from "../api/reviews";

const TYPE_ICON = {
  BOOK: BookOpen,
  LECTURE: Video,
  WORKBOOK: NotebookPen,
  MOOC: GraduationCap,
} as const;

interface RatingMeta {
  rating: Rating;
  label: string;
  shortcut: string;
  classes: string;
}

const RATINGS: RatingMeta[] = [
  {
    rating: "AGAIN",
    label: "Again",
    shortcut: "1",
    classes: "bg-red-500 hover:bg-red-500/90 text-white",
  },
  {
    rating: "HARD",
    label: "Hard",
    shortcut: "2",
    classes: "bg-orange-500 hover:bg-orange-500/90 text-white",
  },
  {
    rating: "GOOD",
    label: "Good",
    shortcut: "3",
    classes: "bg-primary hover:bg-primary/90 text-primary-foreground",
  },
  {
    rating: "EASY",
    label: "Easy",
    shortcut: "4",
    classes: "bg-green-500 hover:bg-green-500/90 text-white",
  },
];

function formatInterval(days: number): string {
  if (days < 1) return "<1d";
  if (days < 30) return `${days}d`;
  const months = Math.round(days / 30);
  return `${months}mo`;
}

interface ReviewCardProps {
  review: TodayReview;
  onRate: (review: TodayReview, rating: Rating) => void;
  pendingRating?: Rating | null;
  leaving?: boolean;
}

export function ReviewCard({
  review,
  onRate,
  pendingRating = null,
  leaving = false,
}: ReviewCardProps) {
  const Icon = TYPE_ICON[review.unit.material.type];

  return (
    <article
      aria-labelledby={`review-${review.id}-title`}
      className={cn(
        "border-border bg-card flex flex-col gap-3 rounded-xl border p-4 transition-all duration-200",
        leaving && "pointer-events-none -translate-y-1 opacity-0",
      )}
    >
      <header className="flex flex-col gap-1">
        <div className="flex items-center gap-2 text-xs">
          <Icon className="text-primary size-3.5" />
          <span className="text-muted-foreground truncate">
            {review.unit.material.title}
          </span>
        </div>
        <h2
          id={`review-${review.id}-title`}
          className="text-foreground text-base font-medium"
        >
          {review.unit.title}
        </h2>
        <div className="text-muted-foreground flex items-center gap-2 text-xs">
          <span>회차 {review.sequence}</span>
          <span>·</span>
          {review.delayDays > 0 ? (
            <span className="text-destructive inline-flex items-center gap-1">
              <AlertTriangle className="size-3" />
              {review.delayDays}일 지연
            </span>
          ) : (
            <span>오늘</span>
          )}
        </div>
      </header>

      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        {RATINGS.map((meta) => {
          const previewDays = review.preview[
            meta.rating.toLowerCase() as keyof typeof review.preview
          ];
          const isPending = pendingRating === meta.rating;
          return (
            <button
              key={meta.rating}
              type="button"
              onClick={() => onRate(review, meta.rating)}
              disabled={pendingRating !== null || leaving}
              className={cn(
                "group flex flex-col items-center gap-0.5 rounded-lg px-3 py-2 text-sm font-medium transition-all disabled:opacity-50",
                meta.classes,
              )}
              aria-label={`${meta.label} (단축키 ${meta.shortcut})`}
            >
              <span className="text-xs opacity-90">
                {isPending ? "..." : formatInterval(previewDays)}
              </span>
              <span>
                <span className="hidden opacity-80 sm:inline">
                  {meta.shortcut}.
                </span>{" "}
                {meta.label}
              </span>
            </button>
          );
        })}
      </div>
    </article>
  );
}
