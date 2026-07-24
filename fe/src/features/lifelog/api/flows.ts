import { client } from "@/shared/api";

import type { MomentCard } from "./types";

export type FlowStatus = "ACTIVE" | "ARCHIVED";

export interface FlowSummary {
  id: number;
  title: string;
  description: string | null;
  coverUrl: string | null;
  startedAt: string | null;
  endedAt: string | null;
  momentCount: number;
  status: FlowStatus;
}

export interface FlowDetail {
  id: number;
  title: string;
  description: string | null;
  coverUrl: string | null;
  startedAt: string | null;
  endedAt: string | null;
  status: FlowStatus;
  moments: MomentCard[];
}

export interface FlowCreateRequest {
  title: string;
  description?: string | null;
}

export interface FlowUpdateRequest {
  title: string;
  description?: string | null;
  coverObjectKey?: string | null;
  status: FlowStatus;
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

export async function fetchFlows(status?: FlowStatus): Promise<FlowSummary[]> {
  const { data } = await client.get<ApiEnvelope<FlowSummary[]>>(
    "/lifelog/flows",
    { params: status ? { status } : undefined },
  );
  return data.data;
}

export async function fetchFlow(id: number): Promise<FlowDetail> {
  const { data } = await client.get<ApiEnvelope<FlowDetail>>(
    `/lifelog/flows/${id}`,
  );
  return data.data;
}

export async function createFlow(
  request: FlowCreateRequest,
): Promise<FlowSummary> {
  const { data } = await client.post<ApiEnvelope<FlowSummary>>(
    "/lifelog/flows",
    request,
  );
  return data.data;
}

export async function updateFlow(
  id: number,
  request: FlowUpdateRequest,
): Promise<FlowSummary> {
  const { data } = await client.put<ApiEnvelope<FlowSummary>>(
    `/lifelog/flows/${id}`,
    request,
  );
  return data.data;
}

export async function deleteFlow(id: number): Promise<void> {
  await client.delete(`/lifelog/flows/${id}`);
}

export async function addMomentsToFlow(
  id: number,
  momentIds: number[],
): Promise<FlowDetail> {
  const { data } = await client.post<ApiEnvelope<FlowDetail>>(
    `/lifelog/flows/${id}/moments`,
    { momentIds },
  );
  return data.data;
}

export async function removeMomentFromFlow(
  id: number,
  momentId: number,
): Promise<void> {
  await client.delete(`/lifelog/flows/${id}/moments/${momentId}`);
}

export async function reorderFlowMoments(
  id: number,
  momentIds: number[],
): Promise<FlowDetail> {
  const { data } = await client.put<ApiEnvelope<FlowDetail>>(
    `/lifelog/flows/${id}/moments/order`,
    { momentIds },
  );
  return data.data;
}
