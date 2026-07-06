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
  /** 학습자료 종속 노트는 자료 id, 독립 노트는 null. */
  materialId: number | null;
  parentId: number | null;
  title: string;
  sortOrder: number;
  content: NoteContent;
  updatedAt: string;
}

export interface NoteUpdateResponse {
  id: number;
  materialId: number | null;
  parentId: number | null;
  title: string;
  sortOrder: number;
  updatedAt: string;
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

/**
 * 노트 트리를 조회한다. materialId가 있으면 자료 종속 노트, 없으면 독립 노트.
 */
export async function fetchNoteTree(
  materialId?: number,
): Promise<NoteTreeNode[]> {
  const { data } = await client.get<ApiEnvelope<NoteTreeResponse>>("/notes", {
    params: materialId != null ? { materialId } : undefined,
  });
  return data.data.notes;
}

export async function fetchNote(noteId: number): Promise<NoteDetail> {
  const { data } = await client.get<ApiEnvelope<NoteDetail>>(
    `/notes/${noteId}`,
  );
  return data.data;
}

export interface NoteCreateRequest {
  parentId?: number | null;
  title?: string;
}

export async function createNote(
  materialId: number | undefined,
  request: NoteCreateRequest,
): Promise<NoteDetail> {
  const { data } = await client.post<ApiEnvelope<NoteDetail>>(
    "/notes",
    request,
    { params: materialId != null ? { materialId } : undefined },
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
    `/notes/${noteId}`,
    request,
  );
  return data.data;
}

export async function deleteNote(noteId: number): Promise<void> {
  await client.delete(`/notes/${noteId}`);
}
