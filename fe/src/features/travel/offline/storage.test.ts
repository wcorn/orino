import { describe, expect, it } from "vitest";

import { formatBytes } from "./storage";

describe("용량 표기", () => {
  it("단위를 올려가며 읽기 쉽게 만든다", () => {
    expect(formatBytes(512)).toBe("512 B");
    expect(formatBytes(2048)).toBe("2.0 KB");
    expect(formatBytes(5 * 1024 * 1024)).toBe("5.0 MB");
    expect(formatBytes(3 * 1024 * 1024 * 1024)).toBe("3.0 GB");
  });

  it("바이트에는 소수를 붙이지 않는다 — 의미가 없다", () => {
    expect(formatBytes(1)).toBe("1 B");
    expect(formatBytes(1023)).toBe("1023 B");
  });

  it("세 자리가 되면 소수를 떨군다 — 정밀도가 목적이 아니다", () => {
    expect(formatBytes(150 * 1024)).toBe("150 KB");
  });

  it("0은 0이다", () => {
    expect(formatBytes(0)).toBe("0 B");
  });
});
