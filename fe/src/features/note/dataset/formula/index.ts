/**
 * FE 경량 수식 평가기. 계산 SSOT는 BE고, 이건 입력 즉시 미리보기용이다(Epic #892 ADR-2).
 * BE `formula` 패키지와 동일 함수 집합을 구현하며, `conformance.test.ts`의 골든 케이스로 교차검증한다.
 */
export type { FormulaContext, ValueSource } from "./context";
export { FormulaSyntaxError } from "./context";
export { aggregate, evaluate, evaluateToCell } from "./evaluator";
export { collectRefs, parseInput, parseStored } from "./parser";
export type { FormulaValue } from "./value";
export { asCell } from "./value";

import { FormulaContext, ValueSource } from "./context";
import { evaluateToCell } from "./evaluator";
import { parseInput } from "./parser";

/**
 * 사용자가 친 수식(label·행 번호)을 파싱·평가해 셀에 담길 문자열을 돌려준다.
 * 문법 오류는 던진다(호출부가 잡아 BE 확정에 맡긴다).
 */
export function evaluateFormula(
  input: string,
  ctx: FormulaContext,
  source: ValueSource,
): string {
  return evaluateToCell(parseInput(input, ctx), source);
}
