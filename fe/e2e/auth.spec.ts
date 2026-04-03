import { expect, test } from "@playwright/test";

const API_BASE = "https://api.orino.dev/api";

function mockAuthApi(page: import("@playwright/test").Page) {
  return Promise.all([
    page.route(`${API_BASE}/auth/login`, async (route) => {
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

    page.route(`${API_BASE}/auth/reissue`, async (route) => {
      await route.fulfill({
        status: 401,
        contentType: "application/json",
        body: JSON.stringify({
          code: "AUTH-ERR-002",
          message: "유효하지 않은 토큰입니다.",
        }),
      });
    }),

    page.route(`${API_BASE}/auth/logout`, async (route) => {
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
    await expect(page.getByText("orino")).toBeVisible();
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
    await expect(page.getByText("orino")).toBeVisible();
  });
});
