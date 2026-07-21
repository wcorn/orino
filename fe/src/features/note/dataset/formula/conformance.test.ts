import { describe, expect, it } from "vitest";

import { FormulaContext, ValueSource } from "./context";
import { evaluateFormula } from "./index";

/**
 * FE 평가기의 골든(conformance) 스위트. BE `FormulaConformanceTest.java`와 **동일한 입력/기대**를 둔다 —
 * 두 구현이 같은 값을 내는지 교차검증하는 SSOT(Epic #892 ADR-2). 케이스를 추가·수정하면 양쪽을 같이 바꾼다.
 *
 * <p>새 케이스는 float64로 정확히 표현되는 값만 쓴다(BE는 BigDecimal). 미리보기의 임의 소수 오차는
 * 골든이 아니라 BE 확정값이 다룬다.
 */

/** BE `FormulaConformanceTest.Model`과 같은 인메모리 표. 행 번호 N ↔ 행 id 100+N, 현재 행은 0번. */
class Model implements FormulaContext, ValueSource {
  private readonly labels = new Map<string, string>();
  private readonly rows: Record<string, string>[] = [];

  col(key: string, label: string): this {
    this.labels.set(key, label);
    return this;
  }

  row(...cells: string[]): this {
    const keys = [...this.labels.keys()];
    const row: Record<string, string> = {};
    keys.forEach((k, i) => (row[k] = i < cells.length ? cells[i] : ""));
    this.rows.push(row);
    return this;
  }

  columnKeys(): string[] {
    return [...this.labels.keys()];
  }
  keyByLabel(label: string): string | undefined {
    for (const [k, v] of this.labels) {
      if (v === label) {
        return k;
      }
    }
    return undefined;
  }
  labelByKey(key: string): string | undefined {
    return this.labels.get(key);
  }
  rowIdByNumber(n: number): number | undefined {
    return n >= 1 && n <= this.rows.length ? 100 + n : undefined;
  }
  rowNumberById(rowId: number): number | undefined {
    const n = rowId - 100;
    return n >= 1 && n <= this.rows.length ? n : undefined;
  }
  sameRow(colKey: string): string | undefined {
    return this.labels.has(colKey) ? this.rows[0][colKey] : undefined;
  }
  absolute(rowId: number, colKey: string): string | undefined {
    const n = rowId - 100;
    if (n < 1 || n > this.rows.length || !this.labels.has(colKey)) {
      return undefined;
    }
    return this.rows[n - 1][colKey];
  }
  column(colKey: string): string[] | undefined {
    return this.labels.has(colKey)
      ? this.rows.map((r) => r[colKey])
      : undefined;
  }
}

function model(): Model {
  return (
    new Model()
      .col("c0", "수량")
      .col("c1", "단가")
      .col("c2", "메모")
      .col("c3", "재고")
      .col("c4", "상태")
      //     수량   단가   메모  재고  상태
      .row("2", "3", "a", "", "#DIV/0!")
      .row("4", "5", "", "1", "")
      .row("", "10", "x", "2", "")
  );
}

