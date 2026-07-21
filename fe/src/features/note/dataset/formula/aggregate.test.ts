import { describe, expect, it } from "vitest";

import { aggregate, asCell } from "./index";

/**
 * `aggregate`는 선택 범위 요약(표시 전용)이 쓰는 공개 API다. 새 집계가 아니라 수식 평가와 같은
 * `agg`를 태우므로 규칙(숫자만·에러 전파·빈/텍스트 스킵)이 conformance(BE 교차검증)와 같다.
 * 여기선 그 격리 계약을 못박는다 — 순수 함수라 값·문서를 건드리지 않고 결과만 돌려준다.
 */
describe("aggregate (선택 요약 표시 전용)", () => {
  const cell = (fn: "SUM" | "AVERAGE" | "COUNT" | "MIN" | "MAX", v: string[]) =>
    asCell(aggregate(fn, v));

  it("SUM은 숫자만 더한다", () => {
    expect(cell("SUM", ["10", "20", "30"])).toBe("60");
  });

  it("AVERAGE는 평균(엔진 AVG로 매핑)", () => {
    expect(cell("AVERAGE", ["10", "20", "30"])).toBe("20");
  });

  it("COUNT는 숫자 셀만 센다 — 빈·텍스트 스킵", () => {
    expect(cell("COUNT", ["10", "", "x", "20"])).toBe("2");
  });

  it("MIN·MAX", () => {
    expect(cell("MIN", ["10", "20", "5"])).toBe("5");
    expect(cell("MAX", ["10", "20", "5"])).toBe("20");
  });

  it("에러 셀은 그대로 번진다(수식 집계와 같은 규칙)", () => {
    expect(cell("SUM", ["10", "#VALUE!", "20"])).toBe("#VALUE!");
  });

  it("숫자가 없으면 COUNT는 0", () => {
    expect(cell("COUNT", ["", "x"])).toBe("0");
  });
});
