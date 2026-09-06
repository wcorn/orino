import { expect, type Page, test } from "./support/test";

/**
 * 표를 .xlsx로 내보내기(#1308).
 *
 * 「내려받기」는 브라우저만 하는 일이다 — jsdom엔 objectURL도 저장 대화상자도 없어서
 * 단위 테스트는 링크를 만들어 눌렀다는 데까지만 본다. 실제로 파일이 떨어지는지, 그 이름이
 * 서버가 정한 한글 이름 그대로인지는 여기서만 확인된다.
 */

const NOTE_ID = 1;

const ok = (data: unknown) => ({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ code: "OK", data }),
});

async function mockNoteWithTable(page: Page) {
  await page.route("**/api/auth/reissue", (route) =>
    route.fulfill(ok({ accessToken: "mock-access-token" })),
  );

  await page.route("**/api/notes", (route) =>
    route.fulfill(
      ok({
        notes: [
          {
            id: NOTE_ID,
            title: "테스트 노트",
            parentId: null,
            sortOrder: 0,
            children: [],
          },
        ],
      }),
    ),
  );

  await page.route(`**/api/notes/${NOTE_ID}`, (route) => {
    if (route.request().method() === "PATCH")
      return route.fulfill(ok({ id: NOTE_ID }));
    return route.fulfill(
      ok({
        id: NOTE_ID,
        materialId: null,
        parentId: null,
        title: "테스트 노트",
        sortOrder: 0,
        content: {
          type: "doc",
          content: [
            { type: "paragraph", content: [{ type: "text", text: "첫째 줄" }] },
            { type: "datasetTable", attrs: { datasetId: 1 } },
          ],
        },
        updatedAt: "2026-01-01T00:00:00Z",
      }),
    );
  });

  await page.route("**/api/datasets/**", (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/rows"))
      return route.fulfill(
        ok({
          rows: [{ id: 100, rowIndex: 0, cells: ["연필", "500"] }],
          offset: 0,
          limit: 100,
        }),
      );
    if (pathname.endsWith("/merges")) return route.fulfill(ok({ merges: [] }));
    return route.fulfill(
      ok({
        id: 1,
        name: "주문 내역",
        columns: [
          { key: "c0", label: "품목" },
          { key: "c1", label: "단가" },
        ],
        rowCount: 1,
      }),
    );
  });

  // 내보내기는 봉투가 없다 — 바이트가 그대로 온다. 나중에 건 라우트가 먼저 잡으므로
  // 위의 포괄 라우트보다 <b>뒤에</b> 건다.
  await page.route("**/api/datasets/1/export*", (route) =>
    route.fulfill({
      status: 200,
      headers: {
        "Content-Type":
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "Content-Disposition":
          "attachment; filename=\"__ __.xlsx\"; filename*=UTF-8''%EC%A3%BC%EB%AC%B8%20%EB%82%B4%EC%97%AD.xlsx",
      },
      body: "xlsx-bytes",
    }),
  );
}

test.describe("표를 엑셀로 내보내기", () => {
  test.beforeEach(async ({ page }) => {
    await mockNoteWithTable(page);
    await page.goto(`/notes?note=${NOTE_ID}`);
    await expect(page.getByTestId("dataset-grid")).toBeVisible();
  });

  test("우클릭 메뉴로 내보내면 서버가 정한 이름으로 파일이 떨어진다", async ({
    page,
  }) => {
    await page.getByText("연필").click({ button: "right" });

    const downloadPromise = page.waitForEvent("download");
    await page.getByRole("menuitem", { name: "엑셀로 내보내기" }).click();
    const download = await downloadPromise;

    // 한글 이름은 RFC 5987 쪽이 정본이다 — ASCII 대체본을 읽으면 밑줄로 뭉개진다.
    expect(download.suggestedFilename()).toBe("주문 내역.xlsx");

    // 메뉴는 닫힌다 — 열어둔 채로 두면 다음 조작을 가린다.
    await expect(
      page.getByRole("menuitem", { name: "엑셀로 내보내기" }),
    ).toBeHidden();
  });
});
