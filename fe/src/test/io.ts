import { act } from "@testing-library/react";

interface TriggerableObserver {
  trigger: () => void;
}

/**
 * setup.ts의 IntersectionObserver 목이 모아둔 인스턴스들의 교차를 트리거한다.
 * 무한 스크롤 sentinel이 화면에 들어온 상황을 재현해 다음 페이지 fetch를 유발한다.
 */
export function triggerIntersection() {
  const instances =
    (globalThis as unknown as { __ioInstances?: TriggerableObserver[] })
      .__ioInstances ?? [];
  act(() => {
    instances.forEach((io) => io.trigger());
  });
}
