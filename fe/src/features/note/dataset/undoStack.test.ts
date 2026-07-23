import { act, renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { useUndoStack } from "./undoStack";

describe("useUndoStack", () => {
  it("push한 역연산을 LIFO로 undo하고, redo로 되돌린다", async () => {
    const { result } = renderHook(() => useUndoStack());
    const log: string[] = [];

    act(() => {
      result.current.push({
        undo: () => void log.push("undo-A"),
        redo: () => void log.push("redo-A"),
      });
      result.current.push({
        undo: () => void log.push("undo-B"),
        redo: () => void log.push("redo-B"),
      });
    });
    expect(result.current.canUndo).toBe(true);
    expect(result.current.canRedo).toBe(false);

    await act(async () => {
      await result.current.undo();
    });
    expect(log).toEqual(["undo-B"]); // 가장 최근(B)부터
    expect(result.current.canRedo).toBe(true);

    await act(async () => {
      await result.current.redo();
    });
    expect(log).toEqual(["undo-B", "redo-B"]);
    expect(result.current.canRedo).toBe(false);
  });

  it("빈 스택에서 undo/redo는 아무 일도 안 하고 false를 준다", async () => {
    const { result } = renderHook(() => useUndoStack());
    let ok = true;
    await act(async () => {
      ok = (await result.current.undo()) as boolean;
    });
    expect(ok).toBe(false);
    expect(result.current.canUndo).toBe(false);
  });

  it("새 push는 redo 스택을 버린다(분기된 미래 무효)", async () => {
    const { result } = renderHook(() => useUndoStack());
    act(() => {
      result.current.push({ undo: () => {}, redo: () => {} });
    });
    await act(async () => {
      await result.current.undo();
    });
    expect(result.current.canRedo).toBe(true);
    act(() => {
      result.current.push({ undo: () => {}, redo: () => {} });
    });
    expect(result.current.canRedo).toBe(false);
  });

  it("비동기 undo가 진행 중이면 재진입을 막는다", async () => {
    const { result } = renderHook(() => useUndoStack());
    let release: () => void = () => {};
    const gate = new Promise<void>((r) => (release = r));
    const undo = vi.fn(() => gate);

    act(() => {
      result.current.push({ undo, redo: () => {} });
      result.current.push({ undo, redo: () => {} });
    });

    let first: Promise<boolean>;
    let second: boolean | undefined;
    await act(async () => {
      first = result.current.undo() as Promise<boolean>;
      second = (await result.current.undo()) as boolean; // 진행 중 재진입
    });
    expect(second).toBe(false); // 두 번째는 거절
    await act(async () => {
      release();
      await first;
    });
    expect(undo).toHaveBeenCalledTimes(1);
  });
});
