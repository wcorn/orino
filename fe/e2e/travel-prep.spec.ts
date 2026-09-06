import { expect, type Page, test } from "./support/test";

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
  sectionLabel: string | null;
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
    sectionLabel: stored.sectionLabel,
    quantity: stored.quantity,
    dueDaysBefore: stored.dueDaysBefore,
    dueDate: stored.dueDate,
    overdue: stored.overdue,
    url: stored.url,
    memo: stored.memo,
    displayOrder: stored.displayOrder,
  });
  /**
   * 분류 안을 묶음으로 나눈다 — 서버와 같은 규칙이다(#1358). 묶음의 자리는 그 안의 최소
   * `displayOrder`이고, 「묶음 없음」은 언제나 맨 앞이다.
   */
  const sectionsOf = (own: StoredItem[]) => {
    const sorted = [...own].sort((a, b) => a.displayOrder - b.displayOrder);
    const labels: (string | null)[] = [];
    sorted.forEach((i) => {
      if (!labels.includes(i.sectionLabel)) labels.push(i.sectionLabel);
    });
    const ordered = labels.includes(null)
      ? [null, ...labels.filter((label) => label !== null)]
      : labels;
    return ordered.map((label) => {
      const inSection = sorted.filter((i) => i.sectionLabel === label);
      return {
        label,
        total: inSection.length,
        done: inSection.filter((i) => i.done).length,
        items: inSection.map(view),
      };
    });
  };

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
        sectionLabel?: string;
      };
      const category = body.category ?? "TODO";
      const stored: StoredItem = {
        id: nextId++,
        category,
        title: body.title,
        done: false,
        sectionLabel: body.sectionLabel ?? null,
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
      sectionLabel?: string;
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
    // 묶음을 옮기면 그 묶음의 맨 뒤다 — 서버와 같은 규칙이라야 화면 순서를 볼 수 있다.
    if (body.sectionLabel !== undefined) {
      stored.sectionLabel = body.sectionLabel;
      stored.displayOrder =
        Math.max(
          ...items
            .filter((i) => i.category === stored.category)
            .map((i) => i.displayOrder),
        ) + 1;
    }
    if (body.clear?.includes("SECTION_LABEL")) stored.sectionLabel = null;
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

  // 브레드크럼이 읽는 이름 하나. 좁은 화면에서 잘리는지 보려고 길게 준다.
  await page.route(`**/api/travel/trips/${TRIP_ID}`, (route) =>
    route.fulfill(
      ok({
        id: TRIP_ID,
        // 어떤 폰트로 그려도 520px를 넘도록 길게 준다 — 글자 폭은 OS마다 다르다.
        title:
          "일본 간사이 한 바퀴 — 오사카 교토 나라 고베 히메지 와카야마 " +
          "9박 10일 가을 단풍 여행 · 첫날 간사이공항 도착 후 난바 숙소 체크인부터 " +
          "마지막 날 교토역에서 하루카 타고 돌아오는 일정까지",
        destinationName: "오사카",
        destinationPlaceId: 21,
        startDate: "2026-10-24",
        endDate: "2026-11-02",
        timezone: "Asia/Tokyo",
        currency: "JPY",
        lat: null,
        lng: null,
        defaultNotifyMinutes: 15,
        morningSummaryEnabled: true,
        status: "UPCOMING",
        dDay: 49,
        totalDays: 10,
        activityCount: 0,
      }),
    ),
  );

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
            sections: sectionsOf(own),
          };
        }),
      }),
    ),
  );
}

