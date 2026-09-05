import { expect, type Page, test } from "@playwright/test";

/**
 * 준비 화면 한 바퀴(S-10, #1324).
 *
 * <p>여기서 확인하는 것은 <b>출발 전날 밤의 한 흐름</b>이다 — 연달아 적고, 훑으면서 체크하고,
 * 다 한 것은 숨기고, 잘못 적은 것은 지웠다가 되돌린다. 통합 테스트가 각 조각을 보는 것과 달리
 * 이 스펙은 그 조각들이 <b>한 화면에서 이어지는지</b>를 본다.
 */

const TRIP_ID = 1;

const ok = (data: unknown) => ({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ code: "OK", data }),
});

type PrepCategory = "DOCUMENT" | "BOOKING" | "BAG" | "TODO";
const CATEGORIES: PrepCategory[] = ["DOCUMENT", "BOOKING", "BAG", "TODO"];

interface StoredItem {
  id: number;
  category: PrepCategory;
  title: string;
  done: boolean;
  quantity: number | null;
  dueDaysBefore: number | null;
  dueDate: string | null;
  overdue: boolean;
  url: string | null;
  memo: string | null;
  displayOrder: number;
}

/**
 * 서버를 흉내내되 <b>상태를 들고 있는다</b>. 준비는 적고 → 체크하고 → 지우는 흐름이 이어져야
 * 성립하는 화면이라, 고정 응답으로는 「추가했는데 목록에 없다」를 못 잡는다.
 */
async function mockPrep(page: Page) {
  const items: StoredItem[] = [];
  let nextId = 100;

  await page.route("**/api/auth/reissue", (route) =>
    route.fulfill(ok({ accessToken: "mock-access-token" })),
  );
  await page.route("**/api/travel/summary", (route) =>
    route.fulfill(ok({ ongoing: null, next: null, recentCompleted: null })),
  );
  await page.route("**/api/planner/reviews/summary", (route) =>
    route.fulfill(
      ok({
        today: "2026-09-05",
        counts: { now: 0, overdue: 0, upcoming: 0, doneToday: 0 },
        estimatedMinutes: 0,
        materials: [],
      }),
    ),
  );

  /** 저장본에서 서버 응답 모양만 남긴다 — `category`는 항목이 아니라 그룹이 갖는다. */
  const view = (stored: StoredItem): Omit<StoredItem, "category"> => ({
    id: stored.id,
    title: stored.title,
    done: stored.done,
    quantity: stored.quantity,
    dueDaysBefore: stored.dueDaysBefore,
    dueDate: stored.dueDate,
    overdue: stored.overdue,
    url: stored.url,
    memo: stored.memo,
    displayOrder: stored.displayOrder,
  });
  const summary = () => ({
    total: items.length,
    done: items.filter((i) => i.done).length,
    overdueCount: items.filter((i) => i.overdue && !i.done).length,
  });

  await page.route(
    `**/api/travel/trips/${TRIP_ID}/prep/items`,
    async (route) => {
      const body = route.request().postDataJSON() as {
        category?: PrepCategory;
        title: string;
      };
      const category = body.category ?? "TODO";
      const stored: StoredItem = {
        id: nextId++,
        category,
        title: body.title,
        done: false,
        quantity: null,
        dueDaysBefore: null,
        dueDate: null,
        overdue: false,
        url: null,
        memo: null,
        displayOrder: items.filter((i) => i.category === category).length,
      };
      items.push(stored);
      return route.fulfill(
        ok({ category, item: view(stored), summary: summary() }),
      );
    },
  );

  await page.route("**/api/travel/prep/items/*", async (route) => {
    const id = Number(route.request().url().split("/").pop());
    const index = items.findIndex((i) => i.id === id);

    if (route.request().method() === "DELETE") {
      items.splice(index, 1);
      return route.fulfill(ok(null));
    }

    const body = route.request().postDataJSON() as {
      done?: boolean;
      title?: string;
      category?: PrepCategory;
      dueDaysBefore?: number;
      quantity?: number;
      clear?: string[];
    };
    const stored = items[index];
    if (body.done !== undefined) stored.done = body.done;
    if (body.title !== undefined) stored.title = body.title;
    if (body.category !== undefined) stored.category = body.category;
    if (body.dueDaysBefore !== undefined) {
      stored.dueDaysBefore = body.dueDaysBefore;
      stored.dueDate = "2026-10-10";
    }
    if (body.quantity !== undefined) stored.quantity = body.quantity;
    if (body.clear?.includes("DUE_DAYS_BEFORE")) {
      stored.dueDaysBefore = null;
      stored.dueDate = null;
      stored.overdue = false;
    }
    if (body.clear?.includes("QUANTITY")) stored.quantity = null;
    return route.fulfill(
      ok({
        category: stored.category,
        item: view(stored),
        summary: summary(),
      }),
    );
  });

  await page.route(`**/api/travel/trips/${TRIP_ID}/prep`, (route) =>
    route.fulfill(
      ok({
        tripId: TRIP_ID,
        startDate: "2026-10-24",
        dday: 49,
        ...summary(),
        groups: CATEGORIES.map((category) => {
          const own = items.filter((i) => i.category === category);
          return {
            category,
            total: own.length,
            done: own.filter((i) => i.done).length,
            items: own.map(view),
          };
        }),
      }),
    ),
  );
}

