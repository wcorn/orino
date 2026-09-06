import { expect, type Page, test } from "./support/test";

/**
 * 가져오기 — <b>파일 여러 장</b>(#1320).
 *
 * <p>이 스펙이 있는 이유는 하나다: <b>실제로 보낸 것을 읽을 수 있는 유일한 자리</b>다.
 * RTL/jsdom은 multipart 파트의 내용을 비운 채 보내서 「두 파일이 다 담겼는가」·「설정이
 * 두 벌인가」·「어느 줄을 보냈는가」를 확인할 수 없다. 여기서는 요청 본문을 그대로 본다.
 *
 * <p>`<input multiple>`로 두 장을 고르는 길도 실제 브라우저에서만 실증된다.
 */

const ok = (data: unknown) => ({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ code: "OK", data }),
});

interface Captured {
  preview: string[];
  execute: string[];
}

async function mockImport(page: Page): Promise<Captured> {
  const captured: Captured = { preview: [], execute: [] };

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
            subtotal: 0,
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
                balance: 0,
                unpaidAmount: null,
              },
            ],
          },
        ],
        hidden: [],
        totalAssets: 0,
        liabilities: 0,
        netWorth: 0,
      }),
    ),
  );
  await page.route("**/api/ledger/import/batches", (route) =>
    route.fulfill(ok([])),
  );
  await page.route("**/api/ledger/import/analyze", (route) =>
    route.fulfill(
      ok({
        headers: ["날짜", "내용", "금액"],
        sample: [["2026-01-10", "스타벅스", "-5500"]],
        totalRows: 1,
        headerRow: 0,
        presets: [],
      }),
    ),
  );
  await page.route("**/api/ledger/import/preview", (route) => {
    captured.preview.push(route.request().postData() ?? "");
    return route.fulfill(
      ok({
        files: [
          {
            fileIndex: 0,
            fileName: "2026-01.csv",
            rows: [row(2, "2026-01-10", "스타벅스")],
            totalRows: 1,
            duplicateCount: 0,
            errorCount: 0,
          },
          {
            fileIndex: 1,
            fileName: "2026-02.csv",
            rows: [row(2, "2026-02-10", "편의점")],
            totalRows: 1,
            duplicateCount: 0,
            errorCount: 0,
          },
        ],
        totalRows: 2,
        duplicateCount: 0,
        errorCount: 0,
      }),
    );
  });
  await page.route("**/api/ledger/import/execute", (route) => {
    captured.execute.push(route.request().postData() ?? "");
    return route.fulfill(
      ok({
        batches: [
          {
            batchId: 1,
            fileName: "2026-01.csv",
            inserted: 1,
            skipped: 0,
          },
          {
            batchId: 2,
            fileName: "2026-02.csv",
            inserted: 1,
            skipped: 0,
          },
        ],
        inserted: 2,
        skipped: 0,
      }),
    );
  });

  return captured;
}

function row(rowNumber: number, occurredOn: string, title: string) {
  return {
    rowNumber,
    occurredOn,
    type: "EXPENSE",
    amount: 5500,
    title,
    memo: null,
    categoryId: null,
    categoryName: null,
    error: null,
    duplicateOf: null,
    duplicateOfRow: null,
    assetId: 1,
    assetName: "급여통장",
  };
}

function csv(name: string) {
  return {
    name,
    mimeType: "text/csv",
    buffer: Buffer.from("날짜,내용,금액\n2026-01-10,스타벅스,-5500\n"),
  };
}

test.describe("가져오기 — 파일 여러 장", () => {
  test("두 장을 한 번에 골라 한 요청으로 보낸다", async ({ page }) => {
    const captured = await mockImport(page);
    await page.goto("/ledger/import");
    await expect(
      page.getByRole("heading", { name: "가져오기", exact: true }),
    ).toBeVisible();

    await page
      .getByLabel("가져올 파일")
      .setInputFiles([csv("2026-01.csv"), csv("2026-02.csv")]);
    await expect(page.getByText("고른 파일 2장")).toBeVisible();

    const next = page.getByRole("button", { name: "열 맞추기" });
    await expect(next).toBeEnabled();
    await next.click();

    // 열 구성이 같으니 한 장만 맞추고 나머지에 퍼뜨린다.
    await page.getByRole("combobox", { name: "자산", exact: true }).click();
    await page.getByRole("option", { name: "급여통장" }).click();
    await page.getByRole("combobox", { name: "날짜 열" }).click();
    await page.getByRole("option", { name: "1. 날짜" }).click();
    await page.getByRole("combobox", { name: "금액 열" }).click();
    await page.getByRole("option", { name: "3. 금액" }).click();
    await page
      .getByRole("button", { name: /이 설정을 나머지 1장에도/ })
      .click();
    await expect(page.getByText("1장에 적용했어요")).toBeVisible();

    await page.getByRole("button", { name: "미리 보기" }).click();
    await expect(
      page.getByRole("heading", { name: "2026-01.csv" }),
    ).toBeVisible();

    /*
     * 여기가 이 스펙의 값이다 — 두 파일이 <b>한 요청에</b> 담겼는지는 본문을 봐야 안다.
     * 나눠 보내면 둘째 파일을 볼 때 첫 파일이 아직 없어서 겹치는 줄이 안 걸린다.
     */
    expect(captured.preview).toHaveLength(1);
    expect(captured.preview[0]).toContain("2026-01.csv");
    expect(captured.preview[0]).toContain("2026-02.csv");
    // 설정도 파일마다 한 벌씩 간다.
    expect(captured.preview[0].match(/"assetId":1/g)).toHaveLength(2);

    await page.getByRole("button", { name: "2건 넣기" }).click();
    await expect(page.getByText(/파일 2장에서 2건을 넣었어요/)).toBeVisible();

    // 넣을 줄도 파일마다 따로 간다 — 줄 번호는 파일 안에서 세기 때문이다.
    expect(captured.execute).toHaveLength(1);
    expect(captured.execute[0].match(/"rowNumbers":\[2\]/g)).toHaveLength(2);
    expect(captured.execute[0]).toContain('"source":"2026-01"');
    expect(captured.execute[0]).toContain('"source":"2026-02"');
  });

  /** 못 읽은 파일을 그냥 지나치면 사람은 전부 들어갔다고 믿는다. */
  test("읽지 못한 파일이 있으면 다음으로 넘어가지 못한다", async ({ page }) => {
    await mockImport(page);
    await page.route("**/api/ledger/import/analyze", (route) =>
      route.fulfill({
        status: 400,
        contentType: "application/json",
        body: JSON.stringify({
          code: "LDG-ERR-035",
          message: "암호가 걸린 파일입니다.",
        }),
      }),
    );

    await page.goto("/ledger/import");
    await page.getByLabel("가져올 파일").setInputFiles([csv("거래내역.csv")]);

    await expect(page.getByText(/암호가 걸린 파일이에요/)).toBeVisible();
    await expect(page.getByText(/읽지 못한 파일이 1장 있어요/)).toBeVisible();
    await expect(
      page.getByRole("button", { name: "열 맞추기" }),
    ).toBeDisabled();
  });
});
