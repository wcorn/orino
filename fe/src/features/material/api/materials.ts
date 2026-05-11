import client from "../../../shared/api/client";

export type MaterialType = "BOOK" | "LECTURE" | "WORKBOOK" | "MOOC";
export type MaterialStatus = "ACTIVE" | "COMPLETED";
export type UnitStatus = "PENDING" | "COMPLETED";

export interface UnitSummary {
  id: number;
  title: string;
  sortOrder: number;
  status: UnitStatus;
  completedAt: string | null;
}

export interface MaterialSummary {
  id: number;
  title: string;
  type: MaterialType;
  status: MaterialStatus;
  totalUnits: number;
  completedUnits: number;
  createdAt: string;
  updatedAt: string;
}

export interface MaterialDetail extends MaterialSummary {
  units: UnitSummary[];
}

interface ApiResponse<T> {
  code: string;
  data: T;
}

interface MaterialListResponse {
  materials: MaterialSummary[];
}

export async function fetchMaterials(
  status?: MaterialStatus,
): Promise<MaterialSummary[]> {
  const { data } = await client.get<ApiResponse<MaterialListResponse>>(
    "/planner/materials",
    { params: status ? { status } : undefined },
  );
  return data.data.materials;
}

export interface CreateMaterialRequest {
  title: string;
  type: MaterialType;
}

export async function createMaterial(
  request: CreateMaterialRequest,
): Promise<MaterialSummary> {
  const { data } = await client.post<ApiResponse<MaterialSummary>>(
    "/planner/materials",
    request,
  );
  return data.data;
}

export async function fetchMaterial(id: number): Promise<MaterialDetail> {
  const { data } = await client.get<ApiResponse<MaterialDetail>>(
    `/planner/materials/${id}`,
  );
  return data.data;
}

export interface UpdateMaterialRequest {
  title?: string;
  status?: MaterialStatus;
}

export async function updateMaterial(
  id: number,
  request: UpdateMaterialRequest,
): Promise<MaterialSummary> {
  const { data } = await client.patch<ApiResponse<MaterialSummary>>(
    `/planner/materials/${id}`,
    request,
  );
  return data.data;
}

export async function deleteMaterial(id: number): Promise<void> {
  await client.delete(`/planner/materials/${id}`);
}
