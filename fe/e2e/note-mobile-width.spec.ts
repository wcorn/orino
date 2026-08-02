import { expect, type Page, test } from "@playwright/test";

const NOTE_ID = 1;
const DATASET_ID = 1;

/**
 * 노트 본문에 표 블록 하나가 박힌 화면까지 도달하는 데 필요한 API만 목킹한다.
 * (좌우 여백은 CSS라 jsdom에서 폭을 잴 수 없어 실제 브라우저로 확인한다)
 *
 * dev는 VITE_API_URL=/api로 Vite 프록시를 타므로 호스트가 아닌 경로로 가로챈다.
 */
async function mockNoteWithTable(page: Page) {
  const ok = (data: unknown) => ({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({ code: "OK", data }),
  });

  // 액세스 토큰은 메모리에만 있어 페이지 이동 시 사라진다. 실제 앱처럼 재발급으로 세션을 세운다.
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

  await page.route(`**/api/notes/${NOTE_ID}`, (route) =>
    route.fulfill(
      ok({
        id: NOTE_ID,
        materialId: null,
        parentId: null,
        title: "테스트 노트",
        sortOrder: 0,
        content: {
          type: "doc",
          content: [
            { type: "paragraph", content: [{ type: "text", text: "본문" }] },
            { type: "datasetTable", attrs: { datasetId: DATASET_ID } },
          ],
        },
        updatedAt: "2026-01-01T00:00:00Z",
      }),
    ),
  );

  // 표 조회 3종. 등록 순서에 따른 우선순위 함정을 피하려고 한 핸들러에서 경로로 갈라준다.
  await page.route("**/api/datasets/**", (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith("/rows")) {
      return route.fulfill(
        ok({
          rows: [
            { id: 100, rowIndex: 0, cells: ["네트워크", "1주차"] },
            { id: 101, rowIndex: 1, cells: ["OS", "2주차"] },
          ],
          offset: 0,
          limit: 100,
        }),
      );
    }
    if (pathname.endsWith("/merges")) return route.fulfill(ok({ merges: [] }));
    return route.fulfill(
      ok({
        id: DATASET_ID,
        columns: [
          { key: "c0", label: "과목" },
          { key: "c1", label: "일정" },
        ],
        rowCount: 2,
      }),
    );
  });
}

async function openNote(page: Page) {
  await page.goto(`/notes?note=${NOTE_ID}`);
  await expect(page.getByLabel("노트 본문")).toBeVisible();
  await expect(page.getByTestId("dataset-grid")).toBeVisible();
}

/**
 * 모바일에서 좌우 여백이 4겹(페이지 p-6 + 카드 테두리 + 본문 pl-10/pr-4 + 표 거터)으로 쌓여
 * 390px 중 126px이 여백이던 회귀를 잡는다. 폭이 곧 읽을 수 있는 열 수라 표에 특히 치명적이었다.
 */
test.describe("모바일 노트 좌우 여백", () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test.beforeEach(async ({ page }) => {
    await mockNoteWithTable(page);
    await openNote(page);
  });

  test("표가 뷰포트 폭의 80% 이상을 쓴다", async ({ page }) => {
    const grid = await page.getByTestId("dataset-grid").boundingBox();
    expect(grid).not.toBeNull();

    // 수정 전 264/390 = 68%. 수정 후 336/390 = 86%.
    expect(grid!.width).toBeGreaterThanOrEqual(390 * 0.8);
  });

  test("표를 넓혀도 페이지가 가로로 넘치지 않는다", async ({ page }) => {
    // 표 full-bleed는 본문 패딩을 음수 마진으로 상쇄하는 방식이라, 짝이 어긋나면
    // 표가 카드 밖으로 삐져나와 페이지 전체에 가로 스크롤이 생긴다.
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - window.innerWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
  });

  test("블록 이동(⣿) 핸들은 모바일에서 보이지 않는다", async ({ page }) => {
    // hover로만 뜨는 UI라 터치에선 어차피 못 쓴다. 왼쪽 거터를 없앤 만큼 뜨면 글자와 겹친다.
    // 본문 루트가 아니라 문단 위에 올려야 핸들이 실제로 뜬다(빈 영역엔 안 뜸) — 수정 전
    // 상태에서 이 테스트가 실패하는지로 확인했다.
    await page.locator(".ProseMirror > p").first().hover();
    await expect(page.getByRole("button", { name: "블록 이동" })).toBeHidden();
  });
});

/** 데스크탑은 기존 레이아웃 그대로여야 한다(모바일 전용 변경이 넘어오지 않았는지 확인). */
test.describe("데스크탑 노트 좌우 여백", () => {
  test.use({ viewport: { width: 1280, height: 800 } });

  test.beforeEach(async ({ page }) => {
    await mockNoteWithTable(page);
    await openNote(page);
  });

  test("표가 본문 글과 같은 좌측선에서 시작한다(full-bleed 아님)", async ({
    page,
  }) => {
    const paragraph = await page
      .locator(".ProseMirror > p")
      .first()
      .boundingBox();
    const grid = await page.getByTestId("dataset-grid").boundingBox();

    expect(paragraph).not.toBeNull();
    expect(grid).not.toBeNull();
    expect(Math.abs(grid!.x - paragraph!.x)).toBeLessThan(2);
  });

  test("블록 이동(⣿) 핸들 자리(왼쪽 거터)가 유지된다", async ({ page }) => {
    await page.locator(".ProseMirror > p").first().hover();
    const handle = page.getByRole("button", { name: "블록 이동" });
    await expect(handle).toBeVisible();

    // 핸들은 본문 글 왼쪽 거터 안에 떠야 한다(글자를 가리지 않는다).
    const handleBox = await handle.boundingBox();
    const paragraph = await page
      .locator(".ProseMirror > p")
      .first()
      .boundingBox();
    expect(handleBox!.x + handleBox!.width).toBeLessThanOrEqual(
      paragraph!.x + 1,
    );
  });
});
