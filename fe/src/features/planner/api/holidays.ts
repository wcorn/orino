import { client } from "@/shared/api";

export interface Holiday {
  /** "2026-06-06" */
  date: string;
  /** "현충일" */
  name: string;
}

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

/** [from, to] 구간 공휴일. BE가 한국천문연구원 특일정보 API로 동기화한 값을 반환한다. */
export async function fetchHolidays(
  from: string,
  to: string,
): Promise<Holiday[]> {
  const { data } = await client.get<ApiEnvelope<Holiday[]>>(
    "/planner/holidays",
    { params: { from, to } },
  );
  return data.data;
}
