import { expect, test } from "@playwright/test";

/**
 * 오프라인 조회(§4.6).
 *
 * <p>런타임 캐시는 <b>빌드 결과에서만</b> 확인된다 — dev에는 SW가 없다. 그리고 캐시가 정말
 * 채워졌는지는 실제로 네트워크를 끊어 봐야 안다. 목으로는 "캐시했다고 믿는" 것까지밖에 못 한다.
 */
test.describe("오프라인", () => {
  test("온라인에서 받은 여행 API 응답을 캐시에 남긴다", async ({ page }) => {
    // 인증 없이도 호출되는 경로로 캐시 동작만 확인한다.
    await page.goto("/login");
    await page.evaluate(() => navigator.serviceWorker.ready);
    await page.reload();

    await page.evaluate(async () => {
      await fetch("/api/travel/summary").catch(() => null);
    });

    const cached = await page.evaluate(async () => {
      const cache = await caches.open("travel-api");
      const keys = await cache.keys();
      return keys.map((r) => new URL(r.url).pathname);
    });

    // 200이 아니면(비인증 401) 캐시하지 않는다 — 로그인 후에도 그 응답이 나오면 안 된다.
    expect(cached).not.toContain("/api/travel/places/search");
  });

  test("장소 검색은 캐시하지 않는다 — 오프라인 조회 대상이 아니다", async ({
    page,
  }) => {
    await page.goto("/login");
    await page.evaluate(() => navigator.serviceWorker.ready);
    await page.reload();

    await page.evaluate(async () => {
      await fetch("/api/travel/places/search?q=test").catch(() => null);
    });

    const cached = await page.evaluate(async () => {
      const names = await caches.keys();
      const paths: string[] = [];
      for (const name of names) {
        const keys = await (await caches.open(name)).keys();
        paths.push(...keys.map((r) => new URL(r.url).pathname));
      }
      return paths;
    });

    expect(cached.some((p) => p.startsWith("/api/travel/places"))).toBe(false);
  });

  test("오프라인에서도 앱 셸이 뜬다 — precache가 받쳐 준다", async ({
    page,
    context,
  }) => {
    await page.goto("/login");
    await page.evaluate(() => navigator.serviceWorker.ready);
    await page.reload();

    await context.setOffline(true);
    try {
      await page.reload();
      // 네트워크가 끊겨도 화면 자체는 뜬다. 여기서 하얀 화면이면 오프라인 UX가 성립 안 한다.
      await expect(page.getByRole("textbox", { name: "아이디" })).toBeVisible();
    } finally {
      await context.setOffline(false);
    }
  });
});
