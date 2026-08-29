import type { RecurringKind, RecurringView } from "../api/ledger";

/**
 * 정기 항목을 <b>화면에 그리기 위한</b> 표기만 있다.
 *
 * <p>주기 문구(`freqLabel`)도, 월 환산액도, 다음 결제일도 <b>서버가 만들어 준다</b> —
 * 「매월 말일」과 「매월 31일」처럼 같아 보이지만 다른 규칙이 화면마다 다르게 읽히면 안 되기
 * 때문이다(D-13). 여기 있는 것은 라벨과 부호뿐이다.
 */

/** 종류는 표시·필터용 라벨이다. 동작은 전부 같다(§6.1). */
export const RECURRING_KIND_LABELS: Record<RecurringKind, string> = {
  SUBSCRIPTION: "구독",
  FIXED_COST: "고정비",
  INSURANCE: "보험",
  TRANSFER: "자동이체",
  INCOME: "정기 수입",
};

/**
 * 금액 표기. <b>변동은 물결표를 붙인다</b> — 공과금은 예상액이고, 고지서가 오면 고쳐야 한다.
 *
 * @param formatted 이미 천 단위 콤마가 찍힌 문자열
 */
export function amountLabel(rule: RecurringView, formatted: string): string {
  return rule.amountType === "VARIABLE" ? `~${formatted}` : formatted;
}

/** 해지된 항목인가. 목록에서 사라지지 않고 흐리게 남는다 — 연간 회고에 필요하다. */
export function isEnded(rule: RecurringView): boolean {
  return rule.status === "ENDED";
}

/**
 * 예산 카테고리 막대의 상태.
 *
 * <p>초과는 <b>경고가 아니라 사실</b>이라 `destructive`다. 임박(90% 이상)은 아직 사고가
 * 아니므로 `warning`이고, 그 아래는 색을 쓰지 않는다 — 전부 칠하면 정말 넘긴 줄이 묻힌다.
 */
export type BudgetTone = "over" | "near" | "normal";

export function budgetTone(used: number, limit: number): BudgetTone {
  if (limit <= 0) {
    return "normal";
  }
  const ratio = used / limit;
  if (ratio > 1) {
    return "over";
  }
  return ratio >= 0.9 ? "near" : "normal";
}
