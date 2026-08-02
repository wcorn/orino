import { expect, type Page, test } from "@playwright/test";

/**
 * 블록 우클릭 메뉴(#1015).
 *
 * 우클릭은 좌표 → 문서 위치 변환(posAtCoords)과 실제 마우스 이벤트가 얽혀 있어 jsdom으로는
 * 검증되지 않는다. 표 안 우클릭이 표 메뉴로 가는지(경계)도 실제 브라우저로 본다.
 */

const NOTE_ID = 1;

const ok = (data: unknown) => ({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ code: "OK", data }),
});

async function mockNote(page: Page) {
  await page.route("**/api/auth/reissue", (route) =>
    route.fulfill(ok({ accessToken: "mock-access-token" })),
  );
  await page.route("**/api/notes", (route) =>
    route.fulfill(
      ok({
        notes: [
          {
            id: NOTE_ID,
            title: "노트",
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
        title: "노트",
        sortOrder: 0,
        content: {
          type: "doc",
          content: [
            { type: "paragraph", content: [{ type: "text", text: "첫째 줄" }] },
            { type: "paragraph", content: [{ type: "text", text: "둘째 줄" }] },
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
          rows: [
            { id: 100, rowIndex: 0, cells: ["a1", "b1"] },
            { id: 101, rowIndex: 1, cells: ["a2", "b2"] },
          ],
          offset: 0,
          limit: 100,
        }),
      );
    if (pathname.endsWith("/merges")) return route.fulfill(ok({ merges: [] }));
    return route.fulfill(
      ok({
        id: 1,
        name: "표",
        columns: [
          { key: "c0", label: "A" },
          { key: "c1", label: "B" },
        ],
        rowCount: 2,
      }),
    );
  });
}

/**
 * 그 문단에 커서를 놓고, ProseMirror가 그 선택을 실제로 반영할 때까지 기다린다.
 * click()이 돌아온 시점엔 브라우저가 캐럿만 옮겼을 뿐이라, 바로 키를 누르면 에디터가 아직
 * 이전 선택(문서 맨 앞)을 보고 있어 엉뚱한 블록이 잡힌다.
 */
async function placeCursorIn(page: Page, text: string) {
  await page.getByText(text).click();
  await page.waitForFunction((t) => {
    const node = window.getSelection()?.anchorNode ?? null;
    const el = node instanceof Element ? node : node?.parentElement;
    return el?.closest("p")?.textContent === t;
  }, text);
  // selectionchange 핸들러가 돌 한 프레임을 준다.
  await page.evaluate(
    () => new Promise((r) => requestAnimationFrame(() => r(null))),
  );
}

const selectedBlocks = (page: Page) =>
  page.locator(".ProseMirror-selectednoderange").count();

/** 본문 문단 텍스트를 순서대로 — 복제·이동 결과를 눈으로 보듯 확인한다. */
const paragraphs = (page: Page) =>
  page.locator(".ProseMirror > p").allTextContents();

test.beforeEach(async ({ page }) => {
  await mockNote(page);
  await page.goto(`/notes?note=${NOTE_ID}`);
  await expect(page.getByLabel("노트 본문")).toBeVisible();
  await expect(page.getByTestId("dataset-grid")).toBeVisible();
});

const blockMenu = (page: Page) => page.getByRole("menu", { name: "블록 메뉴" });
const cellMenu = (page: Page) => page.getByRole("menu", { name: "셀 메뉴" });

/** 그 문단을 우클릭한다. */
async function rightClick(page: Page, text: string) {
  await page.getByText(text).click({ button: "right" });
}

test.describe("블록 우클릭 메뉴", () => {
  test("문단을 우클릭하면 그 블록이 선택되고 메뉴가 뜬다", async ({ page }) => {
    await rightClick(page, "둘째 줄");

    await expect(blockMenu(page)).toBeVisible();
    // 우클릭한 블록이 잡힌다 — 엉뚱한 블록에 조작이 먹으면 안 된다.
    await expect(page.locator(".ProseMirror-selectednoderange")).toHaveText(
      "둘째 줄",
    );
    await expect(blockMenu(page)).toContainText("블록 1개");
  });

  test("여러 블록을 고른 뒤 그 위에서 우클릭하면 선택이 유지된다", async ({
    page,
  }) => {
    await placeCursorIn(page, "첫째 줄");
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.press("ControlOrMeta+a");
    const count = await selectedBlocks(page);

    await rightClick(page, "둘째 줄");

    // 선택을 깨고 한 블록으로 좁히면 여러 블록 조작이 불가능해진다.
    expect(await selectedBlocks(page)).toBe(count);
    await expect(blockMenu(page)).toContainText(`블록 ${count}개`);
  });

  test("[복제]가 그 블록을 바로 아래에 복제한다", async ({ page }) => {
    await rightClick(page, "첫째 줄");
    await blockMenu(page).getByRole("menuitem", { name: /복제/ }).click();

    expect((await paragraphs(page)).slice(0, 3)).toEqual([
      "첫째 줄",
      "첫째 줄",
      "둘째 줄",
    ]);
    await expect(blockMenu(page)).toBeHidden();
  });

  test("[아래로 이동]이 뒤 블록과 자리를 바꾼다", async ({ page }) => {
    await rightClick(page, "첫째 줄");
    await blockMenu(page)
      .getByRole("menuitem", { name: /아래로 이동/ })
      .click();

    expect((await paragraphs(page)).slice(0, 2)).toEqual([
      "둘째 줄",
      "첫째 줄",
    ]);
  });

  test("[삭제]가 그 블록만 지운다", async ({ page }) => {
    await rightClick(page, "둘째 줄");
    await blockMenu(page).getByRole("menuitem", { name: "삭제" }).click();

    const texts = await paragraphs(page);
    expect(texts).toContain("첫째 줄");
    expect(texts).not.toContain("둘째 줄");
    await expect(page.getByTestId("dataset-grid")).toBeVisible();
  });

  test("바깥을 클릭하면 닫힌다", async ({ page }) => {
    await rightClick(page, "첫째 줄");
    await expect(blockMenu(page)).toBeVisible();

    await page.mouse.click(5, 5);

    await expect(blockMenu(page)).toBeHidden();
  });

  test("Esc로 닫으면 메뉴만 닫히고 블록 선택은 남는다", async ({ page }) => {
    await rightClick(page, "첫째 줄");
    await page.keyboard.press("Escape");

    await expect(blockMenu(page)).toBeHidden();
    // Esc 하강 사다리가 같이 돌아 선택까지 풀리면 안 된다.
    expect(await selectedBlocks(page)).toBe(1);
  });
});

test.describe("표와의 경계", () => {
  test("표 안 우클릭은 표 메뉴가 뜨고 블록 메뉴는 안 뜬다", async ({
    page,
  }) => {
    await page.getByText("a1").click({ button: "right" });

    await expect(cellMenu(page)).toBeVisible();
    await expect(blockMenu(page)).toBeHidden();
  });
});
