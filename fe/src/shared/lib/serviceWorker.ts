import { registerSW } from "virtual:pwa-register";

import { createUpdateWatcher } from "./swUpdate";

export interface RegisterServiceWorkerOptions {
  /** 새 버전을 발견했을 때 호출. 인자를 실행하면 적용하고 새로고침한다. */
  onUpdateFound: (applyUpdate: () => void) => void;
  /** 적용을 눌렀지만 아직 내려받는 중일 때 호출. 잠깐 걸린다는 걸 알린다. */
  onApplying?: () => void;
}

/**
 * Service Worker 등록.
 *
 * <p><b>여행만이 아니라 앱 전체가 SW 아래로 들어간다.</b> 캐시가 잘못되면 배포해도 옛 JS가
 * 계속 떠서, 사용자는 이미 고친 버그를 계속 본다. 그래서 새 버전을 <b>말없이 적용하지 않고</b>
 * 안내한 뒤 사용자가 새로고침하게 한다.
 *
 * <p>안내는 <b>install이 끝나기 전</b>에 뜬다({@link ./swUpdate}). 플러그인의
 * {@code onNeedRefresh}만 쓰면 precache를 다 받은 뒤라 몇 초가 비고, 그 사이 사용자는
 * 옛 화면을 보며 하드 리로드를 하게 된다.
 */
export function registerServiceWorker(
  options: RegisterServiceWorkerOptions,
): void {
  const watcher = createUpdateWatcher({
    isControlled: () => navigator.serviceWorker.controller != null,
    applyUpdate: () => {
      // 새 SW가 페이지를 넘겨받는 순간 새로고침한다. 플러그인도 같은 일을 하지만
      // 그건 workbox의 waiting 이벤트를 본 방문에서만이고, 브라우저가 우리보다 먼저
      // 업데이트를 찾아낸 방문에는 그 이벤트가 없다 — 그러면 적용만 되고 화면은 옛것으로 남는다.
      navigator.serviceWorker.addEventListener(
        "controllerchange",
        () => window.location.reload(),
        { once: true },
      );
      void update(true);
    },
    ...options,
  });

  const update = registerSW({
    immediate: true,
    onRegisteredSW(_swUrl, registration) {
      if (registration) watcher.watch(registration);
    },
    // 아래 이른 감시가 updatefound를 놓쳤을 때의 뒷받침. 설치가 끝난 뒤에 불린다.
    onNeedRefresh() {
      watcher.notifyReady();
    },
  });

  // onRegisteredSW는 register()가 끝난 뒤에 온다. updatefound는 그보다 먼저 일어날 수 있어서,
  // 이미 등록돼 있으면(= 업데이트가 일어나는 바로 그 경우) 등록 객체를 미리 잡아 붙는다.
  void navigator.serviceWorker?.getRegistration().then((registration) => {
    if (registration) watcher.watch(registration);
  });
}
