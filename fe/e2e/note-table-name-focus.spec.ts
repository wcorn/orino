import { expect, type Page, test } from "./support/test";

/**
 * 표 이름을 클릭해도 포커스가 첫 셀로 튀던 문제(#1017).
 *
 * 어느 요소가 실제로 포커스를 갖는지가 전부라 jsdom으로는 검증되지 않는다.
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
            { type: "datasetTable", attrs: { datasetId: 1 } },
          ],
        },
        updatedAt: "2026-01-01T00:00:00Z",
      }),
    );
  });
  await page.route("**/api/datasets/**", (route) => {
    const { pathname } = new URL(route.request().url());
    if (route.request().method() !== "GET") return route.fulfill(ok({}));
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
        name: "진도표",
        columns: [
          { key: "c0", label: "A" },
          { key: "c1", label: "B" },
        ],
        rowCount: 2,
      }),
    );
  });
}

/** 지금 포커스를 가진 요소의 aria-label(없으면 태그명). */
const focusedLabel = (page: Page) =>
  page.evaluate(() => {
    const el = document.activeElement;
    return el?.getAttribute("aria-label") ?? el?.tagName ?? "none";
  });

test.beforeEach(async ({ page }) => {
  await mockNote(page);
  await page.goto(`/notes?note=${NOTE_ID}`);
  await expect(page.getByTestId("dataset-grid")).toBeVisible();
});

test.describe("표 이름 포커스", () => {
  test("전체 블록 선택 뒤에도 표 이름을 클릭하면 이름에 포커스가 남는다", async ({
    page,
  }) => {
    await page.getByText("첫째 줄").click();
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.press("ControlOrMeta+a");

    await page.getByLabel("표 이름").click();

    // 회귀 대상: 그리드가 첫 셀을 잡아 이름 입력창에서 포커스를 빼앗던 문제.
    expect(await focusedLabel(page)).toBe("표 이름");
  });

  test("표 이름에서 Cmd+A → Cmd+C 로 이름이 복사된다", async ({ page }) => {
    await page.getByText("첫째 줄").click();
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.press("ControlOrMeta+a");

    await page.getByLabel("표 이름").click();
    await page.keyboard.press("ControlOrMeta+a");

    const sel = await page.evaluate(() => {
      const el = document.activeElement as HTMLInputElement | null;
      return { start: el?.selectionStart, end: el?.selectionEnd, v: el?.value };
    });
    expect(sel).toEqual({ start: 0, end: 3, v: "진도표" });
  });

  test("표 이름을 고칠 수 있다", async ({ page }) => {
    await page.getByText("첫째 줄").click();
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.press("ControlOrMeta+a");

    await page.getByLabel("표 이름").click();
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.type("새 이름");

    await expect(page.getByLabel("표 이름")).toHaveValue("새 이름");
  });

  test("표 바깥에서 표 블록을 고르면 기존대로 첫 셀이 잡힌다", async ({
    page,
  }) => {
    // 과교정 방지 — 자동 첫 셀 선택은 표 바깥에서 선택했을 때를 위한 기능이다.
    await page.getByText("첫째 줄").click();
    await page.keyboard.press("ArrowDown");

    await expect(page.getByLabel("1행 1열 셀 (입력하면 편집)")).toBeVisible();
  });
});
