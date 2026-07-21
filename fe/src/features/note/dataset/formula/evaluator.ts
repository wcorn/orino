import { ValueSource } from "./context";
import { FormulaNode } from "./node";
import {
  asCell,
  bool,
  ERR,
  err,
  FormulaValue,
  isErr,
  num,
  text,
} from "./value";

/**
 * 구문 트리 평가. BE `FormulaEvaluator`를 그대로 옮긴다 — 값 규칙(강제 변환·에러 전파·교차타입 순위)이
 * 동일해야 골든 케이스에서 BE와 일치한다.
 */
export function evaluate(node: FormulaNode, src: ValueSource): FormulaValue {
  switch (node.type) {
    case "num":
      return num(node.value);
    case "str":
      return text(node.value);
    case "unary": {
      const n = asNumber(evaluate(node.operand, src));
      if (isErr(n)) {
        return n;
      }
      return num(
        node.op === "-"
          ? -(n as { value: number }).value
          : (n as { value: number }).value,
      );
    }
    case "binary":
      return binary(node, src);
    case "compare":
      return compare(node, src);
    case "ref":
      return ref(node, src);
    case "agg":
      return agg(node, src);
    case "aggif":
      return aggIf(node, src);
    case "call":
      return call(node, src);
  }
}

function call(
  node: Extract<FormulaNode, { type: "call" }>,
  src: ValueSource,
): FormulaValue {
  const a = node.args;
  switch (node.func) {
    case "ABS": {
      const n = asNumber(evaluate(a[0], src));
      return isErr(n) ? n : num(Math.abs((n as { value: number }).value));
    }
    case "IF": {
      const cond = asBool(evaluate(a[0], src));
      if (isErr(cond)) {
        return cond;
      }
      // 지연 평가: 고른 가지만 계산한다.
      return evaluate((cond as { value: boolean }).value ? a[1] : a[2], src);
    }
    case "NOT": {
      const v = asBool(evaluate(a[0], src));
      return isErr(v) ? v : bool(!(v as { value: boolean }).value);
    }
    case "AND":
    case "OR":
      return logical(node.func, a, src);
    default:
      return err(ERR.VALUE);
  }
}

/** AND/OR — 인자를 다 평가하되 에러는 전파. */
function logical(
  func: string,
  args: FormulaNode[],
  src: ValueSource,
): FormulaValue {
  const and = func === "AND";
  let acc = and;
  for (const arg of args) {
    const v = asBool(evaluate(arg, src));
    if (isErr(v)) {
      return v;
    }
    const b = (v as { value: boolean }).value;
    acc = and ? acc && b : acc || b;
  }
  return bool(acc);
}

function binary(
  node: Extract<FormulaNode, { type: "binary" }>,
  src: ValueSource,
): FormulaValue {
  const l = asNumber(evaluate(node.left, src));
  if (isErr(l)) {
    return l;
  }
  const r = asNumber(evaluate(node.right, src));
  if (isErr(r)) {
    return r;
  }
  const x = (l as { value: number }).value;
  const y = (r as { value: number }).value;
  switch (node.op) {
    case "+":
      return num(x + y);
    case "-":
      return num(x - y);
    case "*":
      return num(x * y);
    case "/":
      return y === 0 ? err(ERR.DIV0) : num(x / y);
    default:
      return err(ERR.VALUE);
  }
}

/** 비교 → boolean. 같은 타입끼리, 다르면 number<text<bool 순위. 텍스트는 대소문자 무시. */
function compare(
  node: Extract<FormulaNode, { type: "compare" }>,
  src: ValueSource,
): FormulaValue {
  const l = evaluate(node.left, src);
  if (isErr(l)) {
    return l;
  }
  const r = evaluate(node.right, src);
  if (isErr(r)) {
    return r;
  }
  return bool(satisfies(node.op, order(l, r)));
}

/** 비교 연산자를 order() 결과(음수/0/양수)에 적용. 비교·조건부 집계가 공유. */
function satisfies(op: string, cmp: number): boolean {
  switch (op) {
    case "=":
      return cmp === 0;
    case "<>":
      return cmp !== 0;
    case "<":
      return cmp < 0;
    case ">":
      return cmp > 0;
    case "<=":
      return cmp <= 0;
    case ">=":
      return cmp >= 0;
    default:
      return false;
  }
}

function order(l: FormulaValue, r: FormulaValue): number {
  if (l.kind === "num" && r.kind === "num") {
    return sign(l.value - r.value);
  }
  if (l.kind === "text" && r.kind === "text") {
    const a = l.value.toLowerCase();
    const b = r.value.toLowerCase();
    return a < b ? -1 : a > b ? 1 : 0;
  }
  if (l.kind === "bool" && r.kind === "bool") {
    return sign(Number(l.value) - Number(r.value));
  }
  return sign(rank(l) - rank(r));
}

function rank(v: FormulaValue): number {
  switch (v.kind) {
    case "num":
      return 0;
    case "text":
      return 1;
    case "bool":
      return 2;
    case "err":
      return 3;
  }
}

/** 산술·ABS 피연산자를 숫자로 강제. Bool→1/0, Text→#VALUE!(빈 셀은 이미 0). */
function asNumber(v: FormulaValue): FormulaValue {
  switch (v.kind) {
    case "num":
      return v;
    case "bool":
      return num(v.value ? 1 : 0);
    case "text":
      return err(ERR.VALUE);
    case "err":
      return v;
  }
}

