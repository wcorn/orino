import type {
  FlashcardType,
  OrderingItem,
} from "@/features/flashcard/api/flashcards";
import { client } from "@/shared/api";

export type Rating = "AGAIN" | "HARD" | "GOOD" | "EASY";

export interface TodayReviewMaterial {
  id: number;
  title: string;
  type: "BOOK" | "LECTURE" | "WORKBOOK" | "MOOC";
}

export interface TodayReviewFlashcard {
  id: number;
  type: FlashcardType;
  front: string;
  /** ORDERING 카드는 없음(null/생략). */
  back: string | null;
  /** ORDERING 카드만 정답 순서 배열. */
  items: OrderingItem[] | null;
  /** 양방향 짝 카드가 공유하는 그룹 키. 단방향 카드는 null. */
  siblingGroupId: number | null;
  material: TodayReviewMaterial;
}

export interface PreviewView {
  again: number;
  hard: number;
  good: number;
  easy: number;
}

export interface TodayReview {
  id: number;
  /** ISO datetime (예: "2026-06-07T04:00:00"). due = scheduledAt <= now */
  scheduledAt: string;
  delayDays: number;
  sequence: number;
  intervalDays: number;
  easeFactor: number;
  flashcard: TodayReviewFlashcard;
  preview: PreviewView;
}

export interface TodayReviewsResponse {
  today: string;
  reviews: TodayReview[];
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

export async function fetchTodayReviews(): Promise<TodayReviewsResponse> {
  const { data } = await client.get<ApiEnvelope<TodayReviewsResponse>>(
    "/planner/reviews/today",
  );
  return data.data;
}

export interface CompletedReview {
  id: number;
  status: "COMPLETED";
  rating: Rating;
  elapsedDays: number;
  completedAt: string;
}

export interface NextReview {
  id: number;
  flashcardId: number;
  sequence: number;
  scheduledAt: string;
  intervalDays: number;
  easeFactor: number;
  status: "PENDING";
}

export interface CompleteReviewResponse {
  completed: CompletedReview;
  nextReview: NextReview;
  /** sibling burying으로 오늘 큐에서 밀려난 짝 복습 id들(없으면 빈 배열). */
  buriedReviewIds: number[];
}

export async function completeReview(
  reviewId: number,
  rating: Rating,
): Promise<CompleteReviewResponse> {
  const { data } = await client.post<ApiEnvelope<CompleteReviewResponse>>(
    `/planner/reviews/${reviewId}/complete`,
    { rating },
  );
  return data.data;
}
