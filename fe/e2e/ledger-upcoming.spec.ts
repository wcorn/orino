import { expect, type Page, test } from "./support/test";

/**
 * 예정 줄 인라인 수정 — <b>그 줄에서 끝난다</b>(#1265).
 *
 * <p>「이번 달만 17,000원」을 고치려고 모달을 열게 하지 않는다. 확인하는 것은 모양이 아니라
 * <b>무엇이 서버로 갔는가</b>다: 정기 회차를 고치면 규칙이 아니라 <b>그 회차 하나</b>에
 * override가 남아야 한다.
 *
 * <p>hover로만 나타나는 버튼이라 RTL로는 「보이는지」를 확인할 수 없다 — 실제 포인터가 필요하다.
 */

const ok = (data: unknown) => ({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ code: "OK", data }),
});

interface Captured {
  occurrences: Record<string, unknown>[];
}

const RECURRING_ITEM = {
  kind: "RECURRING",
  date: "2026-08-31",
  dday: 3,
  title: "넷플릭스 프리미엄",
  amount: 17000,
  flow: "EXPENSE",
  isTransfer: false,
  overdue: false,
  estimated: false,
  categoryId: null,
  assetId: 1,
  assetName: "급여통장",
  transactionId: null,
  recurringId: 12,
  occurrenceDate: "2026-08-31",
  statementId: null,
  installmentId: null,
};

const CARD_ITEM = {
  ...RECURRING_ITEM,
  kind: "CARD_PAYMENT",
  date: "2026-09-14",
  dday: 17,
  title: "카드 대금 · 신한 Deep Dream",
  amount: 842000,
  flow: "TRANSFER",
  isTransfer: true,
  recurringId: null,
  occurrenceDate: null,
  statementId: 7,
};

async function mockLedger(page: Page): Promise<Captured> {
  const captured: Captured = { occurrences: [] };

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
  await page.route("**/api/ledger/summary", (route) =>
    route.fulfill(
      ok({
        monthEstimate: 0,
        monthSpent: 0,
        monthScheduled: 0,
        uncategorizedCount: 0,
        monthEndBalance: 0,
        remainingOutflow: 859000,
        overdueCount: 0,
        period: { start: "2026-08-01", end: "2026-08-31" },
      }),
    ),
  );
  await page.route("**/api/ledger/upcoming/occurrence", async (route) => {
    captured.occurrences.push(
      route.request().postDataJSON() as Record<string, unknown>,
    );
    return route.fulfill(
      ok({
        recurringId: 12,
        name: "넷플릭스 프리미엄",
        occurrenceDate: "2026-08-31",
        date: "2026-08-31",
        amount: 22000,
        action: "AMOUNT",
        overdue: false,
        transactionId: null,
      }),
    );
  });
  await page.route("**/api/ledger/upcoming*", (route) =>
    route.fulfill(
      ok({
        from: "2026-08-28",
        to: "2026-09-27",
        days: 30,
        stats: {
          outflow: 859000,
          income: 0,
          currentBalance: 1500000,
          expectedBalance: 641000,
          minBalance: {
            amount: 641000,
            date: "2026-09-14",
            reason: "카드 대금 · 신한 Deep Dream",
          },
          count: 2,
          byKind: { RECURRING: 1, CARD_PAYMENT: 1 },
        },
        items: [RECURRING_ITEM, CARD_ITEM],
      }),
    ),
  );
  await page.route("**/api/ledger/transactions*", (route) =>
    route.fulfill(
      ok({
        todayLine: "2026-08-28",
        monthTotals: {
          income: 0,
          expense: 4500,
          transfer: 0,
          scheduledExpense: 0,
          scheduledIncome: 0,
          scheduledCount: 0,
        },
        groups: [
          {
            date: "2026-08-28",
            income: 0,
            expense: 4500,
            items: [
              {
                id: 10,
                type: "EXPENSE",
                status: "CONFIRMED",
                occurredOn: "2026-08-28",
                occurredAt: null,
                amount: 4500,
                assetId: 1,
                assetName: "급여통장",
                counterAssetId: null,
                counterAssetName: null,
                categoryId: 21,
                categoryName: "식비",
                title: "스타벅스 역삼",
                memo: null,
                source: "MANUAL",
                estimated: false,
                refundOfId: null,
                tags: [],
                fx: null,
              },
            ],
          },
        ],
      }),
    ),
  );

  return captured;
}

test.describe("예정 줄", () => {
  test("hover로 나온 「금액 수정」이 그 회차만 고친다", async ({ page }) => {
    const captured = await mockLedger(page);
    await page.goto("/ledger/transactions");
    await expect(page.getByRole("heading", { name: "내역" })).toBeVisible();

    // 파생 예정이 원장 줄과 한 스크롤 위에 있다.
    const row = page.getByText("넷플릭스 프리미엄").locator("..");
    await expect(row).toBeVisible();
    await expect(page.getByText("스타벅스 역삼")).toBeVisible();

    await row.hover();
    const edit = row.getByRole("button", { name: "금액 수정" });
    await expect(edit).toBeVisible();
    await edit.click();

    const input = page.getByLabel("이번 회차 금액");
    await input.fill("22000");
    await page.getByRole("button", { name: "적용" }).click();

    await expect
      .poll(() => captured.occurrences.length, { timeout: 5000 })
      .toBe(1);
    // 규칙이 아니라 그 회차 하나다 — 키는 규칙이 계산한 원래 예정일이다.
    expect(captured.occurrences[0]).toMatchObject({
      recurringId: 12,
      occurrenceDate: "2026-08-31",
      action: "AMOUNT",
      amount: 22000,
    });
  });

  test("카드 대금 줄에는 회차 액션이 없다 — 청구서에서 정산할 일이다", async ({
    page,
  }) => {
    await mockLedger(page);
    await page.goto("/ledger/transactions");

    const row = page.getByText("카드 대금 · 신한 Deep Dream").locator("..");
    await row.hover();

    await expect(row.getByRole("button", { name: "금액 수정" })).toHaveCount(0);
  });

  test("예정 화면의 최저 예상 잔액이 이유와 함께 보인다", async ({ page }) => {
    await mockLedger(page);
    await page.goto("/ledger/upcoming");

    await expect(page.getByRole("heading", { name: "예정" })).toBeVisible();
    await expect(page.getByText("최저 예상 잔액")).toBeVisible();
    await expect(page.getByText("641,000").first()).toBeVisible();
    await expect(
      page.getByText(/「카드 대금 · 신한 Deep Dream」 직후가 가장 낮은 지점/),
    ).toBeVisible();
  });
});
