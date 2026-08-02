import { expect, type Page, test } from "@playwright/test";

/**
 * Esc 하강 사다리 + 블록 복제·이동(#1013).
 *
 * 표는 자체 단축키를 갖고 있고 포커스가 셀 입력창에 있는데, 셀 입력창이 에디터 DOM 안이라
 * 키가 본문까지 올라온다. 이 "가드가 실제로 먹는지"는 진짜 포커스가 있어야 검증되므로
 * jsdom이 아닌 실제 브라우저로 본다.
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

test.describe("Esc 하강 사다리", () => {
  test("편집 → Esc → 현재 블록 선택 → Esc → 커서 복귀", async ({ page }) => {
    await placeCursorIn(page, "첫째 줄");

    await page.keyboard.press("Escape");
    expect(await selectedBlocks(page)).toBe(1);

    await page.keyboard.press("Escape");
    expect(await selectedBlocks(page)).toBe(0);

    // 커서가 돌아왔으니 바로 타이핑이 이어진다.
    await page.keyboard.type("X");
    expect(await paragraphs(page)).toContain("X첫째 줄");
  });
});

test.describe("블록 복제·이동", () => {
  test("Cmd+D 는 커서가 놓인 블록을 바로 아래에 복제한다", async ({ page }) => {
    await placeCursorIn(page, "첫째 줄");

    await page.keyboard.press("ControlOrMeta+d");

    expect((await paragraphs(page)).slice(0, 3)).toEqual([
      "첫째 줄",
      "첫째 줄",
      "둘째 줄",
    ]);
  });

  test("Cmd+Shift+↓ 로 블록을 아래로 옮긴다", async ({ page }) => {
    await placeCursorIn(page, "첫째 줄");

    await page.keyboard.press("ControlOrMeta+Shift+ArrowDown");

    expect((await paragraphs(page)).slice(0, 2)).toEqual([
      "둘째 줄",
      "첫째 줄",
    ]);
  });

  test("Esc로 블록을 고른 뒤 Backspace로 그 블록만 지운다", async ({
    page,
  }) => {
    await placeCursorIn(page, "둘째 줄");
    await page.keyboard.press("Escape");
    // 고른 게 정말 그 블록인지 먼저 못박는다 — 엉뚱한 블록이 지워지면 여기서 걸린다.
    await expect(page.locator(".ProseMirror-selectednoderange")).toHaveText(
      "둘째 줄",
    );

    await page.keyboard.press("Backspace");

    const texts = await paragraphs(page);
    expect(texts).toContain("첫째 줄");
    expect(texts).not.toContain("둘째 줄");
    // 표는 그대로다 — 고른 블록만 지워져야 한다.
    await expect(page.getByTestId("dataset-grid")).toBeVisible();
  });
});

test.describe("표 안에서는 본문 블록이 반응하지 않는다", () => {
  test("셀에서 Esc를 눌러도 본문 블록이 선택되지 않는다", async ({ page }) => {
    await page.getByText("a1").click();

    await page.keyboard.press("Escape");

    expect(await selectedBlocks(page)).toBe(0);
  });

  test("셀에서 Cmd+D를 눌러도 본문 블록이 복제되지 않는다", async ({
    page,
  }) => {
    await page.getByText("a1").click();
    const before = await paragraphs(page);

    await page.keyboard.press("ControlOrMeta+d");

    expect(await paragraphs(page)).toEqual(before);
  });

  test("셀에서 Cmd+Shift+↓를 눌러도 본문 블록이 움직이지 않는다", async ({
    page,
  }) => {
    await page.getByText("a1").click();
    const before = await paragraphs(page);

    await page.keyboard.press("ControlOrMeta+Shift+ArrowDown");

    expect(await paragraphs(page)).toEqual(before);
  });
});
