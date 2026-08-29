import type { StatementBreakdown, StatementStatus } from "../api/ledger";

/**
 * 청구서를 <b>화면에 그리기 위한</b> 계산만 있다.
 *
 * <p><b>판정은 서버 값을 쓴다</b>([D-13](https://github.com/wcorn/orino/wiki/Ledger-Open-Items)).
 * 「미납인가」·「청구액이 얼마인가」는 서버가 정한 값이고 여기서 다시 더하지 않는다 —
 * 산식을 두 곳에 두면 그중 하나만 틀려도 조용히 어긋나고, 그게 이 모듈에서 가장 비싼 버그다.
 */

/** 상태 스테퍼 4단계(§7.1). 순서가 곧 돈이 나가는 순서다. */
export const STATEMENT_STEPS: { value: StatementStatus; label: string }[] = [
  { value: "COLLECTING", label: "집계 중" },
  { value: "CONFIRMED", label: "확정" },
  { value: "PARTIAL", label: "부분 납부" },
  { value: "PAID", label: "납부 완료" },
];

export const STATEMENT_STATUS_LABELS: Record<StatementStatus, string> = {
  COLLECTING: "집계 중",
  CONFIRMED: "확정",
  PARTIAL: "부분 납부",
  PAID: "납부 완료",
};

/** 브레이크다운 한 줄. `sign`은 산식에서 그 항목이 붙는 부호다. */
export interface BreakdownRow {
  key: keyof StatementBreakdown;
  label: string;
  amount: number;
  sign: "+" | "−";
}

/**
 * 산식을 <b>그대로</b> 줄로 편다(§7.4).
 *
 * ```
 * 청구액 = 사용 + 할부 회차 + 이월 + 이자·수수료 + 차액 − 환불 − 할인
 * ```
 *
 * <p>0인 항목은 빼되 <b>사용 합계는 언제나 남긴다</b> — 그 줄이 없으면 「이번 달 안 썼다」와
 * 「이 화면이 사용을 못 세고 있다」가 구분되지 않는다.
 */
export function breakdownRows(breakdown: StatementBreakdown): BreakdownRow[] {
  const rows: BreakdownRow[] = [
    { key: "usage", label: "사용 합계", amount: breakdown.usage, sign: "+" },
    {
      key: "installment",
      label: "할부 회차",
      amount: breakdown.installment,
      sign: "+",
    },
    {
      key: "carriedOver",
      label: "이월 잔액",
      amount: breakdown.carriedOver,
      sign: "+",
    },
    {
      key: "interestFee",
      label: "이자·수수료",
      amount: breakdown.interestFee,
      sign: "+",
    },
    {
      key: "adjustment",
      label: "차액 조정",
      amount: breakdown.adjustment,
      sign: "+",
    },
    { key: "refund", label: "환불", amount: breakdown.refund, sign: "−" },
    { key: "discount", label: "할인", amount: breakdown.discount, sign: "−" },
  ];
  return rows.filter((row) => row.key === "usage" || row.amount !== 0);
}

/** 한도 대비 사용률(%). 한도를 안 정했으면 `null` — 0으로 두면 막대가 거짓말을 한다. */
export function limitUsage(
  unpaidAmount: number,
  creditLimit: number | null,
): number | null {
  if (creditLimit === null || creditLimit <= 0) {
    return null;
  }
  return Math.min((unpaidAmount / creditLimit) * 100, 100);
}

/** `매월 1일 ~ 말일 · 결제일 매월 14일`. 99는 말일이다. */
export function cycleLabel(
  cycleStartDay: number | null,
  cycleCloseDay: number | null,
  paymentDay: number | null,
): string {
  if (cycleStartDay === null || cycleCloseDay === null || paymentDay === null) {
    return "사이클 미등록";
  }
  return `정산 매월 ${dayLabel(cycleStartDay)} ~ ${dayLabel(cycleCloseDay)} · 결제일 매월 ${dayLabel(paymentDay)}`;
}

function dayLabel(day: number): string {
  return day === 99 ? "말일" : `${day}일`;
}
