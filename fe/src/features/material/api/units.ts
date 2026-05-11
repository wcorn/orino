import client from "../../../shared/api/client";
import type { UnitStatus } from "./materials";

interface ApiResponse<T> {
  code: string;
  data: T;
}

export interface UnitDetail {
  id: number;
  materialId: number;
  title: string;
  sortOrder: number;
  status: UnitStatus;
  completedAt: string | null;
}

interface UnitListResponse {
  units: UnitDetail[];
}

export interface UnitItemInput {
  title: string;
}

export async function createUnits(
  materialId: number,
  units: UnitItemInput[],
): Promise<UnitDetail[]> {
  const { data } = await client.post<ApiResponse<UnitListResponse>>(
    `/planner/materials/${materialId}/units`,
    { units },
  );
  return data.data.units;
}

export interface UpdateUnitRequest {
  title?: string;
  sortOrder?: number;
}

export async function updateUnit(
  unitId: number,
  request: UpdateUnitRequest,
): Promise<UnitDetail> {
  const { data } = await client.patch<ApiResponse<UnitDetail>>(
    `/planner/units/${unitId}`,
    request,
  );
  return data.data;
}

export async function deleteUnit(unitId: number): Promise<void> {
  await client.delete(`/planner/units/${unitId}`);
}

export interface UnitCompletionResult {
  unit: {
    id: number;
    title: string;
    status: UnitStatus;
    completedAt: string;
  };
  firstReview: {
    id: number;
    studyUnitId: number;
    sequence: number;
    scheduledDate: string;
    intervalDays: number;
    easeFactor: number;
    status: "PENDING" | "COMPLETED";
    completedAt: string | null;
  };
}

export async function completeUnit(
  unitId: number,
): Promise<UnitCompletionResult> {
  const { data } = await client.post<ApiResponse<UnitCompletionResult>>(
    `/planner/units/${unitId}/complete`,
  );
  return data.data;
}
