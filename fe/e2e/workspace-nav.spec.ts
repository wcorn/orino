import { expect, type Page, test } from "./support/test";

/**
 * 워크스페이스 진입 동선(#1258) — `/select` 4카드 → 각 워크스페이스 → 사이드바 스위처.
 *
 * <p>이 스펙은 <b>세 프로젝트 전부</b>에서 돈다(chromium · built · mobile-touch).
 * `/select`와 `Sidebar`는 여행·일상·링크·가계부가 모두 지나가는 공용 화면이라, 한 곳이
 * 깨지면 네 워크스페이스가 같이 막힌다. 모바일에서는 사이드바가 드로어로 접히므로
 * <b>드로어를 열고서도 스위처가 동작하는지</b>까지 같은 스펙으로 확인한다.
 *
 * <p>스위처는 세그먼트가 아니라 드롭다운이다 — 224px에 4칸을 넣으면 아이콘과 라벨이 눌린다.
 * 그래서 「지금 어디인지」는 트리거의 접근성 이름이 말한다.
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

/** 스위처 트리거. 접근성 이름이 지금 있는 워크스페이스를 담는다. */
function switcher(page: Page, workspace: string) {
  return page.getByRole("button", {
    name: `워크스페이스 전환 — 현재 ${workspace}`,
  });
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

test.describe("워크스페이스 진입 동선", () => {
  test.beforeEach(async ({ page }) => {
    await mockApi(page);
  });

  test("/select에 카드가 넷 있고 각각 제 워크스페이스로 들어간다", async ({
    page,
  }) => {
    await page.goto("/select");

    await expect(
      page.getByRole("heading", { name: "어디로 갈까요" }),
    ).toBeVisible();
    for (const name of ["여행", "일상", "링크", "가계부"]) {
      await expect(page.getByRole("button", { name })).toBeVisible();
    }

    await page.getByRole("button", { name: "가계부" }).click();

    await expect(page).toHaveURL(/\/ledger$/);
    await expect(page.getByRole("heading", { name: "가계부" })).toBeVisible();
    await openSidebar(page);
    await expect(switcher(page, "가계부")).toBeVisible();
  });

  test("사이드바 스위처로 가계부 → 링크 → 일상을 오간다", async ({ page }) => {
    await page.goto("/ledger");
    await openSidebar(page);

    await expect(switcher(page, "가계부")).toBeVisible();
    // 가계부 메뉴가 서 있다(스타일은 다른 세트와 같고, 여기서는 자리만 본다).
    // 이름이 정확히 「내역」인 것으로 찾는다 — 대시보드 헤더의 「내역 보기」도 링크라
    // 부분 일치로는 둘이 걸린다.
    await expect(
      page.getByRole("link", { name: "내역", exact: true }),
    ).toBeVisible();

    await switcher(page, "가계부").click();
    await page.getByRole("menuitem", { name: "링크" }).click();

    await expect(page).toHaveURL(/\/links$/);
    await openSidebar(page);
    await expect(switcher(page, "링크")).toBeVisible();
    await expect(page.getByRole("link", { name: /링크 목록/ })).toBeVisible();

    await switcher(page, "링크").click();
    await page.getByRole("menuitem", { name: "일상" }).click();

    await expect(page).toHaveURL(/\/home$/);
    await openSidebar(page);
    await expect(switcher(page, "일상")).toBeVisible();
    await expect(page.getByRole("link", { name: /학습 자료/ })).toBeVisible();
  });

  test("스위처의 「선택 화면으로」가 /select로 되돌린다", async ({ page }) => {
    await page.goto("/ledger");
    await openSidebar(page);

    await switcher(page, "가계부").click();
    await page.getByRole("menuitem", { name: "선택 화면으로" }).click();

    await expect(page).toHaveURL(/\/select$/);
    await expect(
      page.getByRole("heading", { name: "어디로 갈까요" }),
    ).toBeVisible();
  });

  test("아직 없는 가계부 하위 경로는 가계부 홈으로 보낸다", async ({
    page,
  }) => {
    // 화면이 다 생긴 뒤로는 없는 경로를 일부러 고른다 — 규칙 자체를 확인한다.
    await page.goto("/ledger/there-is-no-such-page");

    await expect(page).toHaveURL(/\/ledger$/);
    await expect(page.getByRole("heading", { name: "가계부" })).toBeVisible();
  });
});
