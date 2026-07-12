import { describe, expect, it } from "vitest";

import { shouldUseDataset } from "./datasetImport";

describe("shouldUseDataset", () => {
  it("소형 표는 native(false)", () => {
    expect(
      shouldUseDataset(
        {
          headers: ["a", "b"],
          rows: [
            ["1", "2"],
            ["3", "4"],
          ],
        },
        30,
        200,
      ),
    ).toBe(false);
  });

  it("열 상한 초과 → dataset(true)", () => {
    const wide = Array.from({ length: 31 }, (_, i) => `c${i}`);
    expect(shouldUseDataset({ headers: wide, rows: [wide] }, 30, 200)).toBe(
      true,
    );
  });

  it("행 상한 초과 → dataset(true)", () => {
    const rows = Array.from({ length: 201 }, () => ["x"]);
    expect(shouldUseDataset({ headers: null, rows }, 30, 200)).toBe(true);
  });

  it("셀 임계치(2000) 초과 → dataset(true)", () => {
    // 100행 × 21열 = 2100셀 (열·행 상한은 안 넘지만 셀 임계 초과)
    const rows = Array.from({ length: 100 }, () =>
      Array.from({ length: 21 }, () => "x"),
    );
    expect(shouldUseDataset({ headers: null, rows }, 30, 200)).toBe(true);
  });
});
