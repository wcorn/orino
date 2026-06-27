import { client } from "@/shared/api";

/** 서버 응답 블록(저장 후 id 포함). dayOfWeek 0=일~6=토, 시간은 "HH:mm". */
export interface WeeklyPlanBlock {
  id: number;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  label: string;
  color: string | null;
}

/** 전량 교체 요청 블록(id 불필요). */
export interface WeeklyPlanBlockInput {
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  label: string;
  color: string | null;
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

interface WeeklyPlanData {
  blocks: WeeklyPlanBlock[];
}

/** 주간 템플릿 조회. */
export async function fetchWeeklyPlan(): Promise<WeeklyPlanBlock[]> {
  const { data } =
    await client.get<ApiEnvelope<WeeklyPlanData>>("/planner/plan");
  return data.data.blocks;
}

/** 주간 템플릿 전량 교체(저장). 반환은 새 id가 부여된 전체 블록. */
export async function saveWeeklyPlan(
  blocks: WeeklyPlanBlockInput[],
): Promise<WeeklyPlanBlock[]> {
  const { data } = await client.put<ApiEnvelope<WeeklyPlanData>>(
    "/planner/plan",
    { blocks },
  );
  return data.data.blocks;
}
