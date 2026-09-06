import { expect, test } from "./support/test";

/**
 * 앱이 실제로 부르는 주소로 가로챈다.
 *
 * 호스트를 하드코딩하면(`https://api.orino.dev/api`) dev에서 매치되지 않는다 —
 * `.env.development`가 `VITE_API_URL=/api`라 앱은 Vite 프록시(`/api/...`)로 부른다.
 * 매치가 안 되면 요청이 프록시를 타고 실제 BE로 나가고, BE가 없으면 500이 온다.
 * 경로 패턴은 dev·배포 양쪽에 다 걸린다.
 *
 * <p><b>인증 외의 API는 통째로 막는다.</b> 개별 경로를 하나씩 막으면 화면이 새 API를
 * 부르기 시작할 때마다 하나씩 새고, 그때 이 테스트는 <b>BE가 떠 있는 기계에서만</b>
 * 실패한다 — 가짜 토큰을 실제 BE가 401로 거부하고 앱이 정상적으로 자동 로그아웃하기
 * 때문이다. 실제로 그렇게 깨졌다(#1098).
 *
 * <p>막는 방법에 주의: 글로브 {@code **&#47;api&#47;**}는 dev에서 <b>앱 소스</b>
 * ({@code /src/shared/api/client.ts})까지 삼켜 화면이 아예 안 뜬다. 경로 술어로 잡는다.
 *
 * <p>Playwright는 <b>나중에 등록한 route가 이긴다</b> — 포괄 규칙을 먼저 깔고 개별 규칙을 얹는다.
 */
function mockAuthApi(page: import("@playwright/test").Page) {
  return Promise.all([
    page.route(
      (url) => url.pathname.startsWith("/api/"),
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ code: "OK", data: null }),
        });
      },
    ),

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

    // 위 포괄 규칙으로도 막히지만, Sidebar가 이 응답의 <b>형태</b>에 기대므로 진짜에 가깝게 준다.
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

  test("로그인 성공 시 /select로 이동한다", async ({ page }) => {
    await page.goto("/login");

    await page.getByLabel("아이디").fill("admin");
    await page.getByLabel("비밀번호").fill("password");
    await page.getByRole("button", { name: "로그인" }).click();

    // 로그인 직후는 항상 워크스페이스 선택이다(마지막 선택을 기억하지 않는다).
    await expect(page).toHaveURL(/\/select/);
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
    await expect(page).toHaveURL(/\/select/);

    // 로그아웃
    await page.getByRole("button", { name: /로그아웃/ }).click();

    // 로그아웃 후 인증이 필요 없는 페이지로 이동
    await page.waitForURL(/\/(login)?$/);
    // 로고가 보이는지보다 "로그아웃됐는지"가 이 테스트가 확인할 것이다.
    await expect(page.getByRole("button", { name: /로그아웃/ })).toBeHidden();
  });
});
