/**
 * Service Worker 업데이트 감시.
 *
 * <p><b>왜 따로 있나</b> — 알림 시점을 <b>install이 끝나기 전으로</b> 당기기 위해서다.
 * {@code virtual:pwa-register}가 주는 {@code onNeedRefresh}는 workbox의 {@code waiting}
 * 이벤트, 즉 새 SW가 precache 전체(모든 JS 청크·CSS·폰트·이미지)를 다 내려받은 뒤에 불린다.
 * 배포가 클수록 오래 걸리고, 그동안 화면은 옛 버전이라 사용자는 안내를 못 본 채 하드 리로드를 한다.
 *
 * <p>새 버전이 있다는 <b>사실</b>은 그보다 훨씬 이른 {@code updatefound}(= {@code /sw.js}를 받아
 * 바뀐 걸 안 순간)에 알 수 있다. 그래서 감시는 등록 객체에 직접 붙고, 알림은 그 시점에 한다.
 *
 * <p>{@code virtual:pwa-register}는 빌드 타임 가상 모듈이라 테스트에서 불러올 수 없다.
 * 이 파일은 그 의존을 빼서 순수 로직만 담는다 — 배선은 {@link ./serviceWorker}가 한다.
 */

export interface UpdateWatcherOptions {
  /**
   * 이 페이지를 제어 중인 SW가 있는가. 없으면 첫 설치이므로 "새 버전"이 아니다
   * (알리면 처음 방문한 사람에게 다짜고짜 새로고침을 권하게 된다).
   */
  isControlled: () => boolean;
  /** 대기 중인 새 SW에 SKIP_WAITING을 보내고 새로고침시킨다(`registerSW`가 돌려준 `updateSW(true)`). */
  applyUpdate: () => void;
  /** 새 버전을 발견했을 때. 인자를 실행하면 적용한다. */
  onUpdateFound: (applyUpdate: () => void) => void;
  /** 적용을 눌렀지만 아직 내려받는 중이라 곧바로 새로고침하지 못할 때. */
  onApplying?: () => void;
  /** 내려받기가 끝나기 전에 적용을 눌렀는데 그 설치가 깨졌을 때의 최후 수단. */
  reload?: () => void;
}

export interface UpdateWatcher {
  /** 등록 객체를 감시한다. 같은 객체로 여러 번 불러도 한 번만 붙는다. */
  watch: (registration: ServiceWorkerRegistration) => void;
  /**
   * 감시가 {@code updatefound}를 놓쳤을 때를 위한 직접 알림.
   * 플러그인의 {@code onNeedRefresh}(= 이미 설치가 끝난 상태)를 여기에 잇는다.
   */
  notifyReady: () => void;
}

export function createUpdateWatcher({
  isControlled,
  applyUpdate,
  onUpdateFound,
  onApplying,
  reload = () => window.location.reload(),
}: UpdateWatcherOptions): UpdateWatcher {
  const watched = new WeakSet<ServiceWorkerRegistration>();
  // 경로가 여럿이라(이른 감시 · 플러그인 콜백 · onNeedRefresh) 같은 배포를 두 번 알릴 수 있다.
  let notified = false;

  function notify(installing: ServiceWorker | null): void {
    if (notified) return;
    notified = true;
    onUpdateFound(() => apply(installing));
  }

  function apply(installing: ServiceWorker | null): void {
    // 이미 대기 중이면 곧바로 적용된다.
    if (!installing || installing.state !== "installing") {
      applyUpdate();
      return;
    }

    // 아직 내려받는 중이다. 지금 보내면 <b>조용히 사라진다</b> —
    // workbox의 messageSkipWaiting()은 registration.waiting이 있을 때만 메시지를 보내서,
    // 이 시점에 부르면 아무 일도 일어나지 않고 사용자는 버튼이 먹통이라고 느낀다.
    onApplying?.();
    installing.addEventListener("statechange", () => {
      if (installing.state === "installed") applyUpdate();
      // 설치가 깨지면 적용할 대상이 없다. 사용자는 "새로고침"을 눌렀으니 새로고침은 해 준다
      // (옛 SW가 그대로 제어하므로 화면은 그대로지만, 눌러도 아무 일 없는 것보다는 낫다).
      else if (installing.state === "redundant") reload();
    });
  }

  return {
    watch(registration) {
      if (watched.has(registration)) return;
      watched.add(registration);

      // <b>이벤트를 기다리기 전에 지금 상태부터 본다.</b> 제어 중인 페이지로 이동하면
      // 브라우저가 우리 코드보다 먼저 sw.js를 확인하므로, 앱이 뜰 때는 updatefound가
      // 이미 지나가 있을 수 있다. 그때 이벤트만 기다리면 이 방문에서는 아무것도 알리지 못한다
      // (workbox도 register() 시점에 waiting만 보고 installing은 보지 않아 같은 구멍이 있다).
      if (isControlled()) {
        if (registration.waiting) notify(null);
        else if (registration.installing) notify(registration.installing);
      }

      registration.addEventListener("updatefound", () => {
        const installing = registration.installing;
        // 첫 설치는 교체가 아니다.
        if (!installing || !isControlled()) return;
        notify(installing);
      });
    },

    notifyReady() {
      notify(null);
    },
  };
}
