import { beforeEach, describe, expect, it, vi } from "vitest";

import { reloadOnChunkError } from "./chunkReload";

describe("reloadOnChunkError", () => {
  beforeEach(() => sessionStorage.clear());

  it("첫 청크 실패엔 새로고침한다", () => {
    const reload = vi.fn();
    expect(reloadOnChunkError(1000, reload)).toBe(true);
    expect(reload).toHaveBeenCalledOnce();
  });

  it("쿨다운(10s) 내 재실패는 루프 방지로 스킵한다", () => {
    const reload = vi.fn();
    reloadOnChunkError(1000, reload);
    expect(reloadOnChunkError(5000, reload)).toBe(false);
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it("쿨다운 경과 후엔 다시 새로고침한다(다음 배포 대응)", () => {
    const reload = vi.fn();
    reloadOnChunkError(1000, reload);
    expect(reloadOnChunkError(20000, reload)).toBe(true);
    expect(reload).toHaveBeenCalledTimes(2);
  });
});
