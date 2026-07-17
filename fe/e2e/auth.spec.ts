import { expect, test } from "@playwright/test";

/**
 * 앱이 실제로 부르는 주소로 가로챈다.
 *
 * 호스트를 하드코딩하면(`https://api.orino.dev/api`) dev에서 매치되지 않는다 —
 * `.env.development`가 `VITE_API_URL=/api`라 앱은 Vite 프록시(`/api/...`)로 부른다.
 * 매치가 안 되면 요청이 프록시를 타고 실제 BE로 나가고, BE가 없으면 500이 온다.
 * 경로 패턴은 dev·배포 양쪽에 다 걸린다.
 */
function mockAuthApi(page: import("@playwright/test").Page) {
  return Promise.all([
    page.route("**/api/auth/login", async (route) => {
      const body = route.request().postDataJSON();
      if (body.loginId === "admin" && body.password === "password") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            code: "OK",
            data: { accessToken: "mock-access-token" },
          }),
        });
      } else {
        await route.fulfill({
          status: 401,
          contentType: "application/json",
          body: JSON.stringify({
            code: "AUTH-ERR-001",
            message: "아이디 또는 비밀번호가 올바르지 않습니다.",
          }),
        });
      }
    }),

    page.route("**/api/auth/reissue", async (route) => {
      await route.fulfill({
        status: 401,
        contentType: "application/json",
        body: JSON.stringify({
          code: "AUTH-ERR-002",
          message: "유효하지 않은 토큰입니다.",
        }),
      });
    }),

    // 로그인 후 화면엔 Sidebar가 딸려 오고 복습 요약을 부른다. 안 막아두면
    // 실제 BE가 가짜 토큰을 401로 거부하고 앱이 정상적으로 자동 로그아웃해 버린다
    // (= BE가 떠 있으면 실패하는 테스트가 된다).
    page.route("**/api/planner/reviews/summary*", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        // 형태는 src/test/mocks/handlers.ts의 것을 따른다 — 지어내면 Sidebar가 깨진다.
        body: JSON.stringify({
          code: "OK",
          data: {
            today: "2026-05-18",
            counts: { now: 0, overdue: 0, upcoming: 0, doneToday: 0 },
            estimatedMinutes: 0,
            materials: [],
          },
        }),
      });
    }),

    page.route("**/api/auth/logout", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ code: "OK", data: null }),
      });
    }),
  ]);
}

test.describe("인증 흐름", () => {
  test.beforeEach(async ({ page }) => {
    await mockAuthApi(page);
  });

  test("랜딩 페이지에서 시작하기 클릭 시 /login으로 이동한다", async ({
    page,
  }) => {
    await page.goto("/");
    await expect(page.getByText("orino")).toBeVisible();
    await page.getByRole("link", { name: /시작하기/ }).click();
    await expect(page).toHaveURL(/\/login/);
  });

  test("미인증 시 /home 접근하면 /login으로 리다이렉트된다", async ({
    page,
  }) => {
    await page.goto("/home");
    await expect(page).toHaveURL(/\/login/);
  });

  test("로그인 성공 시 /home으로 이동한다", async ({ page }) => {
    await page.goto("/login");

    await page.getByLabel("아이디").fill("admin");
    await page.getByLabel("비밀번호").fill("password");
    await page.getByRole("button", { name: "로그인" }).click();

    await expect(page).toHaveURL(/\/home/);
    // 워드마크가 글자를 쪼개 놓아(or + ı + no) getByText로는 안 잡힌다. 접근성 이름으로 찾는다.
    await expect(
      page.getByRole("img", { name: "orino" }).first(),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: /로그아웃/ })).toBeVisible();
  });

  test("로그인 실패 시 /login에 머문다", async ({ page }) => {
    await page.goto("/login");

    await page.getByLabel("아이디").fill("wrong");
    await page.getByLabel("비밀번호").fill("wrong");
    await page.getByRole("button", { name: "로그인" }).click();

    await expect(page).toHaveURL(/\/login/);
  });

  test("로그인 → 로그아웃 전체 흐름", async ({ page }) => {
    // 로그인
    await page.goto("/login");
    await page.getByLabel("아이디").fill("admin");
    await page.getByLabel("비밀번호").fill("password");
    await page.getByRole("button", { name: "로그인" }).click();
    await expect(page).toHaveURL(/\/home/);

    // 로그아웃
    await page.getByRole("button", { name: /로그아웃/ }).click();

    // 로그아웃 후 인증이 필요 없는 페이지로 이동
    await page.waitForURL(/\/(login)?$/);
    // 로고가 보이는지보다 "로그아웃됐는지"가 이 테스트가 확인할 것이다.
    await expect(page.getByRole("button", { name: /로그아웃/ })).toBeHidden();
  });
});
