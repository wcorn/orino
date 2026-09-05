import { client } from "@/shared/api";

import type { TripStatus } from "../lib/tripStatus";

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

/** 쓴 날의 환율로 굳은 값. 조회할 때 다시 계산하지 않는다(§4.3). */
export interface ExpenseFx {
  currency: string;
  amount: number;
  rate: number;
}

/**
 * 지출 한 줄. <b>편집용 필드가 없다</b> — 누르면 가계부 지출 상세로 간다(D-35).
 *
 * @property amount 원화 환산액. 합계는 전부 이 값만 읽는다
 */
export interface ExpenseRow {
  transactionId: number;
  title: string | null;
  amount: number;
  fx: ExpenseFx | null;
  status: "CONFIRMED" | "SCHEDULED";
  uncategorized: boolean;
  occurredOn: string;
}

/**
 * 날짜 묶음. `sum`은 <b>보이는 줄의 합</b>이라 확정과 예정을 함께 센다 —
 * 사용자가 눈으로 더한 값과 같아야 한다.
 */
export interface ExpenseGroup {
  key: string;
  label: string;
  dayNumber: number | null;
  date: string | null;
  cityName: string | null;
  sum: number;
  rows: ExpenseRow[];
}

/**
 * 예산과 그 파생값. <b>안 정했으면 이 블록이 통째로 없다</b> — `amount: 0`이 아니다.
 *
 * @property dailyAllowance 여행이 끝나면 `null`. 그 자리를 `totals.dailyAverage`가 받는다
 */
export interface ExpenseBudget {
  amount: number;
  spent: number;
  scheduled: number;
  remaining: number;
  daysLeft: number | null;
  dailyAllowance: number | null;
}

/** 예산과 무관한 총계. 「얼마 썼나」는 예산 없이도 답이 있다. */
export interface ExpenseTotals {
  spent: number;
  scheduled: number;
  days: number;
  /** 다녀온 뒤에만 채워진다. */
  dailyAverage: number | null;
}

export interface TripExpenses {
  tripId: number;
  status: TripStatus;
  todayDayNumber: number | null;
  budget: ExpenseBudget | null;
  totals: ExpenseTotals;
  unsortedCount: number;
  groups: ExpenseGroup[];
}

export async function fetchTripExpenses(tripId: number): Promise<TripExpenses> {
  const { data } = await client.get<ApiEnvelope<TripExpenses>>(
    `/travel/trips/${tripId}/expenses`,
  );
  return data.data;
}

/** 여행이 갖는 유일한 경비 쓰기. `null`이면 해제다 — 0은 400이다(§5.3). */
export async function putTripBudget(
  tripId: number,
  amount: number | null,
): Promise<{ amount: number | null }> {
  const { data } = await client.put<ApiEnvelope<{ amount: number | null }>>(
    `/travel/trips/${tripId}/budget`,
    { amount },
  );
  return data.data;
}
