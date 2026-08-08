import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { toast, useToastStore } from "./toast";

describe("스낵바", () => {
  beforeEach(() => {
    useToastStore.setState({ toasts: [] });
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("시간이 지나면 스스로 닫힌다", () => {
    toast("저장했어요.");
    expect(useToastStore.getState().toasts).toHaveLength(1);

    vi.advanceTimersByTime(60_000);

    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  it("durationMs가 Infinity면 닫히지 않는다", () => {
    // setTimeout(fn, Infinity)는 0으로 강제돼 오히려 즉시 사라진다 —
    // 타이머를 아예 걸지 않아야 계속 떠 있는다(새 버전 안내가 이 경우다).
    useToastStore.getState().show("새 버전이 있어요.", {
      action: { label: "새로고침", onAction: () => {} },
      durationMs: Number.POSITIVE_INFINITY,
    });

    vi.advanceTimersByTime(10 * 60_000);

    expect(useToastStore.getState().toasts).toHaveLength(1);
  });

  it("닫히지 않는 스낵바도 액션을 누르면 사라진다", () => {
    const onAction = vi.fn();
    const id = useToastStore.getState().show("새 버전이 있어요.", {
      action: { label: "새로고침", onAction },
      durationMs: Number.POSITIVE_INFINITY,
    });

    useToastStore.getState().runAction(id);

    expect(onAction).toHaveBeenCalledOnce();
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });
});
