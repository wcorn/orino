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

  /**
   * #1095 — 여기까지 와야 오프라인 캐시가 쓸모 있다.
   *
   * <p>위 테스트는 <b>로그인 화면</b>에서만 새로고침한다. 로그인한 상태로 새로고침하면
   * 토큰이 사라지고 재발급도 못 해, 예전에는 로그인 화면으로 쫓겨났다 — 캐시에 일정이
   * 다 있어도 볼 수 없었다.
   */
  test("로그인한 채 오프라인에서 새로고침해도 로그인 화면으로 쫓겨나지 않는다", async ({
    page,
    context,
  }) => {
    await page.route("**/api/auth/login", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ code: "OK", data: { accessToken: "t" } }),
      }),
    );
    // 조회는 비어 있어도 된다 — 여기서 보는 것은 "로그인 화면으로 튕기지 않는가"다.
    await page.route("**/api/travel/**", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          code: "OK",
          data: { ongoing: null, next: null, recentCount: 0 },
        }),
      }),
    );

    await page.goto("/login");
    await page.evaluate(() => navigator.serviceWorker.ready);
    await page.reload();

    await page.getByRole("textbox", { name: "아이디" }).fill("admin");
    await page.getByLabel("비밀번호").fill("password");
    await page.getByRole("button", { name: "로그인" }).click();
    await page.waitForURL(/\/(select|home)/);

    await context.setOffline(true);
    try {
      await page.reload();
      await expect(page).not.toHaveURL(/\/login/);
      await expect(page.getByRole("textbox", { name: "아이디" })).toHaveCount(
        0,
      );
    } finally {
      await context.setOffline(false);
    }
  });
});
