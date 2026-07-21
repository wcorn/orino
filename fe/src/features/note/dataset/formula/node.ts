/**
 * 수식 구문 트리. BE `FormulaNode`(sealed interface)를 discriminated union으로 옮긴다.
 * 파싱이 끝나면 label·행 번호가 남지 않고 열 key와 행 id로만 이뤄진다.
 */
export type RefKind = "SAME_ROW" | "ABSOLUTE" | "COLUMN_ALL";

export type RefNode = {
  type: "ref";
  kind: RefKind;
  colKey: string;
  rowId: number | null;
};

export type FormulaNode =
  | { type: "num"; value: number }
  | { type: "str"; value: string }
  | { type: "unary"; op: "+" | "-"; operand: FormulaNode }
  | { type: "binary"; op: string; left: FormulaNode; right: FormulaNode }
  | { type: "compare"; op: string; left: FormulaNode; right: FormulaNode }
  | RefNode
  | { type: "agg"; func: string; colKeys: string[] }
  | {
      type: "aggif";
      func: string;
      critCol: string;
      criteria: FormulaNode;
      sumCol: string | null;
    }
  | { type: "call"; func: string; args: FormulaNode[] };
