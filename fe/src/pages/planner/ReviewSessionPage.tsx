import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { FieldError } from "@/components/ui/field-error";
import { LoadingText } from "@/components/ui/loading-text";
import { type TodayReview } from "@/features/review/api/reviews";
import { CompletionState } from "@/features/review/components/CompletionState";
import { EmptyTodayState } from "@/features/review/components/EmptyTodayState";
import { ReviewCard } from "@/features/review/components/ReviewCard";
import { useCompleteReview } from "@/features/review/hooks/useCompleteReview";
import { useTodayReviews } from "@/features/review/hooks/useTodayReviews";
import { shuffleDeck } from "@/features/review/ordering";
import { toast } from "@/shared/lib/toast";

/**
 * 복습 세션(카드 넘기기). 허브의 시작 액션에서 진입하며 스코프/자료를 URL 쿼리
 * (`?scope=all|overdue&materialId=`)로 받는다. 세션 BE는 무변경 — `GET /reviews/today`(지금 due)를
 * 받아 스코프/자료를 **클라이언트에서 필터**해 큐를 구성한다.
 */
export function ReviewSessionPage() {
  const [searchParams] = useSearchParams();
  const scope = searchParams.get("scope") ?? "all";
  const materialIdParam = searchParams.get("materialId");
  const materialId = materialIdParam ? Number(materialIdParam) : null;

  const { data, isLoading, isError } = useTodayReviews();
  const completeMutation = useCompleteReview();
  const [currentIndex, setCurrentIndex] = useState(0);
  const [queue, setQueue] = useState<TodayReview[] | null>(null);

  useEffect(() => {
    if (data && queue === null) {
      const filtered = data.reviews.filter((r) => {
        if (scope === "overdue" && r.delayDays <= 0) return false;
        if (materialId !== null && r.flashcard.material.id !== materialId) {
          return false;
        }
        return true;
      });
      // 세션 진입마다 순서를 섞는다(순서 암기 방지). 큐는 한 번만 만들어 렌더 중 재셔플되지 않는다.
      setQueue(shuffleDeck(filtered));
    }
  }, [data, queue, scope, materialId]);

  if (isLoading || (data && queue === null)) {
    return <LoadingText />;
  }
  if (isError || !data || !queue) {
    return <FieldError>복습을 불러오지 못했어요.</FieldError>;
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
          toast(formatNextReviewMessage(res.nextReview.scheduledAt), "success");
          // sibling burying — 밀려난 짝 복습을 세션 큐에서 제거(분모도 함께 감소)
          const buried = res.buriedReviewIds ?? [];
          if (buried.length > 0) {
            setQueue((q) =>
              q === null ? q : q.filter((r) => !buried.includes(r.id)),
            );
            toast("짝 카드는 다른 날 복습해요.", "info");
          }
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

function formatNextReviewMessage(scheduledAt: string): string {
  const now = new Date();
  const next = new Date(scheduledAt);

  // 당일 짧은 재복습(AGAIN 등)은 분/시간 단위로 안내
  const diffMin = Math.round((next.getTime() - now.getTime()) / 60000);
  if (diffMin <= 0) {
    return "다음 복습이 곧 다시 표시됩니다";
  }
  if (diffMin < 60) {
    return `다음 복습은 약 ${diffMin}분 후`;
  }

  const todayMid = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const nextMid = new Date(next.getFullYear(), next.getMonth(), next.getDate());
  const days = Math.round(
    (nextMid.getTime() - todayMid.getTime()) / (1000 * 60 * 60 * 24),
  );
  const md = `${next.getMonth() + 1}/${next.getDate()}`;
  if (days <= 0) {
    return `다음 복습은 오늘 (${md})`;
  }
  return `다음 복습은 ${days}일 후 (${md})`;
}
