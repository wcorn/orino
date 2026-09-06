import { expect, type Page, test } from "./support/test";

/**
 * 표가 든 블록을 복사·붙여넣기(#1019).
 *
 * 표는 노트 문서가 아니라 별도 dataset에 살아서, 클립보드로 나갈 때 "표 모양"으로 바꿔 담고
 * 들어올 때 그 모양으로 새 표를 만든다. 클립보드 왕복은 실제 브라우저에서만 검증된다.
 */

const NOTE_ID = 1;

const ok = (data: unknown) => ({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ code: "OK", data }),
});

/** 붙여넣기로 만들어지는 표에 순서대로 부여할 id. */
let nextDatasetId = 2;
/** 생성된 dataset의 행 — 붙여넣은 내용이 실제로 담겼는지 확인한다. */
let created: Record<number, { columns: string[]; rows: string[][] }> = {};

async function mockNote(page: Page) {
  nextDatasetId = 2;
  created = {};

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

  // 정규식으로 정확히 API 경로만 잡는다. `**/api/datasets**` 같은 글롭은 소스 모듈
  // (dataset/api/datasets.ts)까지 가로채 dev 서버가 500을 낸다.
  await page.route(/\/api\/datasets(\/|\?|$)/, async (route) => {
    const req = route.request();
    const { pathname } = new URL(req.url());
    const idMatch = pathname.match(/\/datasets\/(\d+)/);
    const id = idMatch ? Number(idMatch[1]) : null;

    // 표 생성 — 새 id를 발급하고 열을 기억한다.
    if (req.method() === "POST" && pathname.endsWith("/datasets")) {
      const body = JSON.parse(req.postData() ?? "{}");
      const newId = nextDatasetId++;
      created[newId] = {
        columns: body.columns.map((c: { label: string }) => c.label),
        rows: [],
      };
      return route.fulfill(
        ok({ id: newId, name: null, columns: body.columns, rowCount: 0 }),
      );
    }
    // 행 벌크 추가
    if (req.method() === "POST" && pathname.endsWith("/rows/bulk")) {
      const body = JSON.parse(req.postData() ?? "{}");
      if (id != null && created[id]) created[id].rows.push(...body.rows);
      return route.fulfill(
        ok({
          id,
          name: null,
          columns: [],
          rowCount: created[id!]?.rows.length ?? 0,
        }),
      );
    }
    if (req.method() !== "GET") return route.fulfill(ok({}));

    if (pathname.endsWith("/rows")) {
      if (id === 1)
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
      const rows = created[id!]?.rows ?? [];
      return route.fulfill(
        ok({
          rows: rows.map((cells, i) => ({ id: 900 + i, rowIndex: i, cells })),
          offset: 0,
          limit: 100,
        }),
      );
    }
    if (pathname.endsWith("/merges")) return route.fulfill(ok({ merges: [] }));

    if (id === 1)
      return route.fulfill(
        ok({
          id: 1,
          name: "진도표",
          columns: [
            { key: "c0", label: "과목" },
            { key: "c1", label: "상태" },
          ],
          rowCount: 2,
        }),
      );
    const meta = created[id!];
    return route.fulfill(
      ok({
        id,
        name: null,
        columns: (meta?.columns ?? []).map((label, i) => ({
          key: `c${i}`,
          label,
        })),
        rowCount: meta?.rows.length ?? 0,
      }),
    );
  });
}

const clipText = (page: Page) =>
  page.evaluate(() => navigator.clipboard.readText());

test.use({ permissions: ["clipboard-read", "clipboard-write"] });

test.beforeEach(async ({ page }) => {
  await mockNote(page);
  await page.goto(`/notes?note=${NOTE_ID}`);
  await expect(page.getByTestId("dataset-grid")).toBeVisible();
});

test.describe("표가 든 블록 복사", () => {
  test("텍스트로는 마크다운 표가 담긴다", async ({ page }) => {
    await page.getByText("첫째 줄").click();
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.press("ControlOrMeta+c");

    const text = await clipText(page);
    // 회귀 대상: 표 자리가 빈 줄로만 나가던 문제.
    expect(text).toContain("진도표");
    expect(text).toContain("| 과목 | 상태 |");
    expect(text).toContain("| a1 | b1 |");
    expect(text).toContain("| a2 | b2 |");
  });
});

test.describe("표가 든 블록 붙여넣기", () => {
  test("전체를 복사해 덮어써도 표가 살아남는다(원본과 별개인 새 표)", async ({
    page,
  }) => {
    await page.getByText("첫째 줄").click();
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.press("ControlOrMeta+c");
    await page.keyboard.press("ControlOrMeta+v");

    // 회귀 대상: 붙여넣기가 표를 통째로 없애 버리던 문제(전체 선택을 덮어쓰므로 1개가 맞다).
    await expect(page.getByTestId("dataset-grid")).toHaveCount(1);

    // 원본 값이 그대로 담긴 "새" 표가 만들어졌다.
    await expect
      .poll(() => Object.values(created).map((d) => d.rows))
      .toContainEqual([
        ["a1", "b1"],
        ["a2", "b2"],
      ]);
    // 원본 id를 재사용하지 않는다 — 재사용하면 한쪽을 지울 때 둘 다 사라진다.
    expect(Object.keys(created).map(Number)).not.toContain(1);
  });

  test("커서 위치에 붙여넣으면 표가 하나 더 생긴다", async ({ page }) => {
    await page.getByText("첫째 줄").click();
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.press("ControlOrMeta+c");

    // 선택을 풀고 커서만 둔 뒤 붙여넣는다 → 덮어쓰지 않고 끼워 넣는다.
    await page.getByText("첫째 줄").click();
    await page.keyboard.press("ControlOrMeta+v");

    await expect(page.getByTestId("dataset-grid")).toHaveCount(2);
  });

  test("외부(엑셀·노션)에서 복사한 표를 붙여넣으면 그리드가 된다", async ({
    page,
  }) => {
    await page.getByText("첫째 줄").click();

    // 외부 앱이 주는 형태 — data-dataset-id 없이 순수 <table>.
    await page.evaluate(async () => {
      const html =
        "<table><tr><th>이름</th><th>점수</th></tr><tr><td>네트워크</td><td>92</td></tr></table>";
      await navigator.clipboard.write([
        new ClipboardItem({
          "text/html": new Blob([html], { type: "text/html" }),
          "text/plain": new Blob(["이름\t점수\n네트워크\t92"], {
            type: "text/plain",
          }),
        }),
      ]);
    });
    await page.keyboard.press("ControlOrMeta+v");

    await expect(page.getByTestId("dataset-grid")).toHaveCount(2);
    await expect
      .poll(() => Object.values(created).map((d) => d.columns))
      .toContainEqual(["이름", "점수"]);
    await expect
      .poll(() => Object.values(created).map((d) => d.rows))
      .toContainEqual([["네트워크", "92"]]);
  });
});
