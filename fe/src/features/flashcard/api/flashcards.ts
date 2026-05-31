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

export interface Flashcard {
  id: number;
  materialId: number;
  front: string;
  back: string;
  nextReview: NextReview | null;
  createdAt: string;
}

export interface FlashcardListResponse {
  flashcards: Flashcard[];
}

export interface FlashcardCreateRequest {
  front: string;
  back: string;
}

export interface FlashcardCreateResponse {
  flashcard: Flashcard;
  firstReview: FirstReview;
}

export interface FlashcardUpdateRequest {
  front?: string;
  back?: string;
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
