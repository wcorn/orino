import { describe, expect, it } from "vitest";

import { formatCompactAmount } from "./compactAmount";

/**
 * 예산 카드는 비율을 눈으로 잡는 자리다. 「800,000 중 412,000」처럼 자릿수를 세게 만들면
 * 그 카드는 제 일을 못 한다 — 이 함수가 지키는 것이 그것 하나다.
 */
describe("formatCompactAmount", () => {
  it("만 단위로 줄이고 소수는 한 자리까지만 남긴다", () => {
    expect(formatCompactAmount(412_000)).toBe("41.2만");
    expect(formatCompactAmount(97_000)).toBe("9.7만");
  });

  it("딱 떨어지면 소수를 떼고 「80만」으로 읽는다", () => {
    expect(formatCompactAmount(800_000)).toBe("80만");
    expect(formatCompactAmount(1_000_000)).toBe("100만");
  });

  it("만 미만은 콤마 그대로 둔다 — 「0.4만」은 4,000보다 읽기 어렵다", () => {
    expect(formatCompactAmount(4_500)).toBe("4,500");
    expect(formatCompactAmount(0)).toBe("0");
  });

  it("음수도 그대로 읽힌다 — 초과분이 이 자리에 온다", () => {
    expect(formatCompactAmount(-23_000)).toBe("-2.3만");
  });
});
