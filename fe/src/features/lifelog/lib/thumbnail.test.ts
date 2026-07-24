import { describe, expect, it } from "vitest";

import { computeThumbSize } from "./thumbnail";

describe("computeThumbSize", () => {
  it("최대 변보다 작으면 원본 크기 유지(확대 안 함)", () => {
    expect(computeThumbSize(300, 200, 480)).toEqual({
      width: 300,
      height: 200,
    });
  });

  it("가로가 길면 가로를 max로 맞춰 비율 유지", () => {
    expect(computeThumbSize(4000, 3000, 480)).toEqual({
      width: 480,
      height: 360,
    });
  });

  it("세로가 길면 세로를 max로 맞춰 비율 유지", () => {
    expect(computeThumbSize(3000, 4000, 480)).toEqual({
      width: 360,
      height: 480,
    });
  });

  it("정사각형은 그대로 축소", () => {
    expect(computeThumbSize(1000, 1000, 480)).toEqual({
      width: 480,
      height: 480,
    });
  });
});
