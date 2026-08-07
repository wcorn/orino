import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { useDeferredCommits } from "./useDeferredCommits";

describe("useDeferredCommits", () => {
  it("보류하면 목록에 뜨고 실행 함수는 아직 돌지 않는다", () => {
    const run = vi.fn();
    const { result } = renderHook(() => useDeferredCommits());

    act(() => result.current.defer(1, run));

    expect(result.current.pendingIds).toEqual([1]);
    expect(run).not.toHaveBeenCalled();
  });

  it("commit하면 실행하고 목록에서 뺀다", () => {
    const run = vi.fn();
    const { result } = renderHook(() => useDeferredCommits());

    act(() => result.current.defer(1, run));
    act(() => result.current.commit(1));

    expect(run).toHaveBeenCalledTimes(1);
    expect(result.current.pendingIds).toEqual([]);
  });

  it("cancel하면 실행하지 않고 버린다 — 요청 자체가 나가지 않는다", () => {
    const run = vi.fn();
    const { result } = renderHook(() => useDeferredCommits());

    act(() => result.current.defer(1, run));
    act(() => result.current.cancel(1));

    expect(run).not.toHaveBeenCalled();
    expect(result.current.pendingIds).toEqual([]);
  });

  it("cancel 후 commit해도 다시 실행되지 않는다", () => {
    const run = vi.fn();
    const { result } = renderHook(() => useDeferredCommits());

    act(() => result.current.defer(1, run));
    act(() => result.current.cancel(1));
    act(() => result.current.commit(1));

    expect(run).not.toHaveBeenCalled();
  });

  it("언마운트하면 보류 중인 것을 전부 즉시 실행한다", () => {
    const first = vi.fn();
    const second = vi.fn();
    const { result, unmount } = renderHook(() => useDeferredCommits());

    act(() => {
      result.current.defer(1, first);
      result.current.defer(2, second);
    });
    unmount();

    // 화면이 사라졌는데 요청이 안 나가면 사용자는 지웠다고 믿는데 서버엔 남는다.
    expect(first).toHaveBeenCalledTimes(1);
    expect(second).toHaveBeenCalledTimes(1);
  });

  it("이미 commit한 것은 언마운트에서 다시 실행되지 않는다", () => {
    const run = vi.fn();
    const { result, unmount } = renderHook(() => useDeferredCommits());

    act(() => result.current.defer(1, run));
    act(() => result.current.commit(1));
    unmount();

    expect(run).toHaveBeenCalledTimes(1);
  });

  it("같은 id를 두 번 보류해도 목록에 한 번만 들어간다", () => {
    const { result } = renderHook(() => useDeferredCommits());

    act(() => {
      result.current.defer(1, vi.fn());
      result.current.defer(1, vi.fn());
    });

    expect(result.current.pendingIds).toEqual([1]);
  });
});
