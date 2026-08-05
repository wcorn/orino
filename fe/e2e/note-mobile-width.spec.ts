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
 *
 * #1023에서 한 겹 더 나아가 여백을 페이지 p-4 하나로 줄이고, 남아 있던 "정렬" 문제까지 잡았다.
 * 예전엔 표만 음수 마진으로 넓혀 왼쪽만 당겨졌고(문단과 12px 어긋남) 오른쪽은 그리드 거터가
 * 남아 좌우가 비대칭이었다. 이제 제목·툴바·문단·표가 한 좌측선을 공유한다(Notion 모바일).
 */
test.describe("모바일 노트 좌우 여백", () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test.beforeEach(async ({ page }) => {
    await mockNoteWithTable(page);
    await openNote(page);
  });

  test("제목·툴바·문단·표가 모두 같은 좌측선에서 시작한다", async ({
    page,
  }) => {
    const left = async (locator: ReturnType<Page["locator"]>) => {
      const box = await locator.boundingBox();
      expect(box).not.toBeNull();
      return box!.x;
    };

    const title = await left(page.getByLabel("노트 제목"));
    const toolbar = await left(page.getByRole("toolbar"));
    const paragraph = await left(page.locator(".ProseMirror > p").first());
    const grid = await left(page.getByTestId("dataset-grid"));

    // 페이지 여백(p-4=16px) 한 겹만 남아야 한다.
    expect(title).toBe(16);
    for (const x of [toolbar, paragraph, grid]) {
      expect(Math.abs(x - title)).toBeLessThan(2);
    }
  });

  test("표가 뷰포트 폭의 80% 이상을 쓴다", async ({ page }) => {
    const grid = await page.getByTestId("dataset-grid").boundingBox();
    expect(grid).not.toBeNull();

    // #1007 이전 264/390 = 68%. 지금은 338/390 = 87%
    // (오른쪽 20px은 열 추가 바 자리 — 모바일에선 상시 보이는 버튼이다).
    expect(grid!.width).toBeGreaterThanOrEqual(390 * 0.8);
  });

  test("페이지가 가로로 넘치지 않는다", async ({ page }) => {
    // 본문이 카드 없이 페이지 여백만 쓰므로, 어느 블록이든 그 폭을 넘기면 곧장
    // 페이지 전체에 가로 스크롤이 생긴다(표는 자기 안에서 스크롤해야 한다).
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - window.innerWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
  });

  test("툴바가 한 줄이고 넘치면 가로 스크롤한다", async ({ page }) => {
    // flex-wrap이면 버튼 14개가 2줄로 접혀 좁은 화면 상단을 크게 잡아먹는다.
    const ys = await page
      .getByRole("toolbar")
      .getByRole("button")
      .evaluateAll((els) =>
        els.map((el) => Math.round(el.getBoundingClientRect().y)),
      );
    expect(ys.length).toBeGreaterThan(5);
    expect(new Set(ys).size).toBe(1);

    // 한 줄에 다 안 들어가므로 잘린 버튼은 스크롤로 닿아야 한다.
    const scrollable = await page
      .getByRole("toolbar")
      .evaluate((el) => el.scrollWidth > el.clientWidth);
    expect(scrollable).toBe(true);
  });

  test("행·열 추가 바가 모바일에선 상시 보인다", async ({ page }) => {
    // hover 전용이면 터치에선 영영 안 떠서 행·열을 늘릴 방법이 사라지고, 그 자리(거터)는
    // 기능 없는 죽은 여백으로만 남는다. Playwright의 toBeVisible은 opacity를 보지 않으므로
    // 계산된 opacity를 직접 확인한다.
    for (const name of ["행 추가", "열 추가"]) {
      const opacity = await page
        .getByRole("button", { name })
        .evaluate((el) => getComputedStyle(el).opacity);
      expect(opacity, `${name} 바가 모바일에서 보여야 한다`).toBe("1");
    }
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

  test("본문 카드(테두리·배경)가 유지된다", async ({ page }) => {
    // 카드 제거는 모바일 전용이다. 데스크탑은 폭이 남아 카드가 본문 영역을 구분해 준다.
    const card = await page.getByLabel("노트 본문").evaluate((el) => {
      const wrapper = el.parentElement?.parentElement;
      if (!wrapper) return null;
      const s = getComputedStyle(wrapper);
      return { borderWidth: s.borderTopWidth, radius: s.borderTopLeftRadius };
    });
    expect(card).not.toBeNull();
    expect(parseFloat(card!.borderWidth)).toBeGreaterThan(0);
    expect(parseFloat(card!.radius)).toBeGreaterThan(0);
  });

  test("행·열 추가 바는 호버 전엔 보이지 않는다", async ({ page }) => {
    // 모바일 상시 표시가 데스크탑까지 넘어오면 표 옆에 늘 회색 바가 붙어 시끄럽다.
    for (const name of ["행 추가", "열 추가"]) {
      const opacity = await page
        .getByRole("button", { name })
        .evaluate((el) => getComputedStyle(el).opacity);
      expect(opacity, `${name} 바는 데스크탑에서 호버 전 숨어야 한다`).toBe(
        "0",
      );
    }
  });
});
