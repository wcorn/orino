import { useEffect, useRef } from "react";

/**
 * 리스트 하단 sentinel이 보이면 {@link onIntersect}를 호출한다. 반환한 ref를 sentinel 요소에 붙인다.
 * {@link enabled}가 false면(다음 페이지 없음·로딩 중) 관찰하지 않는다.
 */
export function useInfiniteScroll(onIntersect: () => void, enabled: boolean) {
  const ref = useRef<HTMLDivElement | null>(null);
  const callbackRef = useRef(onIntersect);
  callbackRef.current = onIntersect;

  useEffect(() => {
    const el = ref.current;
    if (!el || !enabled) return;

    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        callbackRef.current();
      }
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, [enabled]);

  return ref;
}
