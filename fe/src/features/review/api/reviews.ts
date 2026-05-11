import client from "../../../shared/api/client";

export type Rating = "AGAIN" | "HARD" | "GOOD" | "EASY";

export interface ReviewMaterial {
  id: number;
  title: string;
  type: "BOOK" | "LECTURE" | "WORKBOOK" | "MOOC";
}

export interface ReviewUnit {
  id: number;
  title: string;
  material: ReviewMaterial;
}

export interface ReviewPreview {
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
  unit: ReviewUnit;
  preview: ReviewPreview;
}

export interface TodayReviewsResponse {
  today: string;
  reviews: TodayReview[];
}

interface ApiResponse<T> {
  code: string;
  data: T;
}

export async function fetchTodayReviews(): Promise<TodayReviewsResponse> {
  const { data } = await client.get<ApiResponse<TodayReviewsResponse>>(
    "/planner/reviews/today",
  );
  return data.data;
}

export interface ReviewSchedule {
  id: number;
  studyUnitId: number;
  sequence: number;
  scheduledDate: string;
  intervalDays: number;
  easeFactor: number;
  status: "PENDING" | "COMPLETED";
  completedAt: string | null;
}

export interface CompleteReviewResult {
  completed: ReviewSchedule;
  nextReview: ReviewSchedule;
}

export async function completeReview(
  reviewId: number,
  rating: Rating,
): Promise<CompleteReviewResult> {
  const { data } = await client.post<ApiResponse<CompleteReviewResult>>(
    `/planner/reviews/${reviewId}/complete`,
    { rating },
  );
  return data.data;
}
