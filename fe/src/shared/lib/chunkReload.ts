const KEY = "vite:preloadError:lastReload";
const COOLDOWN_MS = 10_000;

/**
 * 청크(dynamic import) 로드 실패 시 1회 새로고침으로 흡수한다.
 * 대개 새 배포로 옛 해시 청크가 교체·삭제된 stale 탭이 원인 — 새로고침하면 새 index+청크를 받는다.
 * 단, 쿨다운(10s) 내 재실패면 진짜 404(깨진 배포)로 보고 무한 리로드를 막기 위해 스킵한다
 * (이 경우 ErrorBoundary가 에러 화면을 보여준다). 테스트를 위해 now/reload를 주입할 수 있다.
 */
export function reloadOnChunkError(
  now: number = Date.now(),
  reload: () => void = () => window.location.reload(),
): boolean {
  let last = 0;
  try {
    last = Number(sessionStorage.getItem(KEY) ?? 0);
  } catch {
    // sessionStorage 접근 불가(프라이빗 모드 등)는 무시하고 리로드를 시도한다
  }
  // last>0(실제 리로드 이력)일 때만 쿨다운 적용 — 첫 실패는 항상 새로고침한다.
  if (last > 0 && now - last < COOLDOWN_MS) return false;
  try {
    sessionStorage.setItem(KEY, String(now));
  } catch {
    // 저장 실패는 무시 — 리로드 자체는 진행한다
  }
  reload();
  return true;
}

/** Vite가 dynamic import 청크 로드에 실패하면 발생시키는 vite:preloadError를 잡아 새로고침한다. */
export function installChunkReloadHandler(): void {
  window.addEventListener("vite:preloadError", () => {
    reloadOnChunkError();
  });
}
