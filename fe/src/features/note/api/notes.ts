import { client } from "@/shared/api";

export interface NoteContent {
  type: string;
  content?: unknown[];
  [key: string]: unknown;
}

export interface NoteTreeNode {
  id: number;
  title: string;
  parentId: number | null;
  sortOrder: number;
  children: NoteTreeNode[];
}

export interface NoteTreeResponse {
  notes: NoteTreeNode[];
}

export interface NoteDetail {
  id: number;
  materialId: number;
  parentId: number | null;
  title: string;
  sortOrder: number;
  content: NoteContent;
  updatedAt: string;
}

export interface NoteUpdateResponse {
  id: number;
  materialId: number;
  parentId: number | null;
  title: string;
  sortOrder: number;
  updatedAt: string;
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

export async function fetchNoteTree(
  materialId: number,
): Promise<NoteTreeNode[]> {
  const { data } = await client.get<ApiEnvelope<NoteTreeResponse>>(
    `/planner/materials/${materialId}/notes`,
  );
  return data.data.notes;
}

export async function fetchNote(noteId: number): Promise<NoteDetail> {
  const { data } = await client.get<ApiEnvelope<NoteDetail>>(
    `/planner/notes/${noteId}`,
  );
  return data.data;
}

export interface NoteCreateRequest {
  parentId?: number | null;
  title?: string;
}

export async function createNote(
  materialId: number,
  request: NoteCreateRequest,
): Promise<NoteDetail> {
  const { data } = await client.post<ApiEnvelope<NoteDetail>>(
    `/planner/materials/${materialId}/notes`,
    request,
  );
  return data.data;
}

export interface NoteUpdateRequest {
  title?: string;
  content?: NoteContent;
  parentId?: number | null;
  sortOrder?: number;
}

export async function updateNote(
  noteId: number,
  request: NoteUpdateRequest,
): Promise<NoteUpdateResponse> {
  const { data } = await client.patch<ApiEnvelope<NoteUpdateResponse>>(
    `/planner/notes/${noteId}`,
    request,
  );
  return data.data;
}

export async function deleteNote(noteId: number): Promise<void> {
  await client.delete(`/planner/notes/${noteId}`);
}
