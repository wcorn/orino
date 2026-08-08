/// <reference lib="webworker" />
import { CacheableResponsePlugin } from "workbox-cacheable-response";
import { ExpirationPlugin } from "workbox-expiration";
import {
  cleanupOutdatedCaches,
  createHandlerBoundToURL,
  precacheAndRoute,
} from "workbox-precaching";
import { NavigationRoute, registerRoute } from "workbox-routing";
import { NetworkFirst } from "workbox-strategies";

import { TRAVEL_CACHE } from "@/shared/lib/cacheNames";

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
 * SPA 경로 폴백.
 *
 * <p>온라인에서는 서버(nginx)가 어떤 경로든 index.html을 준다. 오프라인에는 그 서버가 없어서,
 * <b>SW가 같은 일을 대신하지 않으면 새로고침 순간 흰 화면</b>이 된다 — precache에 index.html이
 * 들어 있어도 `/travel/...` 요청과는 이어지지 않는다.
 *
 * <p>{@code generateSW}는 이걸 자동으로 넣지만 우리는 커스텀 SW라 직접 건다.
 */
registerRoute(
  new NavigationRoute(createHandlerBoundToURL("index.html"), {
    // API는 내비게이션이 아니지만, 혹시라도 셸을 돌려주면 JSON 파싱이 깨진다.
    denylist: [/^\/api\//],
  }),
);

/**
 * 여행 조회 API 캐시(§4.6) — <b>NetworkFirst</b>.
 *
 * <p>온라인이면 항상 최신을 쓰고, 실패했을 때만 캐시로 떨어진다. 반대(CacheFirst)로 하면
 * 현지에서 일정을 고쳐도 옛 화면이 뜬다 — 오프라인 대비가 온라인 사용을 망치면 안 된다.
 *
 * <p><b>GET만</b> 캐시한다. 편집은 오프라인에서 진입 자체를 막으므로(§4.6) 여기 올 일이 없고,
 * 혹시 오더라도 캐시할 대상이 아니다.
 *
 * <p>지도 타일과 장소 검색은 <b>일부러 뺐다</b>. 타일은 양이 많아 캐시해도 쓸모가 없고,
 * 장소 검색은 호출당 과금이라 오프라인에 대비해 미리 받아둘 값이 아니다.
 */
registerRoute(
  ({ url, request, sameOrigin }) =>
    request.method === "GET" &&
    sameOrigin &&
    url.pathname.startsWith("/api/travel/") &&
    // 장소 검색은 오프라인 조회 대상이 아니다(§4.6).
    !url.pathname.startsWith("/api/travel/places"),
  new NetworkFirst({
    // 설정 화면이 같은 이름으로 읽고 비운다(§S-09).
    cacheName: TRAVEL_CACHE,
    // 네트워크가 죽은 게 아니라 느릴 때, 오래 붙잡고 있으면 화면이 멈춘 것처럼 보인다.
    networkTimeoutSeconds: 5,
    plugins: [
      // 200만 캐시한다. 401·404를 캐시하면 로그인 후에도 그 응답이 나온다.
      new CacheableResponsePlugin({ statuses: [200] }),
      new ExpirationPlugin({
        maxEntries: 200,
        // 여행이 끝나면 그 캐시는 의미가 없다. 30일이면 넉넉하다.
        maxAgeSeconds: 30 * 24 * 60 * 60,
        purgeOnQuotaError: true,
      }),
    ],
  }),
);

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
