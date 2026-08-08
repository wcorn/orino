import { describe, expect, it } from "vitest";

import { fitWithin, MAX_FILE_BYTES } from "./processPhoto";

describe("사진 축소 크기", () => {
  it("긴 변을 상한에 맞추고 비율을 지킨다", () => {
    expect(fitWithin(4032, 3024, 2560)).toEqual({ width: 2560, height: 1920 });
    // 세로 사진도 같은 규칙이다.
    expect(fitWithin(3024, 4032, 480)).toEqual({ width: 360, height: 480 });
  });

  it("작은 사진은 늘리지 않는다 — 없는 화질을 만들어내지 않는다", () => {
    expect(fitWithin(800, 600, 2560)).toEqual({ width: 800, height: 600 });
  });

  it("정사각형도 상한을 넘으면 줄인다", () => {
    expect(fitWithin(3000, 3000, 480)).toEqual({ width: 480, height: 480 });
  });

  it("장당 상한은 15MB다", () => {
    expect(MAX_FILE_BYTES).toBe(15 * 1024 * 1024);
  });
});
