/**
 * 평가 결과. BE의 `FormulaValue`(Num/Text/Bool/Err)를 그대로 옮긴다 — 셀 단위로 값 아니면 에러.
 *
 * <p>미리보기용이라 숫자는 float64다(BE는 BigDecimal). 골든 케이스는 정확히 표현되는 값만 골라
 * 두 구현이 같은 문자열을 내도록 맞춘다. 임의 소수의 오차는 BE 확정값이 바로잡는다(ADR-2).
 */
export type FormulaValue =
  | { kind: "num"; value: number }
  | { kind: "text"; value: string }
  | { kind: "bool"; value: boolean }
  | { kind: "err"; code: string };

/** BE `FormulaValue.Err`와 같은 코드. */
export const ERR = {
  VALUE: "#VALUE!",
  DIV0: "#DIV/0!",
  REF: "#REF!",
} as const;

export const num = (value: number): FormulaValue => ({ kind: "num", value });
export const text = (value: string): FormulaValue => ({ kind: "text", value });
export const bool = (value: boolean): FormulaValue => ({ kind: "bool", value });
export const err = (code: string): FormulaValue => ({ kind: "err", code });

export const isErr = (v: FormulaValue): v is { kind: "err"; code: string } =>
  v.kind === "err";

/** 셀에 담길 문자열. BE `asCell()`과 같은 결과여야 한다. */
export function asCell(v: FormulaValue): string {
  switch (v.kind) {
    case "num":
      return formatNumber(v.value);
    case "text":
      return v.value;
    case "bool":
      return v.value ? "TRUE" : "FALSE";
    case "err":
      return v.code;
  }
}

/** BE의 `stripTrailingZeros().toPlainString()`에 대응. 지수 표기를 피한다. */
function formatNumber(v: number): string {
  if (!Number.isFinite(v)) {
    return ERR.VALUE;
  }
  const normalized = Object.is(v, -0) ? 0 : v;
  return String(normalized);
}
