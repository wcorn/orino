import { expect, type Page, test } from "./support/test";

/**
 * 블록 전체 선택 시 표만 선택 안 된 것처럼 보이던 문제(#1021).
 *
 * 그리드는 자기 배경이 불투명해 블록 선택 하이라이트를 가린다. "다른 블록과 같은 색으로 보이는가"가
 * 검증 대상이라 실제로 그려진 픽셀을 비교한다 — 클래스 유무로는 이 성질을 확인할 수 없다.
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
  await page.route(/\/api\/datasets(\/|\?|$)/, (route) => {
    const { pathname } = new URL(route.request().url());
    if (route.request().method() !== "GET") return route.fulfill(ok({}));
    if (pathname.endsWith("/rows"))
      return route.fulfill(
        ok({
          rows: [{ id: 100, rowIndex: 0, cells: ["a1", "b1"] }],
          offset: 0,
          limit: 100,
        }),
      );
    if (pathname.endsWith("/merges")) return route.fulfill(ok({ merges: [] }));
    return route.fulfill(
      ok({
        id: 1,
        name: null,
        columns: [
          { key: "c0", label: "A" },
          { key: "c1", label: "B" },
        ],
        rowCount: 1,
      }),
    );
  });
}

/**
 * 문단 안쪽과 표 셀 안쪽에 실제로 그려진 색을 읽는다.
 * 표본은 가장자리(테두리·여백)를 피해 각 영역 안쪽에서 뽑는다.
 */
async function sampleColors(page: Page) {
  const points = await page.evaluate(() => {
    const p = document
      .querySelector(".ProseMirror > p")!
      .getBoundingClientRect();
    const g = document
      .querySelector('[data-testid="dataset-grid"]')!
      .getBoundingClientRect();
    return {
      paragraph: {
        x: Math.round(p.x + p.width / 2),
        y: Math.round(p.y + p.height / 2),
      },
      cell: { x: Math.round(g.x + g.width / 2), y: Math.round(g.y + 12) },
    };
  });
  const shot = (await page.screenshot()).toString("base64");
  return page.evaluate(
    async ({ b64, points }) => {
      const img = new Image();
      img.src = "data:image/png;base64," + b64;
      await img.decode();
      const canvas = document.createElement("canvas");
      canvas.width = img.width;
      canvas.height = img.height;
      const ctx = canvas.getContext("2d")!;
      ctx.drawImage(img, 0, 0);
      const at = (pt: { x: number; y: number }) =>
        Array.from(ctx.getImageData(pt.x, pt.y, 1, 1).data)
          .slice(0, 3)
          .join(",");
      return { paragraph: at(points.paragraph), cell: at(points.cell) };
    },
    { b64: shot, points },
  );
}

test.beforeEach(async ({ page }) => {
  await mockNote(page);
  await page.goto(`/notes?note=${NOTE_ID}`);
  await expect(page.getByTestId("dataset-grid")).toBeVisible();
});

test.describe("블록 선택 시 표도 선택돼 보인다", () => {
  test("표가 선택된 문단과 같은 색으로 칠해진다", async ({ page }) => {
    const before = await sampleColors(page);
    // 선택 전엔 문단과 표가 각자 자기 배경색이다.
    expect(before.paragraph).not.toBe("235,225,250");

    await page.getByText("첫째 줄").click();
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.press("ControlOrMeta+a");

    const after = await sampleColors(page);
    // 회귀 대상: 표만 자기 배경(흰색) 그대로라 안 잡힌 것처럼 보이던 문제.
    expect(after.cell).toBe(after.paragraph);
    expect(after.cell).not.toBe(before.cell);
  });

  test("선택을 풀면 표 색도 돌아온다", async ({ page }) => {
    const before = await sampleColors(page);

    await page.getByText("첫째 줄").click();
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.press("Escape");

    const after = await sampleColors(page);
    expect(after.cell).toBe(before.cell);
  });

  test("표만 클릭했을 땐 전체 틴트를 씌우지 않는다", async ({ page }) => {
    // 그땐 셀 단위 UI(활성 셀)가 뜨므로 표 전체를 덮으면 시끄럽다.
    await page.getByText("a1").click();

    await expect(page.getByTestId("dataset-block-selected")).toBeHidden();
  });

  test("틴트가 셀 클릭을 막지 않는다", async ({ page }) => {
    await page.getByText("첫째 줄").click();
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.press("ControlOrMeta+a");
    await expect(page.getByTestId("dataset-block-selected")).toBeVisible();

    await page.getByText("a1").click();

    // 덮개가 클릭을 먹으면 셀이 안 잡힌다(pointer-events 회귀 방지).
    await expect(page.getByLabel("1행 1열 셀 (입력하면 편집)")).toBeVisible();
  });
});
