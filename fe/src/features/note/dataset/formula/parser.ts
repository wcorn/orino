import { FormulaContext, FormulaSyntaxError } from "./context";
import { FormulaNode, RefNode } from "./node";

/** 집계 함수 — 인자가 열 참조(열 전체). */
const AGGREGATES = new Set(["SUM", "AVG", "COUNT", "MIN", "MAX"]);
/** 조건부 집계 — 열 인자 + criteria 식. */
const COND_AGGREGATES = new Set(["SUMIF", "COUNTIF"]);
/** 스칼라 함수 — 인자가 식. [최소, 최대] arity. */
const SCALAR_FUNCS: Record<string, [number, number]> = {
  ABS: [1, 1],
  IF: [3, 3],
  AND: [1, Infinity],
  OR: [1, Infinity],
  NOT: [1, 1],
};

const EOF = "\0";

/**
 * 수식 파서. BE `FormulaParser`(재귀 하강)를 그대로 옮긴다. 문법·우선순위·오류 지점이 동일해야
 * 골든 케이스에서 BE와 일치한다.
 */
class Parser {
  private pos = 0;

  constructor(
    private readonly src: string,
    private readonly ctx: FormulaContext,
    private readonly stored: boolean,
  ) {}

  parseAll(): FormulaNode {
    this.skipSpace();
    this.expect("=");
    const node = this.expr();
    this.skipSpace();
    if (this.pos < this.src.length) {
      throw this.syntaxError("수식 뒤에 남은 문자가 있습니다");
    }
    return node;
  }

  /** 최상위 = 비교. 산술보다 우선순위가 낮다(엑셀과 같다). 좌결합. */
  private expr(): FormulaNode {
    let left = this.additive();
    for (;;) {
      this.skipSpace();
      const op = this.peekCompareOp();
      if (op === null) {
        return left;
      }
      this.pos += op.length;
      left = { type: "compare", op, left, right: this.additive() };
    }
  }

  private peekCompareOp(): string | null {
    const c = this.peek();
    const n = this.pos + 1 < this.src.length ? this.src[this.pos + 1] : EOF;
    switch (c) {
      case "=":
        return "=";
      case "<":
        return n === "=" ? "<=" : n === ">" ? "<>" : "<";
      case ">":
        return n === "=" ? ">=" : ">";
      default:
        return null;
    }
  }

  private additive(): FormulaNode {
    let left = this.term();
    for (;;) {
      this.skipSpace();
      const c = this.peek();
      if (c !== "+" && c !== "-") {
        return left;
      }
      this.pos++;
      left = { type: "binary", op: c, left, right: this.term() };
    }
  }

  private term(): FormulaNode {
    let left = this.unary();
    for (;;) {
      this.skipSpace();
      const c = this.peek();
      if (c !== "*" && c !== "/") {
        return left;
      }
      this.pos++;
      left = { type: "binary", op: c, left, right: this.unary() };
    }
  }

  private unary(): FormulaNode {
    this.skipSpace();
    const c = this.peek();
    if (c === "-" || c === "+") {
      this.pos++;
      return { type: "unary", op: c, operand: this.unary() };
    }
    return this.primary();
  }

  private primary(): FormulaNode {
    this.skipSpace();
    const c = this.peek();
    if (c === "(") {
      this.pos++;
      const inner = this.expr();
      this.skipSpace();
      this.expect(")");
      return inner;
    }
    if (c === "{") {
      return this.ref(false);
    }
    if (c === '"') {
      return this.string();
    }
    if (isDigit(c) || c === ".") {
      return this.number();
    }
    if (isLetter(c)) {
      return this.functionCall();
    }
    throw this.syntaxError(`예상하지 못한 문자: '${c}'`);
  }

  /** `"..."` — 안의 `""`는 큰따옴표 하나로 읽는다(엑셀식 이스케이프). */
  private string(): FormulaNode {
    this.expect('"');
    let out = "";
    for (;;) {
      if (this.pos >= this.src.length) {
        throw this.syntaxError("문자열을 닫는 '\"'가 없습니다");
      }
      const c = this.src[this.pos++];
      if (c === '"') {
        if (this.peek() === '"') {
          out += '"';
          this.pos++;
        } else {
          return { type: "str", value: out };
        }
      } else {
        out += c;
      }
    }
  }

