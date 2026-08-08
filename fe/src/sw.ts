/// <reference lib="webworker" />
import { cleanupOutdatedCaches, precacheAndRoute } from "workbox-precaching";

declare const self: ServiceWorkerGlobalScope;

/**
 * 앱 셸 precache.
 *
 * <p>빌드 시 파일 목록이 여기 주입된다. 파일명에 해시가 붙으므로 배포하면 목록이 바뀌고,
 * 새 SW가 설치된다.
 */
precacheAndRoute(self.__WB_MANIFEST);

// 옛 배포의 캐시를 남겨두면 용량만 먹는다. 새 SW가 활성화될 때 정리한다.
cleanupOutdatedCaches();

/**
 * 대기 중인 새 SW를 즉시 활성화하라는 신호. 앱이 "새 버전이 있어요"를 띄우고
 * 사용자가 받아들였을 때만 보낸다 — 보고 있는 화면을 말없이 갈아치우지 않는다.
 */
self.addEventListener("message", (event) => {
  if ((event.data as { type?: string } | undefined)?.type === "SKIP_WAITING") {
    void self.skipWaiting();
  }
});

/** 알림에 실어 보내는 값. 서버가 발송 시점에 조립한다. */
interface PushPayload {
  title: string;
  body?: string;
  /** 탭했을 때 열 앱 내 경로. */
  url?: string;
  /** 같은 일정의 알림이 쌓이지 않게 묶는 키. */
  tag?: string;
}

function parsePayload(event: PushEvent): PushPayload {
  try {
    const parsed = event.data?.json() as PushPayload | undefined;
    if (parsed?.title) return parsed;
  } catch {
    // 형태가 어긋나도 알림 자체는 띄운다 — 아래 기본값으로 떨어진다.
  }
  return { title: "Orino", body: event.data?.text() ?? undefined };
}

/**
 * 푸시 수신. <b>반드시 알림을 하나 띄워야 한다</b> — 받고도 안 띄우면 브라우저가
 * "조용한 푸시"로 보고 권한을 회수할 수 있다.
 */
self.addEventListener("push", (event) => {
  const payload = parsePayload(event);
  event.waitUntil(
    self.registration.showNotification(payload.title, {
      body: payload.body,
      tag: payload.tag,
      // 알림에서 경로를 꺼내야 탭했을 때 그 화면으로 갈 수 있다.
      data: { url: payload.url ?? "/travel" },
    }),
  );
});

/**
 * 알림 탭 → 딥링크.
 *
 * <p>이미 열려 있는 창이 있으면 <b>그 창을 옮긴다</b>. 새 창을 열면 탭이 계속 쌓이고,
 * 사용자는 어느 것이 진짜인지 모르게 된다.
 */
self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const url =
    (event.notification.data as { url?: string } | undefined)?.url ?? "/travel";

  event.waitUntil(
    (async () => {
      const clients = await self.clients.matchAll({
        type: "window",
        includeUncontrolled: true,
      });
      for (const client of clients) {
        if ("focus" in client) {
          await client.focus();
          if ("navigate" in client) {
            await client.navigate(url);
          }
          return;
        }
      }
      await self.clients.openWindow(url);
    })(),
  );
});
