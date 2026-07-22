import type { NumberFormat } from "./api/datasets";

/**
 * 열 숫자 서식(표시 전용). 값·수식은 raw를 쓰고, 화면에만 이걸로 포맷한다(R2). 숫자가 아니면
 * (텍스트·`#에러`·빈칸) 원본을 그대로 둔다 — 서식은 숫자 표시만 바꾸지 값을 바꾸지 않는다.
 */
const FORMATTERS: Record<NumberFormat, Intl.NumberFormat> = {
  KRW: new Intl.NumberFormat("ko-KR", { style: "currency", currency: "KRW" }),
  USD: new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }),
  JPY: new Intl.NumberFormat("ja-JP", { style: "currency", currency: "JPY" }),
  THOUSANDS: new Intl.NumberFormat("en-US", { maximumFractionDigits: 0 }),
  DECIMAL1: new Intl.NumberFormat("en-US", {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }),
  DECIMAL2: new Intl.NumberFormat("en-US", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }),
};

/** 서식이 있고 값이 숫자면 포맷, 아니면 원본 그대로. */
export function formatCellValue(value: string, format?: NumberFormat): string {
  if (!format || value === "") return value;
  const n = Number(value);
  if (!Number.isFinite(n)) return value; // 텍스트·에러 셀은 건드리지 않는다.
  return FORMATTERS[format].format(n);
}

/** 서식 토큰 → 메뉴 라벨(짧게). */
export const FORMAT_LABELS: Record<NumberFormat, string> = {
  KRW: "₩",
  USD: "$",
  JPY: "¥",
  THOUSANDS: "1,000",
  DECIMAL1: "0.0",
  DECIMAL2: "0.00",
};

/** 메뉴에 노출할 순서. */
export const NUMBER_FORMATS: NumberFormat[] = [
  "KRW",
  "USD",
  "JPY",
  "THOUSANDS",
  "DECIMAL1",
  "DECIMAL2",
];
