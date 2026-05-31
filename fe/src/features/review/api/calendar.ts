import { client } from "@/shared/api";

import type { Rating, TodayReviewMaterial } from "./reviews";

export type ReviewStatus = "PENDING" | "COMPLETED";

export interface CalendarReviewFlashcard {
  id: number;
  front: string;
  material: TodayReviewMaterial;
}

export interface CalendarReview {
  id: number;
  /** ISO datetime. 캘린더 그룹/분류는 날짜 부분(slice 0..10)으로 한다. */
  scheduledAt: string;
  status: ReviewStatus;
  rating: Rating | null;
  sequence: number;
  flashcard: CalendarReviewFlashcard;
}

export interface CalendarReviewsResponse {
  from: string;
  to: string;
  reviews: CalendarReview[];
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

export async function fetchCalendarReviews(
  from: string,
  to: string,
): Promise<CalendarReviewsResponse> {
  const { data } = await client.get<ApiEnvelope<CalendarReviewsResponse>>(
    "/planner/reviews/calendar",
    { params: { from, to } },
  );
  return data.data;
}
