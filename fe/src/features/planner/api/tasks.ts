import { client } from "@/shared/api";

import type { PlannerTask } from "./feed";

export interface TaskCreateRequest {
  title: string;
  /** 마감일 "2026-06-12" 또는 null */
  due: string | null;
  notes?: string | null;
}

export interface TaskUpdateRequest {
  title?: string | null;
  due?: string | null;
  notes?: string | null;
  completed?: boolean;
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

export async function createTask(
  request: TaskCreateRequest,
): Promise<PlannerTask> {
  const { data } = await client.post<ApiEnvelope<PlannerTask>>(
    "/planner/tasks",
    request,
  );
  return data.data;
}

export async function updateTask(
  taskId: string,
  request: TaskUpdateRequest,
): Promise<PlannerTask> {
  const { data } = await client.patch<ApiEnvelope<PlannerTask>>(
    `/planner/tasks/${taskId}`,
    request,
  );
  return data.data;
}

export async function deleteTask(taskId: string): Promise<void> {
  await client.delete(`/planner/tasks/${taskId}`);
}
