import { client } from "@/shared/api";

export interface NextReview {
  id: number;
  sequence: number;
  scheduledAt: string;
  intervalDays: number;
  easeFactor: number;
}

export interface FirstReview extends NextReview {
  flashcardId: number;
  status: "PENDING" | "COMPLETED";
}

export type FlashcardType = "BASIC" | "ORDERING";

/** 순서 카드의 항목. 배열 순서 자체가 정답 순서. `id`는 드래그 안정 키(FE 생성). */
export interface OrderingItem {
  id: string;
  text: string;
}

export interface Flashcard {
  id: number;
  materialId: number;
  type: FlashcardType;
  front: string;
  /** ORDERING 카드는 없음(null/생략). */
  back: string | null;
  /** ORDERING 카드만 정답 순서 배열, BASIC은 없음(null/생략). */
  items: OrderingItem[] | null;
  /** 양방향 짝 카드가 공유하는 그룹 키. 단방향 카드는 null. */
  siblingGroupId: number | null;
  nextReview: NextReview | null;
  createdAt: string;
}

/** 카드 종류 필터. `pair`는 BASIC + siblingGroupId 파생(복습 허브와 동일한 축). */
export type CardTypeFilter = "all" | "basic" | "order" | "pair";
/** 복습 상태 필터. PENDING 복습 시각이 어느 구간에 있는지. */
export type ReviewStatusFilter = "all" | "overdue" | "today" | "upcoming";
/** 정렬 축. 복습 임박순은 복습 허브가 담당하므로 여기선 생성순만. */
export type FlashcardSort = "created_asc" | "created_desc";

/**
 * 목록 필터. **서버가 SSOT다** — 페이징이 걸려 있으므로 FE에서 다시 거르면
 * 이미 로드된 페이지에만 적용되어 결과가 어긋난다.
 */
export interface FlashcardListFilters {
  q?: string;
  type?: CardTypeFilter;
  review?: ReviewStatusFilter;
  sort?: FlashcardSort;
}

export interface FlashcardListParams extends FlashcardListFilters {
  cursor?: string;
  size?: number;
}

export interface FlashcardListResponse {
  flashcards: Flashcard[];
  /** 필터를 적용한 총 개수(페이지 길이가 아님). */
  totalCount: number;
  /** 다음 페이지 커서. 마지막 페이지면 생략(undefined). */
  nextCursor?: string;
  hasNext: boolean;
}

/**
 * 카드 생성/수정 페이로드. 종류별로 필요한 필드만 담는다. 편집 시 종류 전환도 이 형태로 전달한다.
 * (BE는 PATCH에서 type이 있으면 해당 종류로 전환하며 대상 종류 제약을 검증한다)
 */
export type FlashcardMutationPayload =
  | { type: "BASIC"; front: string; back: string }
  | { type: "ORDERING"; front: string; items: OrderingItem[] };

/** 생성 요청. BASIC은 `bidirectional`로 역방향 짝 카드도 함께 만들 수 있다(ORDERING 불가). */
export type FlashcardCreateRequest =
  | { type: "BASIC"; front: string; back: string; bidirectional?: boolean }
  | { type: "ORDERING"; front: string; items: OrderingItem[] };

export type FlashcardUpdateRequest = FlashcardMutationPayload;

export interface FlashcardCreateResponse {
  flashcard: Flashcard;
  firstReview: FirstReview;
  /** 양방향 생성일 때만 짝(역방향) 카드가 담긴다. */
  sibling?: {
    flashcard: Flashcard;
    firstReview: FirstReview;
  };
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

export async function fetchFlashcards(
  materialId: number,
  params: FlashcardListParams = {},
): Promise<FlashcardListResponse> {
  const { data } = await client.get<ApiEnvelope<FlashcardListResponse>>(
    `/planner/materials/${materialId}/flashcards`,
    { params },
  );
  return data.data;
}

export async function createFlashcard(
  materialId: number,
  request: FlashcardCreateRequest,
): Promise<FlashcardCreateResponse> {
  const { data } = await client.post<ApiEnvelope<FlashcardCreateResponse>>(
    `/planner/materials/${materialId}/flashcards`,
    request,
  );
  return data.data;
}

export async function updateFlashcard(
  flashcardId: number,
  request: FlashcardUpdateRequest,
): Promise<Flashcard> {
  const { data } = await client.patch<ApiEnvelope<Flashcard>>(
    `/planner/flashcards/${flashcardId}`,
    request,
  );
  return data.data;
}

export async function deleteFlashcard(flashcardId: number): Promise<void> {
  await client.delete(`/planner/flashcards/${flashcardId}`);
}
