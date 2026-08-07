import { type PointerEvent, useRef, useState } from "react";

/** 이 거리를 넘겨야 동작으로 친다. 짧은 흔들림을 삭제로 오인하지 않을 만큼은 되어야 한다. */
const TRIGGER_PX = 72;
/** 가로/세로 판정을 시작하는 최소 이동량. 이 전에는 방향을 정하지 않는다. */
const DIRECTION_PX = 10;

interface Options {
  disabled?: boolean;
  onSwipeLeft?: () => void;
  onSwipeRight?: () => void;
}

/**
 * 행 가로 스와이프 — 좌는 삭제, 우는 보관함.
 *
 * <p>세로 스크롤과 싸우지 않는 것이 핵심이다. 처음 10px에서 <b>가로가 세로보다 클 때만</b>
 * 스와이프로 확정하고, 그 전까지는 브라우저 스크롤을 그대로 둔다. 반대 방향 동작이 없으면
 * (예: 보관함에서 우 스와이프) 아예 따라가지 않는다.
 *
 * <p>포인터 이벤트만 쓴다 — 마우스·터치·펜이 같은 코드로 동작하고, 드래그 라이브러리의
 * 세로 드래그와도 손쉽게 나눌 수 있다.
 */
export function useSwipeAction({
  disabled = false,
  onSwipeLeft,
  onSwipeRight,
}: Options) {
  const start = useRef<{ x: number; y: number } | null>(null);
  const axis = useRef<"none" | "x" | "y">("none");
  const [offset, setOffset] = useState(0);
  const [dragging, setDragging] = useState(false);

  const reset = () => {
    start.current = null;
    axis.current = "none";
    setOffset(0);
    setDragging(false);
  };

  const onPointerDown = (event: PointerEvent<HTMLElement>) => {
    if (disabled || event.pointerType === "mouse") return;
    start.current = { x: event.clientX, y: event.clientY };
    axis.current = "none";
  };

  const onPointerMove = (event: PointerEvent<HTMLElement>) => {
    if (!start.current) return;
    const dx = event.clientX - start.current.x;
    const dy = event.clientY - start.current.y;

    if (axis.current === "none") {
      if (Math.abs(dx) < DIRECTION_PX && Math.abs(dy) < DIRECTION_PX) return;
      // 세로가 더 크면 스크롤이다 — 이 제스처는 포기한다.
      axis.current = Math.abs(dx) > Math.abs(dy) ? "x" : "y";
      if (axis.current === "y") {
        reset();
        return;
      }
      setDragging(true);
    }

    // 동작이 없는 방향으로는 따라가지 않는다(끌리기만 하고 아무 일도 없으면 혼란스럽다).
    if (dx < 0 && !onSwipeLeft) return;
    if (dx > 0 && !onSwipeRight) return;
    setOffset(dx);
  };

  const onPointerUp = () => {
    if (axis.current === "x") {
      if (offset <= -TRIGGER_PX) onSwipeLeft?.();
      else if (offset >= TRIGGER_PX) onSwipeRight?.();
    }
    reset();
  };

  return { offset, dragging, onPointerDown, onPointerMove, onPointerUp };
}
