import { expect, test as base } from "@playwright/test";

/**
 * E2E의 기본 테스트. **스펙은 `@playwright/test`가 아니라 여기서 가져온다.**
 *
 * 막지 않은 `/api` 요청을 그물로 받아 500으로 끝낸다. 이게 없으면 그 요청은 vite dev
 * 서버의 프록시를 타고 `localhost:8080`으로 나가는데, CI에는 그 자리에 BE가 없어 프록시가
 * `ECONNREFUSED`를 로그로 쏟는다 — 통과한 실행에서도 수십 줄이었다.
 *
 * 응답을 **vite가 그 상황에서 주던 것과 똑같이** 맞춘다(`500 text/plain`, 빈 본문).
 * 앱이 보는 것이 달라지면 안 되기 때문이다. 처음엔 연결 거부(`abort`)로 막았는데, 그건
 * 응답이 아예 없는 것이라 앱이 부팅에서 멈췄다 — 133개 중 116개가 깨졌다.
 * 달라지는 것은 **실패가 어디서 결정되는가**뿐이다. 전에는 진짜 소켓을 열어 봐야 알았고,
 * 이제는 브라우저에서 끝난다.
 *
 * 글로브가 아니라 **경로 술어**로 잡는다. `{@code **&#47;api&#47;**}`는 dev에서 앱 소스까지
 * 삼킨다 — vite가 모듈을 URL로 서빙하는데 `/src/shared/api/client.ts`에도 그 경로가 들어 있어
 * 앱 번들이 통째로 가로채인다(`auth.spec.ts`가 같은 이유로 술어를 쓴다).
 *
 * 스펙이 나중에 거는 라우트가 항상 이긴다. Playwright는 나중에 등록된 라우트를 먼저 맞춰
 * 보고, 이 그물은 page 픽스처를 만들 때(= 스펙의 훅보다 먼저) 걸리기 때문이다.
 *
 * **네트워크 자체를 시험하는 스펙은 `test.use({ stubUnmockedApi: false })`로 끈다.**
 * 그물은 오프라인에서도 응답을 만들어 주는데, 그러면 「망이 끊긴 것」과 「서버가 거절한 것」이
 * 구분되지 않는다. 앱은 그 둘을 다르게 다루므로(끊김이면 세션 유지, 거절이면 로그아웃)
 * 그 구분을 확인하는 스펙에는 그물을 씌우지 않는다.
 */
export const test = base.extend<{ stubUnmockedApi: boolean }>({
  stubUnmockedApi: [true, { option: true }],

  page: async ({ page, stubUnmockedApi }, run) => {
    if (stubUnmockedApi) {
      await page.route(
        (url) => url.pathname.startsWith("/api/"),
        (route) =>
          route.fulfill({ status: 500, contentType: "text/plain", body: "" }),
      );
    }
    await run(page);
  },
});

export { expect };
export type { Page } from "@playwright/test";