test.describe("준비", () => {
  test("연달아 적고, 체크하고, 숨기고, 지웠다 되돌린다", async ({ page }) => {
    await mockPrep(page);
    await page.goto(`/travel/trips/${TRIP_ID}/prep`);

    // 항목이 없어도 분류 넷은 다 있다. 화면이 분류 목록을 따로 들지 않는다.
    await expect(page.getByRole("button", { name: /^서류 / })).toBeVisible();
    await expect(page.getByRole("button", { name: /^할 일 / })).toBeVisible();

    // 짐으로 바꾼 뒤 엔터로 연달아 적는다 — 시트가 열리고 닫히지 않는다.
    await page.getByLabel(/추가할 분류/).click();
    await page.getByRole("menuitem", { name: "짐" }).click();

    const input = page.getByLabel("준비 항목 추가");
    await input.fill("멀티어댑터");
    await input.press("Enter");
    await expect(input).toHaveValue("");
    await input.fill("양말");
    await input.press("Enter");
    await input.fill("상비약");
    await input.press("Enter");

    await expect(page.getByRole("button", { name: "짐 0/3" })).toBeVisible();

    // 체크해도 자리를 옮기지 않는다.
    // `check()`가 아니라 `click()`인 이유 — 제어 컴포넌트라 클릭 직후 한 프레임 동안 DOM이
    // 아직 이전 값이고, `check()`는 그 순간을 읽어 "상태가 안 바뀌었다"고 본다.
    const first = page.getByRole("checkbox", { name: "멀티어댑터" });
    await first.click();
    await expect(first).toBeChecked();
    await expect(page.getByRole("button", { name: "짐 1/3" })).toBeVisible();

    const bag = page
      .locator("section")
      .filter({ has: page.getByRole("button", { name: /^짐 / }) });
    await expect(bag.getByRole("listitem").first()).toContainText("멀티어댑터");

    // 완료 숨기기는 목록에서만 뺀다 — 헤더의 1/3은 그대로다.
    await page.getByRole("switch", { name: "완료 숨기기" }).click();
    await expect(
      page.getByRole("button", { name: "멀티어댑터", exact: true }),
    ).toBeHidden();
    await expect(page.getByRole("button", { name: "짐 1/3" })).toBeVisible();
    await expect(page).toHaveURL(/hideDone=1/);
    await page.getByRole("switch", { name: "완료 숨기기" }).click();

    // 편집 시트 — 기한은 「출발 N일 전」 숫자 하나다. 날짜를 고르는 자리가 없다.
    await page.getByRole("button", { name: "양말", exact: true }).click();
    const sheet = page.getByRole("dialog");
    await sheet.getByLabel("기한 (출발 며칠 전)").fill("14");
    await sheet.getByLabel("수량").fill("4");
    await sheet.getByRole("button", { name: "저장" }).click();

    await expect(page.getByText("D-14")).toBeVisible();
    await expect(
      bag.getByRole("listitem").filter({ hasText: "양말" }),
    ).toContainText("4");

    // 삭제는 5초 실행취소다. 되돌리면 요청 자체가 나가지 않는다.
    const medicine = page.getByRole("button", { name: "상비약", exact: true });
    await page.getByLabel("상비약 삭제").click();
    await expect(medicine).toBeHidden();
    await page.getByRole("button", { name: "실행취소" }).click();
    await expect(medicine).toBeVisible();
    await expect(page.getByRole("button", { name: "짐 1/3" })).toBeVisible();
  });
});
