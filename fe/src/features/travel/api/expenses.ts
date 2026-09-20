import { client } from "@/shared/api";

import type { TripStatus } from "../lib/tripStatus";

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

/**
 * 지출 분류. <b>여섯으로 고정이고 서버에서 목록을 받아오지 않는다</b>(경비 독립 §4.1).
 *
 * <p>가계부의 카테고리 테이블을 옮겨 오지 않은 자리다. 분류가 늘면 「어디에 적을지
 * 고민하는 시간」이 늘고, 그만큼 안 적게 된다.
 */
export const EXPENSE_CATEGORIES = [
  "FOOD",
  "TRANSPORT",
  "STAY",
  "SIGHT",
  "SHOPPING",
  "ETC",
] as const;

export type ExpenseCategory = (typeof EXPENSE_CATEGORIES)[number];

export const EXPENSE_CATEGORY_LABELS: Record<ExpenseCategory, string> = {
  FOOD: "식비",
  TRANSPORT: "교통",
  STAY: "숙소",
  SIGHT: "관광",
  SHOPPING: "쇼핑",
  ETC: "기타",
};

export type ExpenseStatus = "CONFIRMED" | "SCHEDULED";

/**
 * 쓴 날의 환율로 굳은 값. 조회할 때 다시 계산하지 않는다(§4.3).
 *
 * @property rate `null`이면 저장할 때 ECB에 닿지 못했다는 뜻이다 — 에러가 아니라
 *   화면이 직접 입력 칸을 여는 신호다. 그때 `amount`는 0이다
 */
export interface ExpenseFx {
  currency: string;
  amount: number;
  rate: number | null;
}

/**
 * 지출 한 줄. <b>편집 시트가 이 값으로 열린다</b> — 행을 눌러도 가계부로 나가지 않는다.
 *
 * @property amount 원화 환산액. 합계는 전부 이 값만 읽는다
 * @property overdue 날짜가 지났는데 아직 예정이다. 화면이 「확정」 버튼을 건다(§4.3)
 */
export interface ExpenseRow {
  expenseId: number;
  title: string | null;
  amount: number;
  fx: ExpenseFx | null;
  status: ExpenseStatus;
  category: ExpenseCategory | null;
  paymentMethod: string | null;
  uncategorized: boolean;
  overdue: boolean;
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

/** 외화 입력. `rate`를 비워 보내면 서버가 고시로 채우고 저장 시점에 고정한다. */
export interface ExpenseFxInput {
  currency: string;
  amount: number;
  rate?: number | null;
}

/**
 * 지출 입력. 필수는 <b>날짜와 금액</b>뿐이다 — 제목도 분류도 없이 저장된다(§6.1).
 *
 * <p>`amount`와 `fx` 중 하나는 있어야 한다. 둘 다 없으면 `TRAVEL-ERR-027`이다.
 */
export interface ExpenseCreateBody {
  occurredOn: string;
  amount?: number;
  title?: string | null;
  category?: ExpenseCategory | null;
  paymentMethod?: string | null;
  status?: ExpenseStatus;
  fx?: ExpenseFxInput;
}

/**
 * 지출 수정. <b>보낸 것만 바뀐다</b> — 비우려면 `clear*`를 쓴다.
 *
 * <p>`null`은 「안 보냈다」와 「비우겠다」를 구분하지 못한다. 분류를 미분류로 되돌리거나
 * 결제수단을 지우는 것은 실제로 일어나는 조작이라 플래그를 따로 둔다.
 */
export interface ExpenseUpdateBody {
  occurredOn?: string;
  amount?: number;
  title?: string;
  clearTitle?: boolean;
  category?: ExpenseCategory;
  clearCategory?: boolean;
  paymentMethod?: string;
  clearPaymentMethod?: boolean;
  status?: ExpenseStatus;
  fx?: ExpenseFxInput;
  /** 외화 근거를 지우고 원화 지출로 되돌린다. `amount`를 함께 보내야 한다. */
  clearFx?: boolean;
}

export async function fetchTripExpenses(tripId: number): Promise<TripExpenses> {
  const { data } = await client.get<ApiEnvelope<TripExpenses>>(
    `/travel/trips/${tripId}/expenses`,
  );
  return data.data;
}

/** 응답은 목록의 한 줄과 같은 모양이다 — 화면이 그 줄만 갈아 끼우면 된다. */
export async function createTripExpense(
  tripId: number,
  body: ExpenseCreateBody,
): Promise<ExpenseRow> {
  const { data } = await client.post<ApiEnvelope<ExpenseRow>>(
    `/travel/trips/${tripId}/expenses`,
    body,
  );
  return data.data;
}

export async function updateTripExpense(
  tripId: number,
  expenseId: number,
  body: ExpenseUpdateBody,
): Promise<ExpenseRow> {
  const { data } = await client.patch<ApiEnvelope<ExpenseRow>>(
    `/travel/trips/${tripId}/expenses/${expenseId}`,
    body,
  );
  return data.data;
}

/** 소프트 삭제. <b>상쇄하지 않는다</b> — 여행 경비는 원장이 아니다(§4.4). */
export async function deleteTripExpense(
  tripId: number,
  expenseId: number,
): Promise<void> {
  await client.delete(`/travel/trips/${tripId}/expenses/${expenseId}`);
}

/** 여행 예산. `null`이면 해제다 — 0은 400이다(§5.3). */
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
