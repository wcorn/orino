import { expect, type Page, test } from "@playwright/test";

/**
 * 블록 선택 레이어 + Cmd+A 점진 선택(#1011).
 *
 * 선택은 실제 DOM 선택·포커스·데코레이션이 얽혀 있어 jsdom으로는 끝까지 못 본다
 * (특히 표 안에서 밖으로 넘어가는 포커스 이동). 실제 브라우저로 검증한다.
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

/** 블록 선택으로 잡힌 블록 수. 0이면 블록 레이어가 안 잡힌 것. */
const selectedBlocks = (page: Page) =>
  page.locator(".ProseMirror-selectednoderange").count();

/** 지금 키를 받는 곳이 에디터인지 표의 셀인지. */
const focusOwner = (page: Page) =>
  page.evaluate(() => {
    const el = document.activeElement;
    if (!el) return "none";
    if (el.classList.contains("ProseMirror")) return "editor";
    if (el.closest("[data-dataset-table]")) return "cell";
    return el.tagName.toLowerCase();
  });

const selectedText = (page: Page) =>
  page.evaluate(() => window.getSelection()?.toString() ?? "");

/**
 * 본문의 최상위 블록 수. 표 뒤에는 ProseMirror가 후행 문단을 자동으로 두므로 상수로 박지 않고
 * 실제 개수와 비교한다("모든 블록이 잡혔다"가 검증하려는 것이다).
 */
const topLevelBlocks = (page: Page) => page.locator(".ProseMirror > *").count();

test.beforeEach(async ({ page }) => {
  await mockNote(page);
  await page.goto(`/notes?note=${NOTE_ID}`);
  await expect(page.getByLabel("노트 본문")).toBeVisible();
  await expect(page.getByTestId("dataset-grid")).toBeVisible();
});

test.describe("본문 Cmd+A 사다리", () => {
  test("1번째는 커서가 놓인 블록만, 2번째에 문서 전체", async ({ page }) => {
    await page.getByText("첫째 줄").click();

    await page.keyboard.press("ControlOrMeta+a");
    // 블록 안 텍스트만 — 아직 블록 레이어로 올라가지 않는다.
    expect(await selectedText(page)).toBe("첫째 줄");
    expect(await selectedBlocks(page)).toBe(0);

    await page.keyboard.press("ControlOrMeta+a");
    expect(await selectedBlocks(page)).toBe(await topLevelBlocks(page));
  });

  test("전체 블록 선택 후 Backspace로 본문이 지워진다", async ({ page }) => {
    await page.getByText("첫째 줄").click();
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.press("Backspace");

    await expect(page.getByText("표(데이터셋)를 삭제할까요?")).toBeVisible();
    await expect(page.getByText("첫째 줄")).toBeHidden();
  });
});

test.describe("표에서의 사다리 — 셀 → 표 → 문서", () => {
  test("표 안 첫 Cmd+A는 표에 머물고, 한 번 더 누르면 문서로 탈출한다", async ({
    page,
  }) => {
    await page.getByText("a1").click();
    expect(await focusOwner(page)).toBe("cell");

    // 1번째 — 표 전체. 본문 블록으로 새지 않는다.
    await page.keyboard.press("ControlOrMeta+a");
    expect(await selectedBlocks(page)).toBe(0);

    // 2번째 — 표 밖으로. 포커스도 에디터로 돌아와야 이후 키가 먹는다.
    await page.keyboard.press("ControlOrMeta+a");
    expect(await selectedBlocks(page)).toBe(await topLevelBlocks(page));
    expect(await focusOwner(page)).toBe("editor");
  });

  test("표에서 탈출한 뒤 Backspace가 본문에 먹는다", async ({ page }) => {
    await page.getByText("a1").click();
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.press("Backspace");

    await expect(page.getByText("표(데이터셋)를 삭제할까요?")).toBeVisible();
  });
});

test.describe("드래그 정책 (Notion식)", () => {
  test("블록을 넘나드는 드래그는 블록 선택으로 승격된다", async ({ page }) => {
    const first = (await page.getByText("첫째 줄").boundingBox())!;
    const second = (await page.getByText("둘째 줄").boundingBox())!;

    await page.mouse.move(first.x + 5, first.y + first.height / 2);
    await page.mouse.down();
    await page.mouse.move(second.x + 40, second.y + second.height / 2, {
      steps: 10,
    });
    await page.mouse.up();

    expect(await selectedBlocks(page)).toBeGreaterThanOrEqual(2);
  });

  test("한 문단 안 드래그는 부분 텍스트 선택으로 남는다(서식 적용 경로)", async ({
    page,
  }) => {
    // 문단 요소의 박스는 블록이라 글자보다 훨씬 넓다(오른쪽은 빈 공간). 그 비율로 끌면
    // 글자 바깥에서 드래그가 시작·종료돼 선택이 안 잡힌다. 글자가 실제로 차지한 영역을 잰다.
    const rect = await page.evaluate(() => {
      const p = [...document.querySelectorAll(".ProseMirror > p")].find(
        (el) => el.textContent === "첫째 줄",
      )!;
      const range = document.createRange();
      range.selectNodeContents(p);
      const r = range.getBoundingClientRect();
      return { x: r.x, y: r.y, width: r.width, height: r.height };
    });

    const y = rect.y + rect.height / 2;
    await page.mouse.move(rect.x + 1, y);
    await page.mouse.down();
    await page.mouse.move(rect.x + rect.width * 0.8, y, { steps: 12 });
    await page.mouse.up();

    // 선택 반영이 한 틱 늦을 수 있어 폴링으로 확인한다.
    await expect.poll(() => selectedText(page)).not.toBe("");
    expect(await selectedBlocks(page)).toBe(0);
  });
});
