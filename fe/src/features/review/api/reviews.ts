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
  scheduledDate: string;
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