/** IF 조건·AND/OR/NOT 인자를 boolean으로 강제. Num→0이면 거짓, Text는 "TRUE"/"FALSE"만. */
function asBool(v: FormulaValue): FormulaValue {
  switch (v.kind) {
    case "bool":
      return v;
    case "num":
      return bool(v.value !== 0);
    case "text":
      if (v.value.toUpperCase() === "TRUE") {
        return bool(true);
      }
      return v.value.toUpperCase() === "FALSE" ? bool(false) : err(ERR.VALUE);
    case "err":
      return v;
  }
}

/** 참조 → 셀 내용을 타입으로. 빈 칸은 0, 숫자는 Num, 에러 문자열은 번지고, 그 외는 Text. */
function ref(
  node: Extract<FormulaNode, { type: "ref" }>,
  src: ValueSource,
): FormulaValue {
  const raw =
    node.kind === "ABSOLUTE"
      ? src.absolute(node.rowId as number, node.colKey)
      : src.sameRow(node.colKey);
  if (raw === undefined) {
    return err(ERR.REF);
  }
  const s = raw.trim();
  if (s === "") {
    return num(0);
  }
  if (s.startsWith("#")) {
    return err(s);
  }
  return cellValue(s);
}

/** 집계. 숫자가 아닌 칸·빈 칸은 무시. 열이 없으면 #REF!, 셀이 에러면 번진다. */
function agg(
  node: Extract<FormulaNode, { type: "agg" }>,
  src: ValueSource,
): FormulaValue {
  const nums: number[] = [];
  for (const key of node.colKeys) {
    const col = src.column(key);
    if (col === undefined) {
      return err(ERR.REF);
    }
    for (const cell of col) {
      const s = (cell ?? "").trim();
      if (s.startsWith("#")) {
        return err(s);
      }
      const n = parseNumber(s);
      if (n !== null) {
        nums.push(n);
      }
    }
  }
  const sum = nums.reduce((a, b) => a + b, 0);
  switch (node.func) {
    case "COUNT":
      return num(nums.length);
    case "SUM":
      return num(sum);
    case "AVG":
      return nums.length === 0 ? err(ERR.DIV0) : num(sum / nums.length);
    case "MIN":
      return num(nums.length === 0 ? 0 : Math.min(...nums));
    case "MAX":
      return num(nums.length === 0 ? 0 : Math.max(...nums));
    default:
      return err(ERR.VALUE);
  }
}

/** 조건부 집계. criteria를 한 번 풀고 조건 열을 행별로 견준다. 빈 셀 스킵, 에러 전파. */
function aggIf(
  node: Extract<FormulaNode, { type: "aggif" }>,
  src: ValueSource,
): FormulaValue {
  const critRaw = evaluate(node.criteria, src);
  if (isErr(critRaw)) {
    return critRaw;
  }
  const crit = parseCriteria(critRaw);

  const critCells = src.column(node.critCol);
  if (critCells === undefined) {
    return err(ERR.REF);
  }
  const sumif = node.sumCol !== null;
  let sumCells: string[] = [];
  if (sumif) {
    const sc = src.column(node.sumCol as string);
    if (sc === undefined) {
      return err(ERR.REF);
    }
    sumCells = sc;
  }

  let sum = 0;
  let count = 0;
  for (let i = 0; i < critCells.length; i++) {
    const cell = (critCells[i] ?? "").trim();
    if (cell === "") {
      continue; // 빈 셀은 어떤 조건에도 매치하지 않는다
    }
    if (cell.startsWith("#")) {
      return err(cell);
    }
    if (!matches(crit.op, cellValue(cell), crit.target)) {
      continue;
    }
    count++;
    if (sumif) {
      const sc = (sumCells[i] ?? "").trim();
      if (sc.startsWith("#")) {
        return err(sc);
      }
      const n = parseNumber(sc);
      if (n !== null) {
        sum += n;
      }
    }
  }
  return num(sumif ? sum : count);
}

const CRITERIA_OPS = ["<=", ">=", "<>", "<", ">", "="];

function parseCriteria(v: FormulaValue): { op: string; target: FormulaValue } {
  if (v.kind === "text") {
    const s = v.value;
    for (const op of CRITERIA_OPS) {
      if (s.startsWith(op)) {
        return { op, target: cellValue(s.slice(op.length).trim()) };
      }
    }
    return { op: "=", target: cellValue(s) }; // "80"도 숫자로 봐 셀 80과 맞춘다
  }
  return { op: "=", target: v }; // 숫자·불린은 그대로 정확 일치
}

/** 셀·기준 문자열을 타입으로. 숫자로 읽히면 Num, 아니면 Text. */
function cellValue(s: string): FormulaValue {
  const n = parseNumber(s);
  return n !== null ? num(n) : text(s);
}

/** 같은 종류면 order()로, 다르면 <>만 참(텍스트 셀은 >80에 안 걸린다). */
function matches(
  op: string,
  cell: FormulaValue,
  target: FormulaValue,
): boolean {
  const sameKind =
    (cell.kind === "num" && target.kind === "num") ||
    (cell.kind === "text" && target.kind === "text");
  return sameKind ? satisfies(op, order(cell, target)) : op === "<>";
}

/** BE `BigDecimal` 파싱에 대응하는 엄격한 숫자 파서. 빈 문자열·비수치는 null. */
function parseNumber(s: string): number | null {
  if (s === "" || !/^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$/.test(s)) {
    return null;
  }
  const n = Number(s);
  return Number.isFinite(n) ? n : null;
}

const sign = (n: number): number => (n < 0 ? -1 : n > 0 ? 1 : 0);

/** 셀에 담길 문자열로 평가. 그리드 낙관적 재계산이 쓸 최종 형태. */
export function evaluateToCell(node: FormulaNode, src: ValueSource): string {
  return asCell(evaluate(node, src));
}
