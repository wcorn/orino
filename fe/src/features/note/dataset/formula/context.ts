/**
 * 파서·평가기가 바깥 데이터를 보는 창. BE의 `FormulaContext`/`FormulaEvaluator.ValueSource`에 대응.
 * `undefined`는 "없음"(→ `#REF!`), 빈 문자열은 "빈 셀"로 구분한다.
 */
export interface FormulaContext {
  /** 현재 열 key 목록(표시 순서). 범위 스냅샷의 기준. */
  columnKeys(): string[];
  /** label → key. 없으면 undefined. */
  keyByLabel(label: string): string | undefined;
  /** key → label. 표시용. */
  labelByKey(key: string): string | undefined;
  /** 화면 행 번호(1-base) → 행 id. */
  rowIdByNumber(rowNumber: number): number | undefined;
  /** 행 id → 현재 행 번호(1-base). */
  rowNumberById(rowId: number): number | undefined;
}

export interface ValueSource {
  /** 계산 중인 그 행의 열 값. 열이 없으면 undefined. */
  sameRow(colKey: string): string | undefined;
  /** 특정 행의 열 값. 행·열이 없으면 undefined. */
  absolute(rowId: number, colKey: string): string | undefined;
  /** 열 전체 값. 열이 없으면 undefined. */
  column(colKey: string): string[] | undefined;
}

/** 문법 오류. BE의 `FORMULA_SYNTAX_ERROR`에 대응. */
export class FormulaSyntaxError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "FormulaSyntaxError";
  }
}
