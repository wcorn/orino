import { type PointerEvent, useCallback, useEffect, useRef } from "react";

/** 설계가 정한 진입 시간. 스크롤하려던 손짓과 구분되는 최소한의 길이다. */
const HOLD_MS = 400;
/** 이만큼 움직이면 누른 게 아니라 스크롤·스와이프다. */
const TOLERANCE_PX = 8;

/**
 * 400ms 롱프레스 감지.
 *
 * <p>드래그 라이브러리의 지연 활성(`delay`)을 쓰지 않고 직접 재는 이유가 있다.
 * dnd-kit은 <b>드래그가 끝난 직후의 클릭 한 번을 삼킨다</b> — 끌어놓은 자리에서 의도치 않은
 * 클릭이 발생하는 걸 막는 정상 동작이다. 그런데 "길게 눌러 모드 진입"은 움직임 없이 끝나는
 * 제스처라, 그것마저 드래그로 처리하면 사용자가 모드에 들어와 처음 누르는 버튼이 먹통이 된다.
 *
 * <p>그래서 진입은 이 훅이 맡고, 실제 드래그는 모드에 들어온 뒤에만 활성화한다.
 */
export function useLongPress(onLongPress: () => void, enabled = true) {
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const origin = useRef<{ x: number; y: number } | null>(null);

  const clear = useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
    origin.current = null;
  }, []);

  useEffect(() => clear, [clear]);

  const onPointerDown = (event: PointerEvent<HTMLElement>) => {
    if (!enabled) return;
    origin.current = { x: event.clientX, y: event.clientY };
    timer.current = setTimeout(() => {
      onLongPress();
      clear();
    }, HOLD_MS);
  };

  const onPointerMove = (event: PointerEvent<HTMLElement>) => {
    if (!origin.current) return;
    const dx = Math.abs(event.clientX - origin.current.x);
    const dy = Math.abs(event.clientY - origin.current.y);
    if (dx > TOLERANCE_PX || dy > TOLERANCE_PX) clear();
  };

  return {
    onPointerDown,
    onPointerMove,
    onPointerUp: clear,
    onPointerCancel: clear,
  };
}
