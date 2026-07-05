import { client } from "@/shared/api";

export interface MemoContent {
  type: string;
  content?: unknown[];
  [key: string]: unknown;
}

export interface MemoTreeNode {
  id: number;
  title: string;
  parentId: number | null;
  sortOrder: number;
  children: MemoTreeNode[];
}

export interface MemoTreeResponse {
  memos: MemoTreeNode[];
}

export interface MemoDetail {
  id: number;
  parentId: number | null;
  title: string;
  sortOrder: number;
  content: MemoContent;
  updatedAt: string;
}

export interface MemoUpdateResponse {
  id: number;
  parentId: number | null;
  title: string;
  sortOrder: number;
  updatedAt: string;
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

export async function fetchMemoTree(): Promise<MemoTreeNode[]> {
  const { data } = await client.get<ApiEnvelope<MemoTreeResponse>>("/memos");
  return data.data.memos;
}

export async function fetchMemo(memoId: number): Promise<MemoDetail> {
  const { data } = await client.get<ApiEnvelope<MemoDetail>>(
    `/memos/${memoId}`,
  );
  return data.data;
}

export interface MemoCreateRequest {
  parentId?: number | null;
  title?: string;
}

export async function createMemo(
  request: MemoCreateRequest,
): Promise<MemoDetail> {
  const { data } = await client.post<ApiEnvelope<MemoDetail>>(
    "/memos",
    request,
  );
  return data.data;
}

export interface MemoUpdateRequest {
  title?: string;
  content?: MemoContent;
  parentId?: number | null;
  sortOrder?: number;
}

export async function updateMemo(
  memoId: number,
  request: MemoUpdateRequest,
): Promise<MemoUpdateResponse> {
  const { data } = await client.patch<ApiEnvelope<MemoUpdateResponse>>(
    `/memos/${memoId}`,
    request,
  );
  return data.data;
}

export async function deleteMemo(memoId: number): Promise<void> {
  await client.delete(`/memos/${memoId}`);
}
