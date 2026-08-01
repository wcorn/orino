import type { FlashcardType } from "@/features/flashcard/api/flashcards";
import { client } from "@/shared/api";

import type { Rating, TodayReviewMaterial } from "./reviews";

/** 목록에서 노출하는 카드 종류. PAIR는 BASIC + siblingGroupId 파생. */
export type CardType = "BASIC" | "ORDERING" | "PAIR";

/** 앞으로 항목이 언제 due인지. 시각 라벨은 FE가 포맷. */
export type WhenKind = "now" | "today" | "future";

/** 앞으로 목록 스코프 필터. */
export type ReviewScope = "all" | "today" | "overdue";
/** 앞으로 목록 기간 필터. */
export type UpcomingWhen = "all" | "today" | "3d" | "7d";
/** 앞으로 목록 종류 필터(pair는 파생). */
export type UpcomingType = "all" | "basic" | "order" | "pair";
/** 완료 목록 평가 필터. */
export type GradeFilter = "all" | Rating;

/** 목록 항목에 동봉되는 카드 뷰(앞면만). */
export interface ReviewCardView {
  id: number;
  type: FlashcardType;
  front: string;
  siblingGroupId: number | null;
  material: TodayReviewMaterial;
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

// ===== summary =====

export interface ReviewSummaryCounts {
  now: number;
  overdue: number;
  upcoming: number;
  doneToday: number;
}

export interface ReviewSummaryMaterial {
  id: number;
  name: string;
  due: number;
  overdue: number;
  nextLabel: string;
}

export interface ReviewSummary {
  today: string;
  counts: ReviewSummaryCounts;
  estimatedMinutes: number;
  materials: ReviewSummaryMaterial[];
}

export async function fetchReviewSummary(): Promise<ReviewSummary> {
  const { data } = await client.get<ApiEnvelope<ReviewSummary>>(
    "/planner/reviews/summary",
  );
  return data.data;
}

// ===== upcoming =====

export interface UpcomingReviewItem {
  id: number;
  /** ISO datetime. due = scheduledAt <= now */
  scheduledAt: string;
  whenKind: WhenKind;
  overdue: boolean;
  cardType: CardType;
  flashcard: ReviewCardView;
}

export interface UpcomingReviewsPage {
  today: string;
  items: UpcomingReviewItem[];
  /** 다음 페이지 커서. 마지막 페이지면 생략(undefined). */
  nextCursor?: string;
  hasNext: boolean;
  /** 현재 필터에 걸리는 전체 건수. 첫 페이지에만 실린다. */
  totalCount?: number;
}

export interface UpcomingReviewParams {
  scope?: ReviewScope;
  materialId?: number;
  when?: UpcomingWhen;
  type?: UpcomingType;
  cursor?: string;
  size?: number;
}

export async function fetchUpcomingReviews(
  params: UpcomingReviewParams,
): Promise<UpcomingReviewsPage> {
  const { data } = await client.get<ApiEnvelope<UpcomingReviewsPage>>(
    "/planner/reviews/upcoming",
    { params },
  );
  return data.data;
}

// ===== completed =====

export interface CompletedReviewItem {
  id: number;
  completedAt: string;
  rating: Rating;
  sequence: number;
  cardType: CardType;
  flashcard: ReviewCardView;
}

export interface CompletedReviewsPage {
  items: CompletedReviewItem[];
  nextCursor?: string;
  hasNext: boolean;
  /** 현재 필터에 걸리는 전체 건수. 첫 페이지에만 실린다. */
  totalCount?: number;
}

export interface CompletedReviewParams {
  materialId?: number;
  grade?: Rating;
  cursor?: string;
  size?: number;
}

export async function fetchCompletedReviews(
  params: CompletedReviewParams,
): Promise<CompletedReviewsPage> {
  const { data } = await client.get<ApiEnvelope<CompletedReviewsPage>>(
    "/planner/reviews/completed",
    { params },
  );
  return data.data;
}
