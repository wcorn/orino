/**
 * 금액 칸의 계산기.
 *
 * <p><b>왼쪽에서 오른쪽으로 순서대로</b> 계산한다. `1000+2000×2`는 6,000이지 5,000이 아니다 —
 * 곱셈 우선순위를 넣지 않은 것은 실수가 아니라 선택이다. 이 칸은 수식을 쓰는 자리가 아니라
 * <b>영수증을 더하는 자리</b>고, 사람은 손계산기처럼 누른 순서대로 읽는다.
 *
 * <p>원 단위 정수만 다룬다. 나눗셈은 반올림하고, 0으로 나누면 계산을 포기하고 `null`을 준다 —
 * 잘못된 금액을 조용히 저장하느니 저장 버튼이 비활성인 편이 낫다.
 */

const OPERATORS = ["+", "-", "×", "÷"] as const;

export type Operator = (typeof OPERATORS)[number];

export function isOperator(token: string): token is Operator {
  return (OPERATORS as readonly string[]).includes(token);
}

/** 수식에 연산자가 들어 있는지. 미리보기 줄을 그릴지 정할 때 쓴다. */
export function hasOperator(expression: string): boolean {
  return OPERATORS.some((op) => expression.includes(op));
}

/**
 * 수식을 원 단위 정수로 계산한다. 비어 있거나 계산할 수 없으면 `null`.
 *
 * <p>끝이 연산자면(`1000+`) 아직 입력 중이라는 뜻이므로 그 앞까지만 계산한다 —
 * 타이핑 도중에 값이 `null`로 깜빡이면 미리보기가 쓸모없어진다.
 */
export function evaluate(expression: string): number | null {
  const tokens = expression.match(/(\d+|[+\-×÷])/g);
  if (!tokens || tokens.length === 0) {
    return null;
  }
  let acc: number | null = null;
  let pending: Operator | null = null;

  for (const token of tokens) {
    if (isOperator(token)) {
      // 연산자가 연달아 오면 마지막 것만 남긴다(사람이 고쳐 누른 경우).
      pending = token;
      continue;
    }
    const value = Number(token);
    if (acc === null) {
      acc = value;
      continue;
    }
    if (pending === null) {
      // 숫자가 연달아 올 수 없는 입력이지만, 방어적으로 뒤엣것을 취한다.
      acc = value;
      continue;
    }
    if (pending === "÷" && value === 0) {
      return null;
    }
    acc = apply(acc, pending, value);
    pending = null;
  }
  return acc === null ? null : Math.round(acc);
}

function apply(left: number, operator: Operator, right: number): number {
  switch (operator) {
    case "+":
      return left + right;
    case "-":
      return left - right;
    case "×":
      return left * right;
    case "÷":
      return left / right;
  }
}

export const CALCULATOR_KEYS: Operator[] = ["+", "-", "×", "÷"];
