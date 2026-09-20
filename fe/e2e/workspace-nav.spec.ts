import { expect, type Page, test } from "./support/test";

/**
 * 워크스페이스 진입 동선(#1258) — `/select` 3카드 → 각 워크스페이스 → 사이드바 스위처.
 *
 * <p>이 스펙은 <b>세 프로젝트 전부</b>에서 돈다(chromium · built · mobile-touch).
 * `/select`와 `Sidebar`는 여행·일상·링크가 모두 지나가는 공용 화면이라, 한 곳이 깨지면
 * 세 워크스페이스가 같이 막힌다. 모바일에서는 사이드바가 드로어로 접히므로
 * <b>드로어를 열고서도 스위처가 동작하는지</b>까지 같은 스펙으로 확인한다.
 *
 * <p>가계부가 빠지면서 스위처는 다시 세그먼트다(#1408). 4칸이던 동안만 드롭다운이었고,
 * 「지금 어디인지」는 칸의 `aria-current`가 말한다.
 */

const ok = (data: unknown) => ({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ code: "OK", data }),
});

/**
 * 인증 외의 API는 통째로 막는다(auth.spec.ts와 같은 이유) — 개별 경로만 막으면 화면이 새 API를
 * 부르기 시작할 때 하나씩 새고, 그때 이 스펙은 BE가 떠 있는 기계에서만 실패한다.
 */
async function mockApi(page: Page) {
  await page.route(
    (url) => url.pathname.startsWith("/api/"),
    (route) => route.fulfill(ok(null)),
  );
  await page.route("**/api/auth/reissue", (route) =>
    route.fulfill(ok({ accessToken: "mock-access-token" })),
  );
  // 사이드바가 이 응답의 형태에 기대므로 진짜에 가깝게 준다.
  await page.route("**/api/planner/reviews/summary*", (route) =>
    route.fulfill(
      ok({
        today: "2026-08-28",
        counts: { now: 0, overdue: 0, upcoming: 0, doneToday: 0 },
        estimatedMinutes: 0,
        materials: [],
      }),
    ),
  );
  await page.route("**/api/travel/summary", (route) =>
    route.fulfill(ok({ ongoing: null, next: null, recentCompleted: null })),
  );
  await page.route("**/api/shortlinks/summary", (route) =>
    route.fulfill(ok({ total: 0, visitsThisWeek: 0 })),
  );
  await page.route("**/api/shortlinks/tags", (route) => route.fulfill(ok([])));
  await page.route("**/api/shortlinks*", (route) =>
    route.fulfill(
      ok({
        counts: { all: 0, active: 0, inactive: 0 },
        favorites: [],
        recent: [],
      }),
    ),
  );
}

/** 세그먼트의 한 칸. 지금 있는 곳에는 `aria-current`가 붙는다. */
function segment(page: Page, workspace: string) {
  return page
    .getByRole("group", { name: "워크스페이스" })
    .getByRole("button", { name: workspace });
}

/**
 * 모바일은 사이드바가 드로어라 먼저 열어야 스위처를 누를 수 있다.
 *
 * <p>닫힌 드로어는 화면 밖으로 밀려 있을 뿐 <b>Playwright의 「visible」에는 걸린다</b> —
 * 그래서 `toBeVisible()`은 통과하고 `click()`만 「element is outside of the viewport」로
 * 실패했다. 여기서는 뷰포트 안에 들어왔는지까지 확인한다.
 *
 * <p>여는 버튼 존재 여부를 곧바로 묻지 않는다. 아직 렌더 전이면 「없다」로 보여
 * 드로어를 열지 않은 채 지나간다 — 앱 셸이 붙을 때까지 기다린 뒤 판단한다.
 */
async function openSidebar(page: Page) {
  const nav = page.getByRole("navigation", { name: "주 메뉴" });
  await expect(nav).toBeAttached();

  const menuButton = page.getByRole("button", { name: "메뉴 열기" });
  // 데스크톱에는 이 버튼이 없다(md 이상). 그쪽 사이드바는 늘 열려 있다.
  if (await menuButton.isVisible()) {
    await menuButton.click();
  }
  await expect(nav).toBeInViewport();
}

/** 지금 그 워크스페이스에 있는가. 세그먼트의 칸 하나가 그 사실을 들고 있다. */
async function expectCurrent(page: Page, workspace: string) {
  await expect(segment(page, workspace)).toHaveAttribute(
    "aria-current",
    "true",
  );
}

test.describe("워크스페이스 진입 동선", () => {
  test.beforeEach(async ({ page }) => {
    await mockApi(page);
  });

  test("/select에 카드가 셋 있고 각각 제 워크스페이스로 들어간다", async ({
    page,
  }) => {
    await page.goto("/select");

    await expect(
      page.getByRole("heading", { name: "어디로 갈까요" }),
    ).toBeVisible();
    for (const name of ["여행", "일상", "링크"]) {
      await expect(page.getByRole("button", { name })).toBeVisible();
    }
    // 가계부는 네 번째 카드였다. 안 쓰는 모듈이라 지웠다(#1405).
    await expect(page.getByRole("button", { name: "가계부" })).toHaveCount(0);

    await page.getByRole("button", { name: "링크" }).click();

    await expect(page).toHaveURL(/\/links$/);
    await openSidebar(page);
    await expectCurrent(page, "링크");
  });

  test("사이드바 세그먼트로 링크 → 일상 → 여행을 오간다", async ({ page }) => {
    await page.goto("/links");
    await openSidebar(page);

    await expectCurrent(page, "링크");
    await expect(page.getByRole("link", { name: /링크 목록/ })).toBeVisible();

    // 한 번 눌러서 옮긴다 — 드롭다운이던 동안은 열고 고르는 두 번이었다.
    await segment(page, "일상").click();

    await expect(page).toHaveURL(/\/home$/);
    await openSidebar(page);
    await expectCurrent(page, "일상");
    await expect(page.getByRole("link", { name: /학습 자료/ })).toBeVisible();

    await segment(page, "여행").click();

    await expect(page).toHaveURL(/\/travel$/);
    await openSidebar(page);
    await expectCurrent(page, "여행");
    await expect(page.getByRole("link", { name: /여행 목록/ })).toBeVisible();
  });

  test("목록 아래의 「선택 화면으로」가 /select로 되돌린다", async ({
    page,
  }) => {
    await page.goto("/home");
    await openSidebar(page);

    // 드롭다운이 사라지면서 갈 곳이 없어진 줄이다 — 없애지 않고 목록 아래로 내렸다.
    await page.getByRole("link", { name: "선택 화면으로" }).click();

    await expect(page).toHaveURL(/\/select$/);
    await expect(
      page.getByRole("heading", { name: "어디로 갈까요" }),
    ).toBeVisible();
  });

  test("가계부로 가던 주소는 선택 화면으로 돌아온다", async ({ page }) => {
    // 라우트가 사라졌으니 앱이 모르는 주소다. 폴백이 랜딩으로 보내고,
    // 로그인한 사용자는 거기서 선택 화면으로 넘어간다.
    await page.goto("/ledger");

    await expect(page).toHaveURL(/\/select$/);
    await expect(
      page.getByRole("heading", { name: "어디로 갈까요" }),
    ).toBeVisible();
  });
});
