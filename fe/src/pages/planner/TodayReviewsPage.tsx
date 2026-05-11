import { useCallback, useEffect, useRef, useState } from "react";

import type { Rating, TodayReview } from "@/features/review/api/reviews";
import { EmptyTodayState } from "@/features/review/components/EmptyTodayState";
import { ReviewCard } from "@/features/review/components/ReviewCard";
import { useCompleteReview } from "@/features/review/hooks/useCompleteReview";
import { useTodayReviews } from "@/features/review/hooks/useTodayReviews";
import { toast } from "@/shared/lib/toast";

const FADE_OUT_MS = 200;

export function TodayReviewsPage() {
  const { data, isLoading, isError } = useTodayReviews();
  const completeReview = useCompleteReview();

  const [leavingIds, setLeavingIds] = useState<Set<number>>(new Set());
  const [pending, setPending] = useState<Map<number, Rating>>(new Map());
  const leaveTimeouts = useRef<Map<number, ReturnType<typeof setTimeout>>>(
    new Map(),
  );

  const handleRate = useCallback(
    async (review: TodayReview, rating: Rating) => {
      if (pending.has(review.id) || leavingIds.has(review.id)) return;

      setPending((prev) => new Map(prev).set(review.id, rating));
      try {
        const result = await completeReview.mutateAsync({
          reviewId: review.id,
          rating,
        });

        setLeavingIds((prev) => new Set(prev).add(review.id));
        const timer = setTimeout(() => {
          setLeavingIds((prev) => {
            const next = new Set(prev);
            next.delete(review.id);
            return next;
          });
          leaveTimeouts.current.delete(review.id);
        }, FADE_OUT_MS);
        leaveTimeouts.current.set(review.id, timer);

        const next = result.nextReview;
        toast(
          `다음 복습: ${next.intervalDays}일 후 (${next.scheduledDate})`,
          "success",
        );
      } catch {
        toast("복습 평가에 실패했어요. 잠시 후 다시 시도해주세요.", "error");
      } finally {
        setPending((prev) => {
          const next = new Map(prev);
          next.delete(review.id);
          return next;
        });
      }
    },
    [completeReview, pending, leavingIds],
  );

  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if (
        event.target instanceof HTMLElement &&
        (event.target.tagName === "INPUT" ||
          event.target.tagName === "TEXTAREA" ||
          event.target.isContentEditable)
      ) {
        return;
      }
      const map: Record<string, Rating> = {
        "1": "AGAIN",
        "2": "HARD",
        "3": "GOOD",
        "4": "EASY",
      };
      const rating = map[event.key];
      if (!rating || !data) return;
      const target = data.reviews.find(
        (r) => !leavingIds.has(r.id) && !pending.has(r.id),
      );
      if (target) {
        event.preventDefault();
        handleRate(target, rating);
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [data, handleRate, leavingIds, pending]);

  useEffect(() => {
    const timers = leaveTimeouts.current;
    return () => {
      timers.forEach((timer) => clearTimeout(timer));
      timers.clear();
    };
  }, []);

  if (isLoading) {
    return <p className="text-muted-foreground text-sm">불러오는 중...</p>;
  }

  if (isError || !data) {
    return (
      <p className="text-destructive text-sm">
        오늘 복습을 불러오지 못했어요.
      </p>
    );
  }

  const reviews = data.reviews;
  const overdueCount = reviews.filter((r) => r.delayDays > 0).length;

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-xl font-semibold">오늘 복습</h1>
        {reviews.length > 0 && (
          <p className="text-muted-foreground text-sm">
            {reviews.length}건
            {overdueCount > 0 && ` (밀린 ${overdueCount}건)`}
          </p>
        )}
      </header>

      {reviews.length === 0 ? (
        <EmptyTodayState />
      ) : (
        <ul className="flex flex-col gap-3">
          {reviews.map((review) => (
            <li key={review.id}>
              <ReviewCard
                review={review}
                onRate={handleRate}
                pendingRating={pending.get(review.id) ?? null}
                leaving={leavingIds.has(review.id)}
              />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