test.describe("준비", () => {
  /**
   * 좁은 화면에서 브레드크럼이 <b>한 줄로 남는지</b>(#1348). 모바일에서는 사이드바가 닫혀
   * 있어 이 줄이 여행 이름을 말하는 유일한 자리인데, 긴 이름에 줄바꿈이 나면 헤더가 통째로
   * 밀린다. jsdom은 레이아웃을 계산하지 않아 여기서만 잡을 수 있다.
   */
  test("520px에서 브레드크럼이 줄바꿈 없이 이름만 자른다", async ({ page }) => {
    await mockPrep(page);
    await page.setViewportSize({ width: 520, height: 800 });
    await page.goto(`/travel/trips/${TRIP_ID}/prep`);

    const crumb = page.getByRole("navigation", { name: "현재 위치" });
    await expect(crumb).toBeVisible();

    const box = await crumb.boundingBox();
    // 13px 한 줄. 두 줄이면 30px을 넘는다.
    expect(box!.height).toBeLessThan(30);

    // 「여행」과 「준비」는 그대로 남고, 잘리는 것은 가운데 이름이다.
    // 긴 제목에도 「여행」이 들어 있다 — 부분 일치로 잡으면 두 링크가 걸린다.
    await expect(
      crumb.getByRole("link", { name: "여행", exact: true }),
    ).toBeVisible();
    await expect(crumb.getByText("준비")).toBeVisible();
    const title = crumb.getByRole("link", { name: /일본 간사이/ });
    const clipped = await title.evaluate(
      (el) => el.scrollWidth > el.clientWidth,
    );
    expect(clipped).toBe(true);

    /*
      잘리는 대신 페이지를 넓히면 화면 전체가 가로로 밀린다. 폰트가 뭐든 성립하는
      단언이라 여기가 실제 안전망이다 — 글자 폭은 OS마다 다르고, CI(리눅스)와 개발
      기기(맥)의 한글 폭이 달라 같은 제목이 한쪽에서만 잘렸다.
    */
    const layout = await page.evaluate(() => ({
      overflows: document.documentElement.scrollWidth > window.innerWidth,
      crumbRight: document
        .querySelector('nav[aria-label="현재 위치"]')!
        .getBoundingClientRect().right,
      viewport: window.innerWidth,
    }));
    expect(layout.overflows).toBe(false);
    expect(layout.crumbRight).toBeLessThanOrEqual(layout.viewport);
  });

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

  /**
   * 묶음 한 겹(#1358). 짐이 수십 줄이 되면 분류 하나가 한 화면에 안 들어간다 — 그때 이
   * 흐름이 성립해야 목록이 다시 짧아진다: 묶음을 만들고, 그 묶음으로 연달아 적고, 다 챙긴
   * 묶음은 접는다.
   */
  test("묶음을 만들어 연달아 적고, 다 챙긴 묶음은 접는다", async ({ page }) => {
    await mockPrep(page);
    await page.goto(`/travel/trips/${TRIP_ID}/prep`);

    await page.getByLabel(/추가할 분류/).click();
    await page.getByRole("menuitem", { name: "짐" }).click();

    // 묶기 전에는 소제목이 없다 — 안 쓰는 사람에게 늘어나는 줄이 없어야 한다.
    const bag = page
      .locator("section")
      .filter({ has: page.getByRole("button", { name: /^짐 / }) });
    const input = page.getByLabel("준비 항목 추가");
    await input.fill("여권 지갑");
    await input.press("Enter");
    await expect(page.getByRole("button", { name: "짐 0/1" })).toBeVisible();
    await expect(bag.getByRole("button", { name: /묶음 없음/ })).toBeHidden();

    // 새 묶음은 입력줄에서 만든다. 편집 시트까지 가야 하면 첫 묶음을 아무도 안 만든다.
    await page.getByLabel(/추가할 묶음/).click();
    await page.getByRole("menuitem", { name: "새 묶음…" }).click();
    const name = page.getByLabel("새 묶음 이름");
    await name.fill("캐리어");
    await name.press("Enter");

    // 묶음도 방금 적은 것을 이어받는다 — 줄마다 다시 고르면 묶는 게 적는 것보다 오래 걸린다.
    await input.fill("충전기");
    await input.press("Enter");
    await input.fill("옷");
    await input.press("Enter");
    await expect(page.getByLabel("추가할 묶음: 캐리어")).toBeVisible();

    // 이름 붙은 묶음이 생기면 그때 소제목이 뜬다. 묶음 없음이 맨 위다.
    await expect(
      bag.getByRole("button", { name: "묶음 없음 0/1" }),
    ).toBeVisible();
    await expect(bag.getByRole("button", { name: "캐리어 0/2" })).toBeVisible();
    const headers = await bag
      .getByRole("button", { expanded: true })
      .evaluateAll((buttons) =>
        buttons.map((button) => button.getAttribute("aria-label")),
      );
    expect(headers).toEqual(["짐 0/3", "묶음 없음 0/1", "캐리어 0/2"]);

    // 다 챙긴 묶음은 접는다 — 접어도 개수는 남는다.
    await bag.getByRole("button", { name: "캐리어 0/2" }).click();
    await expect(
      page.getByRole("button", { name: "충전기", exact: true }),
    ).toBeHidden();
    await expect(bag.getByRole("button", { name: "캐리어 0/2" })).toBeVisible();
    await expect(
      page.getByRole("button", { name: "여권 지갑", exact: true }),
    ).toBeVisible();

    // 시트에서도 묶음을 옮길 수 있다 — 옮기면 그 묶음의 맨 뒤다.
    await page.getByRole("button", { name: "여권 지갑", exact: true }).click();
    const sheet = page.getByRole("dialog");
    await sheet.getByRole("button", { name: "묶음 캐리어(으)로" }).click();
    await sheet.getByRole("button", { name: "저장" }).click();

    await expect(bag.getByRole("button", { name: "캐리어 0/3" })).toBeVisible();
    await expect(bag.getByRole("button", { name: /묶음 없음/ })).toBeHidden();
  });
});
