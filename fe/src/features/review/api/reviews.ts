import { client } from "@/shared/api";

export type Rating = "AGAIN" | "HARD" | "GOOD" | "EASY";

export interface TodayReviewMaterial {
  id: number;
  title: string;
  type: "BOOK" | "LECTURE" | "WORKBOOK" | "MOOC";
}

export interface TodayReviewFlashcard {
  id: number;
  front: string;
  back: string;
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
