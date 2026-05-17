import { useEffect, useState } from "react";

import { type TodayReview } from "@/features/review/api/reviews";
import { CompletionState } from "@/features/review/components/CompletionState";
import { EmptyTodayState } from "@/features/review/components/EmptyTodayState";
import { ReviewCard } from "@/features/review/components/ReviewCard";
import { useCompleteReview } from "@/features/review/hooks/useCompleteReview";
import { useTodayReviews } from "@/features/review/hooks/useTodayReviews";
import { toast } from "@/shared/lib/toast";

export function TodayReviewsPage() {
  const { data, isLoading, isError } = useTodayReviews();
  const completeMutation = useCompleteReview();
  const [currentIndex, setCurrentIndex] = useState(0);
  const [queue, setQueue] = useState<TodayReview[] | null>(null);

  useEffect(() => {
    if (data && queue === null) {
      setQueue(data.reviews);
    }
  }, [data, queue]);

  if (isLoading || (data && queue === null)) {
    return <p className="text-muted-foreground text-sm">불러오는 중...</p>;
  }
  if (isError || !data || !queue) {
    return (
      <p className="text-destructive text-sm">복습을 불러오지 못했어요.</p>
    );
  }

  const reviews = queue;
  const total = reviews.length;

  if (total === 0) {
    return <EmptyTodayState />;
  }
  if (currentIndex >= total) {
    return <CompletionState count={total} />;
  }

  const current = reviews[currentIndex];

  const handleRate = (rating: "AGAIN" | "HARD" | "GOOD" | "EASY") => {
    if (completeMutation.isPending) return;
    completeMutation.mutate(
      { reviewId: current.id, rating },
      {
        onSuccess: (res) => {
          toast(
            formatNextReviewMessage(res.nextReview.scheduledDate),
            "success",
          );
          setCurrentIndex((i) => i + 1);
        },
      },
    );
  };

  return (
    <div className="flex flex-col gap-4">
      <div className="text-muted-foreground text-center text-sm">
        {currentIndex + 1} / {total}
      </div>
      <ReviewCard
        review={current}
        pending={completeMutation.isPending}
        onRate={handleRate}
      />
    </div>
  );
}

function formatNextReviewMessage(scheduledDate: string): string {
  const today = new Date();
  const todayMid = new Date(
    today.getFullYear(),
    today.getMonth(),
    today.getDate(),
  );
  const next = parseDate(scheduledDate);
  const days = Math.round(
    (next.getTime() - todayMid.getTime()) / (1000 * 60 * 60 * 24),
  );
  const md = `${next.getMonth() + 1}/${next.getDate()}`;
  if (days <= 0) {
    return `다음 복습은 오늘 (${md})`;
  }
  return `다음 복습은 ${days}일 후 (${md})`;
}

function parseDate(isoDate: string): Date {
  const [y, m, d] = isoDate.split("-").map(Number);
  return new Date(y, m - 1, d);
}
