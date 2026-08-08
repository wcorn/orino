import { expect, test } from "@playwright/test";

/**
 * Service Worker 도입 검증.
 *
 * <p><b>SW는 여행만이 아니라 앱 전체에 걸린다.</b> 일상 워크스페이스(노트·복습·라이프로그)가
 * 전부 SW 아래로 들어가므로, 이 작업의 절반은 "기존 화면이 그대로인가"다.
 *
 * <p>dev 서버에는 SW가 없어(HMR 간섭 방지) <b>빌드 결과</b>로 확인한다 — 실제로 배포되는 것도
 * 빌드 결과다.
 */
test.describe("Service Worker", () => {
  test("등록되고 페이지를 제어한다", async ({ page }) => {
    await page.goto("/login");

    const scope = await page.evaluate(async () => {
      const registration = await navigator.serviceWorker.ready;
      return registration.scope;
    });

    // 스코프가 루트여야 앱 전체(일상 포함)를 덮는다.
    expect(scope).toContain("localhost:4173/");

    // ready만으로는 "이 페이지를 제어 중"인지 알 수 없다 — 첫 방문은 아직 미제어다.
    await page.reload();
    const controlled = await page.evaluate(
      () => navigator.serviceWorker.controller !== null,
    );
    expect(controlled).toBe(true);
  });

  test("앱 셸을 precache한다", async ({ page }) => {
    await page.goto("/login");
    await page.evaluate(() => navigator.serviceWorker.ready);

    const cachedCount = await page.evaluate(async () => {
      const names = await caches.keys();
      let total = 0;
      for (const name of names) {
        total += (await (await caches.open(name)).keys()).length;
      }
      return total;
    });

    expect(cachedCount).toBeGreaterThan(0);
  });

  test("푸시 알림 권한을 요청할 수 있는 환경이다", async ({ page }) => {
    await page.goto("/login");

    // 3단계에서 구독을 붙일 자리다. 브라우저가 PushManager를 주는지 먼저 확인한다.
    const supported = await page.evaluate(async () => {
      const registration = await navigator.serviceWorker.ready;
      return "pushManager" in registration && "Notification" in window;
    });

    expect(supported).toBe(true);
  });

  test("일상 워크스페이스가 그대로 뜬다 — SW가 앱 전체에 걸린다", async ({
    page,
  }) => {
    await page.goto("/login");
    await page.evaluate(() => navigator.serviceWorker.ready);
    // SW가 제어하는 상태에서 확인해야 의미가 있다.
    await page.reload();

    await expect(page.getByRole("textbox", { name: "아이디" })).toBeVisible();
    await expect(page.getByRole("button", { name: "로그인" })).toBeVisible();
  });

  test("SW가 제어해도 라우팅이 살아 있다 — precache가 index.html을 가로채면 죽는다", async ({
    page,
  }) => {
    await page.goto("/login");
    await page.evaluate(() => navigator.serviceWorker.ready);

    // SPA 경로로 직접 들어가도 앱이 떠야 한다(미인증이라 /login으로 되돌아온다).
    await page.goto("/notes");
    await expect(page).toHaveURL(/\/login/);
  });
});
