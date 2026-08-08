import { registerSW } from "virtual:pwa-register";

/**
 * Service Worker 등록.
 *
 * <p><b>여행만이 아니라 앱 전체가 SW 아래로 들어간다.</b> 캐시가 잘못되면 배포해도 옛 JS가
 * 계속 떠서, 사용자는 이미 고친 버그를 계속 본다. 그래서 새 버전을 <b>말없이 적용하지 않고</b>
 * 안내한 뒤 사용자가 새로고침하게 한다.
 *
 * @param onNeedRefresh 새 버전이 대기 중일 때 호출. 인자를 실행하면 적용하고 새로고침한다
 */
export function registerServiceWorker(
  onNeedRefresh: (applyUpdate: () => void) => void,
): void {
  const update = registerSW({
    immediate: true,
    onNeedRefresh() {
      onNeedRefresh(() => void update(true));
    },
  });
}
