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
  nextReview: NextReview | null;
  createdAt: string;
}

export interface FlashcardListResponse {
  flashcards: Flashcard[];
}

/**
 * 카드 생성/수정 페이로드. 종류별로 필요한 필드만 담는다. 편집 시 종류 전환도 이 형태로 전달한다.
 * (BE는 PATCH에서 type이 있으면 해당 종류로 전환하며 대상 종류 제약을 검증한다)
 */
export type FlashcardMutationPayload =
  | { type: "BASIC"; front: string; back: string }
  | { type: "ORDERING"; front: string; items: OrderingItem[] };

export type FlashcardCreateRequest = FlashcardMutationPayload;

export type FlashcardUpdateRequest = FlashcardMutationPayload;

export interface FlashcardCreateResponse {
  flashcard: Flashcard;
  firstReview: FirstReview;
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

export async function fetchFlashcards(
  materialId: number,
): Promise<Flashcard[]> {
  const { data } = await client.get<ApiEnvelope<FlashcardListResponse>>(
    `/planner/materials/${materialId}/flashcards`,
  );
  return data.data.flashcards;
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
