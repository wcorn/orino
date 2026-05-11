import client from "../../../shared/api/client";

export type MaterialType = "BOOK" | "LECTURE" | "WORKBOOK" | "MOOC";
export type MaterialStatus = "ACTIVE" | "COMPLETED";

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
