import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { MATERIAL_TYPE_ICONS } from "@/features/material/utils";
import { cn } from "@/lib/utils";

import type { Rating, TodayReview } from "../api/reviews";

interface Props {
  review: TodayReview;
  pending: boolean;
  onRate: (rating: Rating) => void;
}

interface RatingButton {
  rating: Rating;
  label: string;
  key: string;
  previewLabel: (r: TodayReview) => string;
  textClass: string;
}

const RATING_BUTTONS: RatingButton[] = [
  {
    rating: "AGAIN",
    label: "Again",
    key: "1",
    // AGAIN은 당일 10분 뒤 재복습 (일 단위 아님)
    previewLabel: () => "10분",
    textClass: "text-red-500",
  },
  {
    rating: "HARD",
    label: "Hard",
    key: "2",
    previewLabel: (r) => `${r.preview.hard}d`,
    textClass: "text-orange-500",
  },
  {
    rating: "GOOD",
    label: "Good",
    key: "3",
    previewLabel: (r) => `${r.preview.good}d`,
    textClass: "text-primary",
  },
  {
    rating: "EASY",
    label: "Easy",
    key: "4",
    previewLabel: (r) => `${r.preview.easy}d`,
    textClass: "text-green-500",
  },
];

export function ReviewCard({ review, pending, onRate }: Props) {
  const [revealed, setRevealed] = useState(false);

  useEffect(() => {
    setRevealed(false);
  }, [review.id]);

  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if (isTypingTarget(event.target)) return;
      if (!revealed) {
        if (event.code === "Space" || event.key === "Enter") {
          event.preventDefault();
          setRevealed(true);
        }
        return;
      }
      if (pending) return;
      const match = RATING_BUTTONS.find((b) => b.key === event.key);
      if (match) {
        event.preventDefault();
        onRate(match.rating);
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [revealed, pending, onRate]);

  return (
    <Card className="mx-auto w-full max-w-2xl">
      <CardContent className="flex flex-col gap-6 py-6">
        <div className="text-muted-foreground flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
          <span className="text-base">
            {MATERIAL_TYPE_ICONS[review.flashcard.material.type]}
          </span>
          <span className="font-medium">{review.flashcard.material.title}</span>
          <span>{review.sequence}회차</span>
          {review.delayDays > 0 && (
            <span className="text-destructive">
              {review.delayDays}일 밀린 복습
            </span>
          )}
        </div>

        <div className="bg-muted/30 rounded-lg px-4 py-10 text-center">
          <p
            className={cn(
              "text-foreground text-2xl font-medium whitespace-pre-wrap",
            )}
          >
            {review.flashcard.front}
          </p>
        </div>

        {!revealed ? (
          <div className="flex justify-center">
            <Button size="lg" onClick={() => setRevealed(true)}>
              답 보기 (Space)
            </Button>
          </div>
        ) : (
          <>
            <div className="bg-card border-border rounded-lg border px-4 py-6 text-center">
              <p className="text-foreground text-lg whitespace-pre-wrap">
                {review.flashcard.back}
              </p>
            </div>

            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              {RATING_BUTTONS.map((btn) => {
                const previewLabel = btn.previewLabel(review);
                return (
                  <Button
                    key={btn.rating}
                    variant="outline"
                    size="lg"
                    disabled={pending}
                    onClick={() => onRate(btn.rating)}
                    aria-label={`${btn.label} (${btn.key})`}
                    className="flex h-auto flex-col gap-1 py-3"
                  >
                    <span className="text-muted-foreground text-xs">
                      {previewLabel}
                    </span>
                    <span className={cn("text-sm font-medium", btn.textClass)}>
                      {btn.label}
                    </span>
                    <span className="text-muted-foreground text-[10px]">
                      [{btn.key}]
                    </span>
                  </Button>
                );
              })}
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}

function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName;
  return tag === "INPUT" || tag === "TEXTAREA" || target.isContentEditable;
}
