import { useEffect, useState } from "react";

// Tailwind md 브레이크포인트(768px) 미만 = 모바일(좁은 화면). 레이아웃이 md에서 갈리므로 767 기준.
const NARROW_QUERY = "(max-width: 767px)";

/**
 * 화면 폭이 좁으면(모바일, md 미만) true. 화면 회전·리사이즈에 반응한다.
 * matchMedia 미지원 환경(테스트 jsdom)은 데스크탑으로 간주한다(false).
 */
export function useIsNarrow(): boolean {
  const [narrow, setNarrow] = useState(
    () =>
      typeof window !== "undefined" &&
      typeof window.matchMedia === "function" &&
      window.matchMedia(NARROW_QUERY).matches,
  );
  useEffect(() => {
    if (typeof window.matchMedia !== "function") return;
    const mq = window.matchMedia(NARROW_QUERY);
    const handler = () => setNarrow(mq.matches);
    mq.addEventListener("change", handler);
    return () => mq.removeEventListener("change", handler);
  }, []);
  return narrow;
}
