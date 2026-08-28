import type { LedgerFlow } from "../api/ledger";

/**
 * 음수 부호는 **U+2212(−)** 다. 하이픈(-)이 아니다.
 *
 * <p>하이픈은 폭이 좁아 `tabular-nums` 열에서 자릿수가 흔들려 보이고, 금액 옆에서는
 * 이음표처럼 읽힌다. 이 모듈은 숫자를 세로로 훑는 화면이 대부분이라 그 차이가 크다.
 */
export const MINUS = "−";

/** 천 단위 콤마. 원 단위 정수라 소수점이 없다. */
export function formatAmount(amount: number): string {
  return Math.abs(amount).toLocaleString("ko-KR");
}

/**
 * 부호까지 붙인 표기. 수입은 `+`, 지출·이체는 `−`(U+2212).
 *
 * <p>지출을 빨갛게 칠하지 않는다 — 가계부에서 지출은 <b>정상</b>이다. 전부 빨가면
 * 정말 위험한 것(부채·초과·미납)이 묻힌다(화면 설계 §1).
 */
export function formatSigned(amount: number, flow: LedgerFlow): string {
  if (flow === "INCOME") {
    return `+${formatAmount(amount)}`;
  }
  return `${MINUS}${formatAmount(amount)}`;
}

/** 잔액처럼 부호가 값 자체에 있는 경우. 0은 부호를 붙이지 않는다. */
export function formatBalance(amount: number): string {
  if (amount < 0) {
    return `${MINUS}${formatAmount(amount)}`;
  }
  return formatAmount(amount);
}

/** 금액 글자색. 수입만 초록이고 지출은 기본색이다. */
export function amountToneClass(flow: LedgerFlow): string {
  return flow === "INCOME" ? "text-success" : "text-foreground";
}

/**
 * 외화 보조 표기 — `1,280 JPY · 8.7604 KRW/JPY`.
 *
 * <p>본문 금액은 언제나 원화(`amount`)다. 이건 <b>근거</b>를 덧붙이는 줄이고,
 * 화면이 이 값으로 다시 환산하지 않는다(D-13).
 */
export function formatFxNote(fx: {
  currency: string;
  amount: number;
  rate: number;
}): string {
  const amount = fx.amount.toLocaleString("ko-KR", {
    maximumFractionDigits: 2,
  });
  return `${amount} ${fx.currency} · ${fx.rate} KRW/${fx.currency}`;
}

/** `2026-08-25` → `8월 25일 화`. 날짜 그룹 헤더용. */
export function formatDateHeader(isoDate: string): string {
  const date = new Date(`${isoDate}T00:00:00`);
  const weekday = ["일", "월", "화", "수", "목", "금", "토"][date.getDay()];
  return `${date.getMonth() + 1}월 ${date.getDate()}일 ${weekday}`;
}

/** 오늘로부터 며칠 뒤인가. 예정 줄의 D-day. */
export function dDayFrom(todayIso: string, targetIso: string): number {
  const today = new Date(`${todayIso}T00:00:00`).getTime();
  const target = new Date(`${targetIso}T00:00:00`).getTime();
  return Math.round((target - today) / 86_400_000);
}
