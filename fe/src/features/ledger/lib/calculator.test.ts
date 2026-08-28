import { describe, expect, it } from "vitest";

import { evaluate, hasOperator } from "./calculator";

/**
 * 금액 칸의 계산기 — 순수 로직이라 단위 테스트로 본다.
 *
 * <p>여기서 고정하는 것은 <b>왼쪽에서 오른쪽</b>이라는 규칙이다. 곱셈 우선순위를 넣지 않은 것은
 * 실수가 아니라 선택이고, 나중에 누가 「고치려」 들면 이 테스트가 막는다.
 */
describe("계산기", () => {
  it("숫자 하나는 그대로 금액이다", () => {
    expect(evaluate("4500")).toBe(4500);
  });

  it("영수증을 더한다", () => {
    expect(evaluate("12000+3000")).toBe(15000);
  });

  it("누른 순서대로 계산한다 — 곱셈을 먼저 하지 않는다", () => {
    // 손계산기와 같다. 1000+2000 = 3000, ×2 = 6000.
    expect(evaluate("1000+2000×2")).toBe(6000);
  });

  it("나눗셈은 원 단위로 반올림한다", () => {
    expect(evaluate("10000÷3")).toBe(3333);
  });

  it("0으로 나누면 계산을 포기한다 — 잘못된 금액을 저장하느니 저장 버튼이 잠기는 편이 낫다", () => {
    expect(evaluate("10000÷0")).toBeNull();
  });

  it("끝이 연산자면 그 앞까지 계산한다 — 타이핑 도중 미리보기가 깜빡이지 않는다", () => {
    expect(evaluate("12000+")).toBe(12000);
  });

  it("비어 있으면 금액이 없다", () => {
    expect(evaluate("")).toBeNull();
  });

  it("연산자가 있는지로 미리보기 줄을 그릴지 정한다", () => {
    expect(hasOperator("4500")).toBe(false);
    expect(hasOperator("4500+500")).toBe(true);
  });
});
