import { describe, expect, it } from "vitest";

import { formatCellValue } from "./numberFormat";

describe("formatCellValue (표시 전용 숫자 서식)", () => {
  it("KRW — 통화 기호 + 천단위", () => {
    expect(formatCellValue("93000", "KRW")).toBe("₩93,000");
  });

  it("THOUSANDS — 천단위 구분", () => {
    expect(formatCellValue("93000", "THOUSANDS")).toBe("93,000");
  });

  it("DECIMAL1 — 소수 한 자리", () => {
    expect(formatCellValue("9.3", "DECIMAL1")).toBe("9.3");
    expect(formatCellValue("9", "DECIMAL1")).toBe("9.0");
  });

  it("숫자가 아니면 원본 그대로 — 값을 바꾸지 않는다", () => {
    expect(formatCellValue("진에어", "KRW")).toBe("진에어");
    expect(formatCellValue("#VALUE!", "KRW")).toBe("#VALUE!");
  });

  it("빈칸·서식 없음은 원본 그대로", () => {
    expect(formatCellValue("", "KRW")).toBe("");
    expect(formatCellValue("93000")).toBe("93000");
  });
});
