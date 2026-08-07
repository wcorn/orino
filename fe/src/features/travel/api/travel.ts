import { client } from "@/shared/api";

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

/** 진행 중 여행 — 탭하면 곧바로 보드로 간다. */
export interface OngoingTripSummary {
  id: number;
  title: string;
  /** 서버가 조립해 주는 보드 경로. 프론트가 경로를 다시 만들지 않는다. */
  boardPath: string;
}

/** 다음 예정 여행 — D-day 카운트다운 카드. */
export interface NextTripSummary {
  id: number;
  title: string;
  destinationName: string;
  startDate: string;
  endDate: string;
  /** 시작일까지 남은 일수. 여행 타임존 기준이라 프론트에서 다시 계산하지 않는다. */
  dDay: number;
  activityCount: number;
}

/** 가장 최근에 끝난 여행. */
export interface CompletedTripSummary {
  id: number;
  title: string;
  endDate: string;
  activityCount: number;
}

/**
 * `/select` 카드와 여행 홈(S-01)이 함께 쓰는 요약.
 * 셋 다 null이면 여행을 한 번도 만들지 않은 상태다.
 */
export interface TravelSummary {
  ongoing: OngoingTripSummary | null;
  next: NextTripSummary | null;
  recentCompleted: CompletedTripSummary | null;
}

export async function fetchTravelSummary(): Promise<TravelSummary> {
  const { data } =
    await client.get<ApiEnvelope<TravelSummary>>("/travel/summary");
  return data.data;
}
