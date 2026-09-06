import { expect, test } from "./support/test";

const NOTE_ID = 1;

/**
 * 노트 화면까지 도달하는 데 필요한 API만 목킹한다.
 * (체크박스 레이아웃은 CSS라 jsdom에서 검증할 수 없어 실제 브라우저로 확인한다)
 *
 * dev는 VITE_API_URL=/api로 Vite 프록시를 타므로 호스트가 아닌 경로로 가로챈다.
 */
async function mockNoteApi(page: import("@playwright/test").Page) {
  await page.route("**/api/auth/login", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        code: "OK",
        data: { accessToken: "mock-access-token" },
      }),
    }),
  );

  // 액세스 토큰은 메모리에만 있어 페이지 이동(리로드) 시 사라진다.
  // 실제 앱과 같이 재발급으로 세션을 되살린다.
  await page.route("**/api/auth/reissue", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        code: "OK",
        data: { accessToken: "mock-access-token" },
      }),
    }),
  );

  await page.route("**/api/notes", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        code: "OK",
        data: {
          notes: [
            {
              id: NOTE_ID,
              title: "테스트 노트",
              parentId: null,
              sortOrder: 0,
              children: [],
            },
          ],
        },
      }),
    }),
  );

  await page.route(`**/api/notes/${NOTE_ID}`, (route) => {
    if (route.request().method() === "PATCH") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          code: "OK",
          data: {
            id: NOTE_ID,
            materialId: null,
            parentId: null,
            title: "테스트 노트",
            sortOrder: 0,
            updatedAt: "2026-01-01T00:00:00Z",
          },
        }),
      });
    }
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        code: "OK",
        data: {
          id: NOTE_ID,
          materialId: null,
          parentId: null,
          title: "테스트 노트",
          sortOrder: 0,
          content: { type: "doc", content: [{ type: "paragraph" }] },
          updatedAt: "2026-01-01T00:00:00Z",
        },
      }),
    });
  });
}

async function openNote(page: import("@playwright/test").Page) {
  // 재발급 목킹으로 세션이 서므로 로그인 화면을 거치지 않는다.
  await page.goto("/notes");
  await expect(page.getByLabel("노트 본문")).toBeVisible();
}

test.describe("노트 체크박스 목록", () => {
  test.beforeEach(async ({ page }) => {
    await mockNoteApi(page);
    await openNote(page);
  });

  test("'[] ' 입력 후 글자가 체크박스와 같은 줄에 들어간다", async ({
    page,
  }) => {
    await page.getByLabel("노트 본문").click();
    await page.keyboard.type("[] 우유 사기");

    const item = page.locator('ul[data-type="taskList"] > li').first();
    await expect(item).toBeVisible();

    const label = item.locator("label");
    const content = item.locator("> div");
    await expect(content).toHaveText("우유 사기");

    const labelBox = await label.boundingBox();
    const contentBox = await content.boundingBox();

    // 회귀 대상: div(block)가 label(inline) 뒤에서 줄바꿈돼 글자가 아래 줄에 찍히던 버그.
    expect(labelBox).not.toBeNull();
    expect(contentBox).not.toBeNull();
    expect(Math.abs(contentBox!.y - labelBox!.y)).toBeLessThan(6);
    expect(contentBox!.x).toBeGreaterThan(labelBox!.x);
  });

  test("체크박스가 첫 줄 글자의 중심에 정렬된다", async ({ page }) => {
    await page.getByLabel("노트 본문").click();
    await page.keyboard.type("[] 우유 사기");
    await expect(page.locator('ul[data-type="taskList"] > li')).toHaveCount(1);

    // 체크박스 중심과 첫 줄 글자 중심의 세로 차이(px). 양수면 체크박스가 아래로 치우침.
    const offset = await page.evaluate(() => {
      const li = document.querySelector('ul[data-type="taskList"] > li')!;
      const box = li
        .querySelector('input[type="checkbox"]')!
        .getBoundingClientRect();
      const textNode = li.querySelector(":scope > div > p")!.firstChild!;
      const range = document.createRange();
      range.setStart(textNode, 0);
      range.setEnd(textNode, 2);
      const text = range.getBoundingClientRect();
      return box.top + box.height / 2 - (text.top + text.height / 2);
    });

    // 회귀 대상: 문단 margin-top(0.15em)이 남아 체크박스가 위로 뜬 것처럼 보이던 문제.
    expect(Math.abs(offset)).toBeLessThan(1.5);
  });

  test("체크박스 목록엔 불릿 마커가 보이지 않는다", async ({ page }) => {
    await page.getByLabel("노트 본문").click();
    await page.keyboard.type("[] 우유 사기");

    const item = page.locator('ul[data-type="taskList"] > li').first();
    await expect(item).toBeVisible();
    await expect(item).toHaveCSS("list-style-type", "none");
  });

  test("체크하면 취소선이 표시된다", async ({ page }) => {
    await page.getByLabel("노트 본문").click();
    await page.keyboard.type("[] 우유 사기");

    const item = page.locator('ul[data-type="taskList"] > li').first();
    await item.locator('input[type="checkbox"]').click();

    await expect(item).toHaveAttribute("data-checked", "true");
    await expect(item.locator("> div")).toHaveCSS(
      "text-decoration-line",
      "line-through",
    );
  });

  test("중첩 항목도 체크박스와 글자가 같은 줄에 있다", async ({ page }) => {
    await page.getByLabel("노트 본문").click();
    await page.keyboard.type("[] 우유 사기");
    await page.keyboard.press("Enter");
    await page.keyboard.press("Tab");
    await page.keyboard.type("저지방으로");

    const nested = page
      .locator('ul[data-type="taskList"] ul[data-type="taskList"] > li')
      .first();
    await expect(nested).toBeVisible();

    const label = nested.locator("label");
    const content = nested.locator("> div");
    await expect(content).toHaveText("저지방으로");

    const labelBox = await label.boundingBox();
    const contentBox = await content.boundingBox();
    expect(Math.abs(contentBox!.y - labelBox!.y)).toBeLessThan(6);
    expect(contentBox!.x).toBeGreaterThan(labelBox!.x);
  });
});
