import { client } from "@/shared/api";

export type MaterialType = "BOOK" | "LECTURE" | "WORKBOOK" | "MOOC";
export type MaterialStatus = "ACTIVE" | "COMPLETED";

export interface Material {
  id: number;
  title: string;
  type: MaterialType;
  status: MaterialStatus;
  flashcardCount: number;
  dueReviewCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface MaterialListResponse {
  materials: Material[];
}

export interface NoteContent {
  type: string;
  content: unknown[];
}

export interface Note {
  id: number;
  materialId: number;
  content: NoteContent;
  updatedAt: string;
}

export interface MaterialCreateResponse {
  material: Material;
  note: Note;
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

export async function fetchMaterials(
  status?: MaterialStatus,
): Promise<Material[]> {
  const { data } = await client.get<ApiEnvelope<MaterialListResponse>>(
    "/planner/materials",
    {
      params: status ? { status } : undefined,
    },
  );
  return data.data.materials;
}

export interface MaterialCreateRequest {
  title: string;
  type: MaterialType;
}

export async function createMaterial(
  request: MaterialCreateRequest,
): Promise<MaterialCreateResponse> {
  const { data } = await client.post<ApiEnvelope<MaterialCreateResponse>>(
    "/planner/materials",
    request,
  );
  return data.data;
}

export async function fetchMaterial(id: number): Promise<Material> {
  const { data } = await client.get<ApiEnvelope<Material>>(
    `/planner/materials/${id}`,
  );
  return data.data;
}

export interface MaterialUpdateRequest {
  title?: string;
  status?: MaterialStatus;
}

export async function updateMaterial(
  id: number,
  request: MaterialUpdateRequest,
): Promise<Material> {
  const { data } = await client.patch<ApiEnvelope<Material>>(
    `/planner/materials/${id}`,
    request,
  );
  return data.data;
}

export async function deleteMaterial(id: number): Promise<void> {
  await client.delete(`/planner/materials/${id}`);
}
