import { expect, type Page, test } from "@playwright/test";

/**
 * 청구서 → 결제 처리 → 상태 전이(#1266).
 *
 * <p>확인하는 것은 모양이 아니라 <b>무엇이 서버로 갔고 화면이 어떻게 바뀌었나</b>다.
 * 전액이면 금액을 보내지 않고(=남은 전액), 일부면 그 금액만 보낸다 — 이 구분이 무너지면
 * 실제로 낸 돈과 장부가 갈라진다.
 */

const ok = (data: unknown) => ({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ code: "OK", data }),
});

interface Captured {
  payments: Record<string, unknown>[];
}

const BREAKDOWN = {
  usage: 842000,
  installment: 0,
  carriedOver: 0,
  interestFee: 0,
  adjustment: 0,
  refund: 0,
  discount: 0,
  billed: 842000,
  paid: 0,
  remaining: 842000,
};

const STATEMENT = {
  id: 7,
  cardAssetId: 3,
  cycleStart: "2026-08-01",
  cycleEnd: "2026-08-31",
  paymentDate: "2026-09-14",
  status: "CONFIRMED",
  overdue: false,
  breakdown: BREAKDOWN,
  paidOn: null,
  carriedToStatementId: null,
};

async function mockLedger(page: Page): Promise<Captured> {
  const captured: Captured = { payments: [] };
  let statement = STATEMENT;

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
  await page.route("**/api/ledger/summary", (route) =>
    route.fulfill(
      ok({
        monthEstimate: 0,
        monthSpent: 0,
        monthScheduled: 0,
        uncategorizedCount: 0,
        monthEndBalance: 0,
        remainingOutflow: 0,
        overdueCount: 0,
        period: { start: "2026-08-01", end: "2026-08-31" },
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
            subtotal: 1500000,
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
                balance: 1500000,
                unpaidAmount: null,
              },
            ],
          },
        ],
        hidden: [],
        totalAssets: 1500000,
        liabilities: 842000,
        netWorth: 658000,
      }),
    ),
  );
  await page.route("**/api/ledger/cards", (route) =>
    route.fulfill(
      ok({
        cards: [
          {
            id: 3,
            name: "신한 Deep Dream",
            accountLast4: "1234",
            cycleStartDay: 1,
            cycleCloseDay: 99,
            paymentDay: 14,
            paymentAssetId: 1,
            paymentAssetName: "급여통장",
            creditLimit: 5000000,
            hasCycle: true,
            unpaidAmount: 842000,
            currentStatement: statement,
          },
        ],
        installmentOutstanding: 0,
      }),
    ),
  );
  await page.route("**/api/ledger/cards/*/statements", (route) =>
    route.fulfill(ok([statement])),
  );
  await page.route("**/api/ledger/statements/*/transactions", (route) =>
    route.fulfill(ok([])),
  );
  await page.route("**/api/ledger/statements/*/pay", (route) => {
    const body = route.request().postDataJSON() as Record<string, unknown>;
    captured.payments.push(body);
    const paid = (body.amount as number | null) ?? BREAKDOWN.remaining;
    statement = {
      ...statement,
      status: paid >= BREAKDOWN.remaining ? "PAID" : "PARTIAL",
      paidOn: (body.paidOn as string | null) ?? statement.paymentDate,
      breakdown: {
        ...BREAKDOWN,
        paid,
        remaining: Math.max(BREAKDOWN.remaining - paid, 0),
      },
    };
    return route.fulfill(ok(statement));
  });

  return captured;
}

test.describe("카드 청구서", () => {
  test("전액 결제하면 납부 완료로 넘어간다", async ({ page }) => {
    const captured = await mockLedger(page);
    await page.goto("/ledger/cards/3/statements");

    await expect(
      page.getByRole("heading", { name: "신한 Deep Dream" }),
    ).toBeVisible();
    // 산식이 항목째로 서 있다.
    await expect(page.getByText("사용 합계")).toBeVisible();

    await page.getByRole("button", { name: "결제 처리" }).first().click();
    const dialog = page.getByRole("dialog");
    await expect(
      dialog.getByText("카드 대금은 자동으로 적지 않습니다"),
    ).toBeVisible();
    await dialog.getByRole("button", { name: "결제 처리" }).click();

    await expect(dialog).toBeHidden();
    // 남은 전액이라는 뜻으로 금액을 보내지 않는다.
    expect(captured.payments).toHaveLength(1);
    expect(captured.payments[0].amount).toBeNull();
    await expect(page.getByText("납부 완료")).toBeVisible();
  });

  test("일부만 내면 이월 안내가 뜨고 부분 납부로 남는다", async ({ page }) => {
    const captured = await mockLedger(page);
    await page.goto("/ledger/cards/3/statements");
    await expect(page.getByText("사용 합계")).toBeVisible();

    await page.getByRole("button", { name: "결제 처리" }).first().click();
    const dialog = page.getByRole("dialog");
    await dialog.getByRole("button", { name: /일부/ }).click();
    await dialog.getByLabel("결제 금액").fill("400000");

    // 이월은 지출이 아니다 — 그 사실을 그 자리에서 말한다.
    await expect(
      dialog.getByText("442,000이 다음 청구서로 이월됩니다"),
    ).toBeVisible();
    await dialog.getByRole("button", { name: "결제 처리" }).click();

    await expect(dialog).toBeHidden();
    expect(captured.payments[0].amount).toBe(400000);
    await expect(page.getByText("부분 납부").first()).toBeVisible();
    await expect(page.getByText(/남은 442,000/)).toBeVisible();
  });
});
