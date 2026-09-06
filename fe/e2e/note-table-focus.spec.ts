import { expect, type Page, test } from "./support/test";

/**
 * 표가 있는 노트에서 Cmd+A → Backspace가 먹지 않던 회귀(#1008).
 *
 * 원인은 포커스 소유권이었다 — 문서 전체 선택이 표를 "덮으면" 그리드가 자기가 선택된 줄 알고
 * 첫 셀을 잡아, 셀 <input>이 DOM 포커스를 가져가 키 입력이 에디터 대신 셀로 샜다.
 * 포커스는 CSS/jsdom으로 재현되지 않아(활성 요소가 실제로 바뀌어야 한다) 실제 브라우저로 검증한다.
 * 행 데이터 로드 타이밍에 좌우돼 불규칙했으므로 반복 실행으로 안정성까지 본다.
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
            { type: "paragraph", content: [{ type: "text", text: "셋째 줄" }] },
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

async function openNote(page: Page) {
  await page.goto(`/notes?note=${NOTE_ID}`);
  await expect(page.getByLabel("노트 본문")).toBeVisible();
  await expect(page.getByTestId("dataset-grid")).toBeVisible();
}

/** 지금 키 입력을 받는 요소가 에디터인지 표의 셀 입력창인지. */
function focusOwner(page: Page) {
  return page.evaluate(() => {
    const el = document.activeElement;
    if (!el) return "none";
    if (el.classList.contains("ProseMirror")) return "editor";
    if (el.closest("[data-dataset-table]")) return "cell";
    return el.tagName.toLowerCase();
  });
}

test.describe("표가 있는 노트의 포커스 소유권", () => {
  test.beforeEach(async ({ page }) => {
    await mockNoteWithTable(page);
    await openNote(page);
  });

  test("전체 선택 후에도 포커스가 에디터에 남는다", async ({ page }) => {
    await page.getByText("첫째 줄").click();
    expect(await focusOwner(page)).toBe("editor");

    // Cmd+A는 점진 선택이라 두 번 눌러야 문서 전체가 잡힌다(#1011). 표를 덮는 건 두 번째다.
    await page.keyboard.press("ControlOrMeta+a");
    expect(await focusOwner(page)).toBe("editor");

    await page.keyboard.press("ControlOrMeta+a");
    // 회귀 대상: 전체 선택이 표를 덮자 그리드가 첫 셀을 잡아 포커스를 가져가던 문제.
    expect(await focusOwner(page)).toBe("editor");
  });

  test("전체 선택 → Backspace 로 표를 포함한 본문이 지워진다", async ({
    page,
  }) => {
    await page.getByText("첫째 줄").click();
    await page.keyboard.press("ControlOrMeta+a"); // 블록 안 텍스트
    await page.keyboard.press("ControlOrMeta+a"); // 문서 전체 블록
    await page.keyboard.press("Backspace");

    // 표 블록이 지워지면 dataset 정리 확인 창이 뜬다(고아 dataset 방지 경로).
    await expect(page.getByText("표(데이터셋)를 삭제할까요?")).toBeVisible();
    await expect(page.getByText("첫째 줄")).toBeHidden();
    await expect(page.getByText("셋째 줄")).toBeHidden();
  });

  test("표를 클릭하면 여전히 셀이 잡혀 바로 타이핑할 수 있다", async ({
    page,
  }) => {
    // 좁힌 조건이 과해서 표 단독 선택까지 막지 않았는지 확인한다.
    await page.getByText("a1").click();
    expect(await focusOwner(page)).toBe("cell");

    await page.keyboard.type("X");
    await expect(page.getByTestId("dataset-grid")).toContainText("X");
  });
});
