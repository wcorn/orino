import { act, renderHook } from "@testing-library/react";
import type { PointerEvent } from "react";
import { describe, expect, it, vi } from "vitest";

import { useSwipeAction } from "./useSwipeAction";

/** 포인터 이벤트 최소 형태. 훅이 보는 값만 담는다. */
function pointer(x: number, y: number, pointerType = "touch") {
  return { clientX: x, clientY: y, pointerType } as PointerEvent<HTMLElement>;
}

function swipe(
  result: { current: ReturnType<typeof useSwipeAction> },
  path: [number, number][],
) {
  act(() => result.current.onPointerDown(pointer(...path[0])));
  for (const [x, y] of path.slice(1)) {
    act(() => result.current.onPointerMove(pointer(x, y)));
  }
  act(() => result.current.onPointerUp());
}

describe("useSwipeAction", () => {
  it("왼쪽으로 충분히 밀면 삭제가 실행된다", () => {
    const onSwipeLeft = vi.fn();
    const { result } = renderHook(() =>
      useSwipeAction({ onSwipeLeft, onSwipeRight: vi.fn() }),
    );

    swipe(result, [
      [200, 100],
      [180, 100],
      [110, 102],
    ]);

    expect(onSwipeLeft).toHaveBeenCalledTimes(1);
  });

  it("오른쪽으로 충분히 밀면 보관함이 실행된다", () => {
    const onSwipeRight = vi.fn();
    const { result } = renderHook(() =>
      useSwipeAction({ onSwipeLeft: vi.fn(), onSwipeRight }),
    );

    swipe(result, [
      [100, 100],
      [120, 100],
      [190, 98],
    ]);

    expect(onSwipeRight).toHaveBeenCalledTimes(1);
  });

  it("조금만 밀면 아무 일도 없고 제자리로 돌아온다", () => {
    const onSwipeLeft = vi.fn();
    const { result } = renderHook(() => useSwipeAction({ onSwipeLeft }));

    swipe(result, [
      [200, 100],
      [175, 100],
    ]);

    expect(onSwipeLeft).not.toHaveBeenCalled();
    expect(result.current.offset).toBe(0);
  });

  it("세로로 움직이면 스크롤로 보고 손을 뗀다", () => {
    const onSwipeLeft = vi.fn();
    const { result } = renderHook(() => useSwipeAction({ onSwipeLeft }));

    // 세로가 먼저 커지면 이 제스처는 스크롤이다.
    swipe(result, [
      [200, 100],
      [196, 130],
      [120, 200],
    ]);

    expect(onSwipeLeft).not.toHaveBeenCalled();
    expect(result.current.offset).toBe(0);
  });

  it("동작이 없는 방향으로는 따라가지 않는다", () => {
    // 보관함에서는 오른쪽(보관함으로)이 없다.
    const { result } = renderHook(() =>
      useSwipeAction({ onSwipeLeft: vi.fn() }),
    );

    act(() => result.current.onPointerDown(pointer(100, 100)));
    act(() => result.current.onPointerMove(pointer(160, 100)));

    expect(result.current.offset).toBe(0);
  });

  it("마우스는 스와이프하지 않는다(버튼이 같은 동작을 한다)", () => {
    const onSwipeLeft = vi.fn();
    const { result } = renderHook(() => useSwipeAction({ onSwipeLeft }));

    act(() => result.current.onPointerDown(pointer(200, 100, "mouse")));
    act(() => result.current.onPointerMove(pointer(100, 100, "mouse")));
    act(() => result.current.onPointerUp());

    expect(onSwipeLeft).not.toHaveBeenCalled();
  });

  it("드래그 모드에서는 스와이프를 끈다(세로 드래그가 주인이다)", () => {
    const onSwipeLeft = vi.fn();
    const { result } = renderHook(() =>
      useSwipeAction({ disabled: true, onSwipeLeft }),
    );

    swipe(result, [
      [200, 100],
      [110, 100],
    ]);

    expect(onSwipeLeft).not.toHaveBeenCalled();
  });
});
