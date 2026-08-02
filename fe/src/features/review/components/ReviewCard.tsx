import { useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import { MATERIAL_TYPE_ICONS } from "@/features/material/utils";
import { cn } from "@/lib/utils";

import type { Rating, TodayReview } from "../api/reviews";
import { OrderingReviewCard } from "./OrderingReviewCard";

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
    textClass: "text-destructive",
  },
  {
    rating: "HARD",
    label: "Hard",
    key: "2",
    previewLabel: (r) => `${r.preview.hard}d`,
    textClass: "text-warning",
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
    textClass: "text-success",
  },
];

export function ReviewCard({ review, pending, onRate }: Props) {
  const [revealed, setRevealed] = useState(false);
  // 머릿속으로만 떠올리면 답을 본 순간 "아 이거였지"가 돼 채점이 후해진다.
  // 적게 해서 리트리벌을 강제하고, 공개 후엔 정답과 나란히 놓고 채점하게 한다.
  // 저장하지 않는다 — 채점과 함께 사라지는 화면 전용 상태다.
  const [note, setNote] = useState("");
  const noteRef = useRef<HTMLTextAreaElement>(null);
  const isOrdering = review.flashcard.type === "ORDERING";

  useEffect(() => {
    setRevealed(false);
    setNote("");
  }, [review.id]);

  // 카드가 뜨면(그리고 다음 카드로 넘어가 입력칸이 다시 붙으면) 바로 타이핑할 수 있게 포커스.
  // 터치 기기는 제외한다 — 질문을 읽기도 전에 가상 키보드가 화면을 덮는다(#994와 같은 이유).
  useEffect(() => {
    if (revealed) return;
    if (window.matchMedia?.("(pointer: coarse)").matches) return;
    noteRef.current?.focus();
  }, [review.id, revealed]);

  const reveal = () => {
    // 입력칸에서 포커스를 뺀다 — 안 그러면 뒤이은 1~4 채점 키가 입력칸으로 흘러든다.
    noteRef.current?.blur();
    setRevealed(true);
  };

  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      // 입력칸 안에서 답을 여는 유일한 키. Enter는 줄바꿈이라 쓸 수 없다.
      if (
        !revealed &&
        (event.metaKey || event.ctrlKey) &&
        event.key === "Enter"
      ) {
        event.preventDefault();
        reveal();
        return;
      }
      if (isTypingTarget(event.target)) return;
      if (!revealed) {
        if (event.code === "Space" || event.key === "Enter") {
          // 순서 카드 드래그 핸들의 Space/Enter(항목 집기)에는 양보한다
          if (isInDragArea(event.target)) return;
          event.preventDefault();
          reveal();
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

        {isOrdering ? (
          <>
            <div className="bg-muted/30 rounded-lg px-4 py-5 text-center">
              <p className="text-foreground text-lg font-medium whitespace-pre-wrap">
                {review.flashcard.front}
              </p>
            </div>
            <OrderingReviewCard
              key={review.flashcard.id}
              items={review.flashcard.items ?? []}
              revealed={revealed}
            />
          </>
        ) : (
          <div className="bg-muted/30 rounded-lg px-4 py-10 text-center">
            <p className="text-foreground text-2xl font-medium whitespace-pre-wrap">
              {review.flashcard.front}
            </p>
          </div>
        )}

        {/* 내 답 — 공개 전엔 적는 칸, 공개 후엔 정답 바로 위에 읽기 전용으로 남아 비교 대상이 된다.
          빈칸이어도 답을 볼 수 있다(막지 않는다). 안 적었으면 공개 후엔 아무것도 남기지 않는다. */}
        {!revealed ? (
          <div className="flex flex-col gap-1.5">
            <label
              htmlFor="review-answer-note"
              className="text-muted-foreground text-xs font-medium"
            >
              내 답
            </label>
            <Textarea
              id="review-answer-note"
              ref={noteRef}
              rows={3}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="떠오르는 대로 적어보세요 (⌘/Ctrl+Enter로 답 보기)"
            />
          </div>
        ) : (
          note.trim() !== "" && (
            <section className="flex flex-col gap-1.5">
              <h3 className="text-muted-foreground text-xs font-medium">
                내 답
              </h3>
              <div className="bg-muted/30 rounded-lg px-4 py-3">
                <p className="text-foreground text-sm whitespace-pre-wrap">
                  {note}
                </p>
              </div>
            </section>
          )
        )}

        {!revealed ? (
          <div className="flex justify-center">
            <Button size="lg" onClick={reveal}>
              {isOrdering ? "정답 확인" : "답 보기"}
            </Button>
          </div>
        ) : (
          <>
            {!isOrdering && (
              // 위의 "내 답"과 나란히 놓고 비교하는 자리라 제목을 붙인다(순서 카드는
              // OrderingReviewCard가 "내 배열/정답 순서"로 이미 구분해준다).
              <section className="flex flex-col gap-1.5">
                <h3 className="text-muted-foreground text-xs font-medium">
                  정답
                </h3>
                <div className="bg-card border-border rounded-lg border px-4 py-6 text-center">
                  <p className="text-foreground text-lg whitespace-pre-wrap">
                    {review.flashcard.back}
                  </p>
                </div>
              </section>
            )}

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

/** 순서 카드 드래그 리스트 내부 요소인지(핸들 Space/Enter를 reveal에 뺏기지 않도록). */
function isInDragArea(target: EventTarget | null): boolean {
  return (
    target instanceof HTMLElement &&
    target.closest("[data-ordering-drag]") !== null
  );
}
