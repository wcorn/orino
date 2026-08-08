/**
 * base64url 공개키 → `applicationServerKey`가 요구하는 바이트 배열.
 *
 * <p>브라우저는 문자열을 받지 않는다. 서버가 주는 것은 base64url인데 `atob`은 표준 base64만
 * 알아서, 문자를 바꾸고 패딩을 되살려야 한다.
 */
export function toApplicationServerKey(base64url: string): Uint8Array {
  const padded = base64url.padEnd(
    base64url.length + ((4 - (base64url.length % 4)) % 4),
    "=",
  );
  const binary = atob(padded.replace(/-/g, "+").replace(/_/g, "/"));
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

/** 이 브라우저가 웹푸시를 할 수 있는가. iOS Safari 등에서는 없다. */
export function isPushSupported(): boolean {
  return (
    typeof navigator !== "undefined" &&
    "serviceWorker" in navigator &&
    typeof window !== "undefined" &&
    "PushManager" in window &&
    "Notification" in window
  );
}

/**
 * 등록된 Service Worker. 없으면 null.
 *
 * <p><b>`ready`를 쓰지 않는다.</b> 그건 SW가 생길 때까지 <b>영원히 기다린다</b> — dev 서버처럼
 * SW가 아예 없거나 운영에서 등록이 실패하면 화면이 "확인 중"에 멈춘 채로 남는다.
 * `getRegistration()`은 없으면 없다고 곧바로 답한다.
 */
export async function registration(): Promise<ServiceWorkerRegistration | null> {
  if (!isPushSupported()) return null;
  return (await navigator.serviceWorker.getRegistration()) ?? null;
}

/**
 * 지금 이 기기의 구독. 없으면 null.
 *
 * <p>서버가 아니라 <b>브라우저</b>에 물어본다 — 서버 기록이 남아 있어도 사용자가 브라우저
 * 설정에서 권한을 끊었으면 실제 구독은 사라진다.
 */
export async function currentSubscription(): Promise<PushSubscription | null> {
  const active = await registration();
  return active ? active.pushManager.getSubscription() : null;
}
