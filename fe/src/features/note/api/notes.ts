import { client } from "@/shared/api";

export interface NoteContent {
  type: string;
  content?: unknown[];
  [key: string]: unknown;
}

export interface Note {
  id: number;
  materialId: number;
  content: NoteContent;
  updatedAt: string;
}

export interface NoteUpdateResponse {
  id: number;
  materialId: number;
  updatedAt: string;
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

export async function fetchNote(materialId: number): Promise<Note> {
  const { data } = await client.get<ApiEnvelope<Note>>(
    `/planner/materials/${materialId}/note`,
  );
  return data.data;
}

export async function putNote(
  materialId: number,
  content: NoteContent,
): Promise<NoteUpdateResponse> {
  const { data } = await client.put<ApiEnvelope<NoteUpdateResponse>>(
    `/planner/materials/${materialId}/note`,
    { content },
  );
  return data.data;
}
