import { expect, type Page, test } from "@playwright/test";

/**
 * 거래 입력 — <b>마우스 없이 완결된다</b>(`LDG-018`, #1260).
 *
 * <p>이 스펙이 확인하는 것은 화면의 모양이 아니라 <b>손의 경로</b>다: `N`으로 열고, 숫자를 치고,
 * `Enter`로 저장한다. 하루 30초 입력은 이 경로가 끊기지 않아야 성립하고, 끊기면 사람은
 * 이 기능을 안 쓴다.
 *
 * <p>필터가 URL에 남는지도 여기서 본다 — MemoryRouter를 쓰는 RTL로는 실제 주소를 볼 수 없다.
 */

const ok = (data: unknown) => ({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ code: "OK", data }),
});

interface Captured {
  created: Record<string, unknown>[];
}

async function mockLedger(page: Page): Promise<Captured> {
  const captured: Captured = { created: [] };

  // 인증 외의 API는 통째로 막는다(auth.spec.ts와 같은 이유).
  await page.route(
    (url) => url.pathname.startsWith("/api/"),
    (route) => route.fulfill(ok(null)),
  );
  await page.route("**/api/auth/reissue", (route) =>
    route.fulfill(ok({ accessToken: "mock-access-token" })),
  );
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
  await page.route("**/api/ledger/assets", (route) =>
    route.fulfill(
      ok({
        groups: [
          {
            id: null,
            name: "그 외",
            kind: "ETC",
            displayOrder: 0,
            collapsed: false,
            subtotal: 1000000,
            assets: [
              {
                id: 1,
                groupId: null,
                name: "급여통장",
                type: "CHECKING",
                accountLast4: null,
                displayOrder: 0,
                hidden: false,
                closedReason: null,
                maturityDate: null,
                targetAmount: null,
                linkedAssetId: null,
                linkedAssetName: null,
                balance: 1000000,
                unpaidAmount: null,
              },
            ],
          },
        ],
        hidden: [],
        totalAssets: 1000000,
        liabilities: 0,
        netWorth: 1000000,
      }),
    ),
  );
  await page.route("**/api/ledger/categories*", (route) =>
    route.fulfill(
      ok([
        {
          id: 21,
          flow: "EXPENSE",
          name: "식비",
          parentId: null,
          color: null,
          icon: null,
          displayOrder: 0,
          archived: false,
          children: [],
        },
      ]),
    ),
  );
  await page.route("**/api/ledger/settings", (route) =>
    route.fulfill(
      ok({
        monthStartDay: 1,
        monthStartWeekendPolicy: "AS_IS",
        defaultAssetId: null,
        defaultPerspective: "SPEND",
      }),
    ),
  );
  await page.route("**/api/ledger/transactions/suggest*", (route) =>
    route.fulfill(ok([])),
  );
  await page.route("**/api/ledger/transactions*", async (route) => {
    if (route.request().method() === "POST") {
      const body = route.request().postDataJSON() as Record<string, unknown>;
      captured.created.push(body);
      return route.fulfill(
        ok({
          transaction: { ...body, id: 1, status: "CONFIRMED" },
          savedAs: "CONFIRMED",
        }),
      );
    }
    return route.fulfill(
      ok({
        todayLine: "2026-08-28",
        monthTotals: {
          income: 0,
          expense: 0,
          transfer: 0,
          scheduledExpense: 0,
          scheduledIncome: 0,
          scheduledCount: 0,
        },
        groups: [],
      }),
    );
  });

  return captured;
}

test.describe("가계부 입력", () => {
  test("N → 숫자 → Tab → Enter 로 마우스 없이 저장한다", async ({ page }) => {
    const captured = await mockLedger(page);
    await page.goto("/ledger/transactions");
    await expect(page.getByRole("heading", { name: "내역" })).toBeVisible();

    // 여기서부터 마우스를 쓰지 않는다.
    await page.keyboard.press("n");
    const dialog = page.getByRole("dialog");
    await expect(dialog).toBeVisible();

    // 열리면 금액 칸에 커서가 있다 — 바로 숫자부터 친다.
    await expect(page.getByLabel("금액")).toBeFocused();
    await page.keyboard.type("4500");
    // Tab으로 칸을 옮겨도 Enter는 여전히 저장이다(통화 버튼 → 날짜).
    await page.keyboard.press("Tab");
    await page.keyboard.press("Tab");
    await expect(page.getByLabel("날짜")).toBeFocused();
    await page.keyboard.press("Enter");

    await expect(dialog).toBeHidden();
    expect(captured.created).toHaveLength(1);
    expect(captured.created[0]).toMatchObject({
      type: "EXPENSE",
      amount: 4500,
      assetId: 1,
    });
  });

  test("글자를 치는 중에는 N이 단축키가 아니다", async ({ page }) => {
    await mockLedger(page);
    await page.goto("/ledger/transactions");

    const search = page.getByLabel("내역 검색");
    await search.click();
    await page.keyboard.type("nn");

    await expect(search).toHaveValue("nn");
    await expect(page.getByRole("dialog")).toBeHidden();
  });

  test("필터가 주소에 남는다 — 새로고침해도 그대로다", async ({ page }) => {
    await mockLedger(page);
    await page.goto("/ledger/transactions");

    await page.getByRole("button", { name: "확정만" }).click();
    await expect(page).toHaveURL(/status=CONFIRMED/);

    await page.reload();
    await expect(page.getByRole("button", { name: "확정만" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });
});
