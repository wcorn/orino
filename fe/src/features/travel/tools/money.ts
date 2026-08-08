/**
 * 환율 입력 다루기.
 *
 * <p>현지에서 한 손으로 두드리는 화면이라, 입력이 조금 지저분해도 받아 준다 —
 * 콤마가 섞이든 앞에 0이 붙든 숫자로 읽는다.
 */

/** 입력 문자열 → 숫자. 숫자가 아니면 null(빈 칸과 잘못된 입력을 구분하지 않는다). */
export function parseAmount(input: string): number | null {
  const cleaned = input.replace(/,/g, "").trim();
  if (cleaned === "") return null;
  const value = Number(cleaned);
  return Number.isFinite(value) && value >= 0 ? value : null;
}

/**
 * 천단위 콤마.
 *
 * <p>소수는 <b>최대 두 자리</b>까지만 보여준다 — 환전소에서 원 단위 소수점을 볼 일이 없고,
 * 길어지면 오히려 못 읽는다.
 */
export function formatAmount(value: number): string {
  return value.toLocaleString("ko-KR", { maximumFractionDigits: 2 });
}

/** 반대편 금액. 소수 오차가 쌓이지 않게 표시 직전에 한 번만 반올림한다. */
export function convert(amount: number, rate: number): number {
  return Math.round(amount * rate * 100) / 100;
}