  private number(): FormulaNode {
    const start = this.pos;
    while (
      this.pos < this.src.length &&
      (isDigit(this.src[this.pos]) || this.src[this.pos] === ".")
    ) {
      this.pos++;
    }
    const raw = this.src.slice(start, this.pos);
    const value = Number(raw);
    if (!Number.isFinite(value)) {
      throw this.syntaxError(`숫자를 읽을 수 없습니다: ${raw}`);
    }
    return { type: "num", value };
  }

  private functionCall(): FormulaNode {
    const start = this.pos;
    while (this.pos < this.src.length && isLetter(this.src[this.pos])) {
      this.pos++;
    }
    const name = this.src.slice(start, this.pos).toUpperCase();
    if (AGGREGATES.has(name)) {
      return this.aggregate(name);
    }
    if (COND_AGGREGATES.has(name)) {
      return this.conditionalAggregate(name);
    }
    if (name in SCALAR_FUNCS) {
      return this.scalar(name);
    }
    throw this.syntaxError(`지원하지 않는 함수: ${name}`);
  }

  private scalar(name: string): FormulaNode {
    this.skipSpace();
    this.expect("(");
    const args: FormulaNode[] = [];
    this.skipSpace();
    if (this.peek() !== ")") {
      args.push(this.expr());
      this.skipSpace();
      while (this.peek() === ",") {
        this.pos++;
        args.push(this.expr());
        this.skipSpace();
      }
    }
    this.expect(")");
    const [min, max] = SCALAR_FUNCS[name];
    if (args.length < min || args.length > max) {
      throw this.syntaxError(
        `함수 ${name}의 인자 개수가 맞지 않습니다: ${args.length}`,
      );
    }
    return { type: "call", func: name, args };
  }

  private conditionalAggregate(name: string): FormulaNode {
    const sumif = name === "SUMIF";
    this.skipSpace();
    this.expect("(");
    const critCol = (this.ref(true) as RefNode).colKey;
    this.skipSpace();
    this.expect(",");
    const criteria = this.expr();
    let sumCol: string | null = null;
    if (sumif) {
      this.skipSpace();
      this.expect(",");
      sumCol = (this.ref(true) as RefNode).colKey;
    }
    this.skipSpace();
    this.expect(")");
    return { type: "aggif", func: name, critCol, criteria, sumCol };
  }

  private aggregate(name: string): FormulaNode {
    this.skipSpace();
    this.expect("(");
    const keys: string[] = [];
    const first = this.ref(true) as RefNode;
    this.skipSpace();
    if (this.peek() === ":") {
      this.pos++;
      const last = this.ref(true) as RefNode;
      keys.push(...this.expandRange(first.colKey, last.colKey));
    } else {
      keys.push(first.colKey);
      while (this.peek() === ",") {
        this.pos++;
        keys.push((this.ref(true) as RefNode).colKey);
        this.skipSpace();
      }
    }
    this.skipSpace();
    this.expect(")");
    return { type: "agg", func: name, colKeys: [...new Set(keys)] };
  }

  private expandRange(fromKey: string, toKey: string): string[] {
    const all = this.ctx.columnKeys();
    const from = all.indexOf(fromKey);
    const to = all.indexOf(toKey);
    if (from < 0 || to < 0) {
      throw this.syntaxError("범위의 열을 찾을 수 없습니다");
    }
    return all.slice(Math.min(from, to), Math.max(from, to) + 1);
  }

  private ref(columnOnly: boolean): FormulaNode {
    this.skipSpace();
    this.expect("{");
    const close = this.src.indexOf("}", this.pos);
    if (close < 0) {
      throw this.syntaxError("열 이름을 닫는 '}'가 없습니다");
    }
    const name = this.src.slice(this.pos, close);
    this.pos = close + 1;
    if (name.trim() === "") {
      throw this.syntaxError("열 이름이 비었습니다");
    }
    const key = this.resolveKey(name);
    const rowId = this.rowSuffix();
    if (rowId !== null && columnOnly) {
      throw this.syntaxError(
        `집계 함수 안에서는 행을 지정할 수 없습니다: {${name}}`,
      );
    }
    if (columnOnly) {
      return { type: "ref", kind: "COLUMN_ALL", colKey: key, rowId: null };
    }
    return rowId === null
      ? { type: "ref", kind: "SAME_ROW", colKey: key, rowId: null }
      : { type: "ref", kind: "ABSOLUTE", colKey: key, rowId };
  }