// [이름, 수식, 기대값] — BE FormulaConformanceTest.cases()와 1:1로 맞춘다.
const CASES: [string, string, string][] = [
  // ── 산술 ──
  ["사칙연산 우선순위", "=1 + 2 * 3", "7"],
  ["같은 행 참조 곱", "={수량} * {단가}", "6"],
  ["빈 셀은 0", "={재고} + {수량}", "2"],
  ["0으로 나누면 #DIV/0!", "=1 / 0", "#DIV/0!"],
  // ── 참조 에러 ──
  ["텍스트를 산술하면 #VALUE!", "={메모} + 1", "#VALUE!"],
  ["에러 셀은 그대로 번진다", "={상태} + 1", "#DIV/0!"],
  ["절대 참조(행 번호)", "={단가}2", "5"],
  // ── 집계 ──
  ["SUM은 빈 셀을 무시", "=SUM({수량})", "6"],
  ["COUNT는 숫자만 센다", "=COUNT({수량})", "2"],
  ["AVG", "=AVG({단가})", "6"],
  ["숫자 없는 열 AVG는 #DIV/0!", "=AVG({메모})", "#DIV/0!"],
  ["MIN", "=MIN({단가})", "3"],
  ["MAX", "=MAX({단가})", "10"],
  ["집계는 텍스트를 무시(합 0)", "=SUM({메모})", "0"],
  // ── 스칼라 함수 ABS ──
  ["ABS 리터럴", "=ABS(-5)", "5"],
  ["ABS 식 인자", "=ABS({수량} - {단가})", "1"],
  ["ABS 빈 셀 인자", "=ABS({재고} - 3)", "3"],
  ["ABS 인자 에러 전파", "=ABS({메모})", "#VALUE!"],
  // ── 비교 → boolean ──
  ["숫자 비교", "={수량} < {단가}", "TRUE"],
  ["숫자 같음", "={수량} = 2", "TRUE"],
  ["다름", "={수량} <> 2", "FALSE"],
  ["텍스트 비교(대소문자 무시)", '={메모} = "A"', "TRUE"],
  ["교차 타입: 텍스트 > 숫자", "={메모} > 999", "TRUE"],
  ["비교 피연산자 에러 전파", "={상태} = 1", "#DIV/0!"],
  // ── IF ──
  ["IF 참 가지", "=IF({수량} = 2, {단가}, -1)", "3"],
  ["IF 거짓 가지", "=IF({수량} = 9, {단가}, -1)", "-1"],
  ["환율 자동환산 예시", '=IF({메모} = "a", {수량} * 10, {수량})', "20"],
  ["IF 지연 평가 — 안 고른 가지 에러 무시", "=IF({수량} > 0, 7, 1 / 0)", "7"],
  ["IF 조건 에러 전파", "=IF({상태} = 1, 1, 0)", "#DIV/0!"],
  // ── AND/OR/NOT ──
  ["AND", "=AND({수량} = 2, {단가} = 3)", "TRUE"],
  ["AND 하나 거짓", "=AND({수량} = 2, {단가} = 9)", "FALSE"],
  ["OR", "=OR({수량} = 9, {단가} = 3)", "TRUE"],
  ["NOT", "=NOT({수량} = 2)", "FALSE"],
  ["숫자 0은 거짓", "=NOT({재고})", "TRUE"],
  ["문자열 인자는 #VALUE!", "=AND({메모}, 1 = 1)", "#VALUE!"],
  // ── 산술이 boolean·문자열을 만나면 ──
  ["boolean은 산술에서 1", "=(1 < 2) + 5", "6"],
  ["문자열 산술은 #VALUE!", '="a" + 1', "#VALUE!"],
  // ── 조건부 집계 SUMIF·COUNTIF ──
  ["COUNTIF 텍스트 정확일치", '=COUNTIF({메모}, "a")', "1"],
  ["COUNTIF 숫자 정확일치", "=COUNTIF({단가}, 3)", "1"],
  ["COUNTIF 연산자 criteria", '=COUNTIF({단가}, ">4")', "2"],
  ["COUNTIF 부정 — 빈 셀은 스킵", '=COUNTIF({메모}, "<>a")', "1"],
  ["SUMIF 텍스트 조건", '=SUMIF({메모}, "a", {수량})', "2"],
  ["SUMIF 숫자 조건 — 합 열 빈칸 무시", '=SUMIF({단가}, ">4", {수량})', "4"],
  ["SUMIF — 조건 열 빈 행 스킵", '=SUMIF({수량}, ">0", {단가})', "8"],
  ["criteria가 식이어도 된다", "=COUNTIF({수량}, {단가} - 1)", "1"],
  ["조건 열 에러는 번진다", '=COUNTIF({상태}, "x")', "#DIV/0!"],
];

describe("FE 수식 평가기 골든 (BE와 교차검증)", () => {
  it.each(CASES)("%s: %s → %s", (_name, formula, expected) => {
    const m = model();
    expect(evaluateFormula(formula, m, m)).toBe(expected);
  });
});
