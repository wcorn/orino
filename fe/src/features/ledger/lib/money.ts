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

/**
 * 「41.2만」. 큰 숫자를 한 줄에 여러 개 늘어놓는 자리에서 쓴다.
 *
 * <p>`412,000`을 그대로 쓰면 「80만 중 41.2만」 한 줄이 「800,000 중 412,000」이 되어,
 * 읽으려면 자릿수를 세어야 한다. 예산 카드는 <b>비율을 눈으로 잡는</b> 자리라 그게 곧 실패다.
 *
 * <p>여행 경비 카드와 월 예산의 「이 달 여행으로」 줄이 함께 쓴다 — 같은 금액을 두
 * 화면이 다르게 줄이면 사용자는 둘이 다른 값이라고 읽는다.
 *
 * <p>만 미만은 콤마 그대로 둔다 — 「0.4만」은 4,000보다 읽기 어렵다.
 * 소수는 한 자리까지만 남기고, 딱 떨어지면 「80만」처럼 뗀다.
 */
export function formatCompactAmount(amount: number): string {
  const abs = Math.abs(amount);
  if (abs < 10_000) {
    return amount.toLocaleString("ko-KR");
  }
  const man = amount / 10_000;
  // toFixed(1)이 만드는 `.0`은 떼고, 억 단위여도 만으로 읽는다 — 여행 경비에서
  // 「1.2억」이 나올 일이 없고, 단위를 섞으면 두 숫자를 비교할 수 없다.
  const rounded = Math.round(man * 10) / 10;
  return `${rounded.toLocaleString("ko-KR", { maximumFractionDigits: 1 })}만`;
}