  private resolveKey(name: string): string {
    if (this.stored) {
      if (!this.ctx.columnKeys().includes(name)) {
        throw this.syntaxError(`없는 열: ${name}`);
      }
      return name;
    }
    const key = this.ctx.keyByLabel(name);
    if (key === undefined) {
      throw this.syntaxError(`없는 열: ${name}`);
    }
    return key;
  }

  private rowSuffix(): number | null {
    if (this.stored) {
      if (this.peek() !== "@") {
        return null;
      }
      this.pos++;
      return this.digits("행 id");
    }
    if (!isDigit(this.peek())) {
      return null;
    }
    const number = this.digits("행 번호");
    const rowId = this.ctx.rowIdByNumber(number);
    if (rowId === undefined) {
      throw this.syntaxError(`없는 행: ${number}`);
    }
    return rowId;
  }

  private digits(what: string): number {
    const start = this.pos;
    while (this.pos < this.src.length && isDigit(this.src[this.pos])) {
      this.pos++;
    }
    if (start === this.pos) {
      throw this.syntaxError(`${what}가 필요합니다`);
    }
    return Number(this.src.slice(start, this.pos));
  }

  private peek(): string {
    return this.pos < this.src.length ? this.src[this.pos] : EOF;
  }

  private expect(c: string): void {
    if (this.peek() !== c) {
      throw this.syntaxError(`'${c}'가 필요합니다`);
    }
    this.pos++;
  }

  private skipSpace(): void {
    while (this.pos < this.src.length && this.src[this.pos] === " ") {
      this.pos++;
    }
  }

  private syntaxError(detail: string): FormulaSyntaxError {
    return new FormulaSyntaxError(detail);
  }
}

const isDigit = (c: string): boolean => c >= "0" && c <= "9";
/** BE는 `Character.isLetter` — 유니코드 글자(한글 포함). 함수명은 ASCII지만 범위는 넓게 잡는다. */
const isLetter = (c: string): boolean => c !== EOF && /\p{L}/u.test(c);

/** 사용자가 친 수식(label·행 번호) → 트리. */
export function parseInput(text: string, ctx: FormulaContext): FormulaNode {
  return new Parser(text, ctx, false).parseAll();
}

/** 저장된 수식(열 key·행 id) → 트리. */
export function parseStored(text: string, ctx: FormulaContext): FormulaNode {
  return new Parser(text, ctx, true).parseAll();
}

/** 트리가 참조하는 것들(중복 합침). 의존성 계산·재계산 범위 판단용. */
export function collectRefs(node: FormulaNode): RefNode[] {
  const out: RefNode[] = [];
  collect(node, out);
  const seen = new Set<string>();
  return out.filter((r) => {
    const k = `${r.kind}|${r.colKey}|${r.rowId}`;
    if (seen.has(k)) {
      return false;
    }
    seen.add(k);
    return true;
  });
}

function collect(node: FormulaNode, out: RefNode[]): void {
  switch (node.type) {
    case "ref":
      out.push(node);
      break;
    case "agg":
      node.colKeys.forEach((k) =>
        out.push({ type: "ref", kind: "COLUMN_ALL", colKey: k, rowId: null }),
      );
      break;
    case "aggif":
      out.push({
        type: "ref",
        kind: "COLUMN_ALL",
        colKey: node.critCol,
        rowId: null,
      });
      if (node.sumCol !== null) {
        out.push({
          type: "ref",
          kind: "COLUMN_ALL",
          colKey: node.sumCol,
          rowId: null,
        });
      }
      collect(node.criteria, out);
      break;
    case "binary":
    case "compare":
      collect(node.left, out);
      collect(node.right, out);
      break;
    case "unary":
      collect(node.operand, out);
      break;
    case "call":
      node.args.forEach((a) => collect(a, out));
      break;
    case "num":
    case "str":
      break;
  }
}
