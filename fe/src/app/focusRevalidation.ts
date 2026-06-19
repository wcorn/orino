import { focusManager } from "@tanstack/react-query";

/**
 * 앱 복귀(재포커스) 시 활성 쿼리를 재검증하기 위한 focus 신호 구독.
 *
 * React Query v5 기본 focusManager는 `visibilitychange`만 구독한다. 다른 탭에서 보다 돌아온
 * 경우는 그걸로 잡히지만, 다른 창/앱에서 (탭이 계속 보이는 채로) 돌아온 경우는 window `focus`가
 * 필요하다. 두 신호를 모두 구독해 "화면이 방금 다시 보이게 됨"을 빠짐없이 잡는다.
 *
 * 재검증 여부는 각 쿼리의 staleTime(=연사 dedupe 창)이 결정하므로, 여기서는 신호만 전달한다.
 */
export function installFocusRevalidation(): void {
  focusManager.setEventListener((handleFocus) => {
    if (typeof window === "undefined" || !window.addEventListener) {
      return () => {};
    }
    const onFocus = () => handleFocus(document.visibilityState === "visible");
    window.addEventListener("visibilitychange", onFocus, false);
    window.addEventListener("focus", onFocus, false);
    return () => {
      window.removeEventListener("visibilitychange", onFocus);
      window.removeEventListener("focus", onFocus);
    };
  });
}
