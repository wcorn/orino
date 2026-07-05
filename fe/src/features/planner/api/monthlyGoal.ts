import { client } from "@/shared/api";

export interface MonthlyGoal {
  year: number;
  month: number;
  content: string;
  updatedAt: string;
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

/** 해당 년월 목표 조회. 없으면 null. */
export async function fetchMonthlyGoal(
  year: number,
  month: number,
): Promise<MonthlyGoal | null> {
  const { data } = await client.get<ApiEnvelope<MonthlyGoal | null>>(
    `/planner/monthly-goals/${year}/${month}`,
  );
  return data.data;
}

/** upsert(PUT). content 1~1000자·공백만 불가는 BE가 검증. */
export async function saveMonthlyGoal(
  year: number,
  month: number,
  content: string,
): Promise<MonthlyGoal> {
  const { data } = await client.put<ApiEnvelope<MonthlyGoal>>(
    `/planner/monthly-goals/${year}/${month}`,
    { content },
  );
  return data.data;
}

/** 삭제(idempotent). */
export async function deleteMonthlyGoal(
  year: number,
  month: number,
): Promise<void> {
  await client.delete(`/planner/monthly-goals/${year}/${month}`);
}
