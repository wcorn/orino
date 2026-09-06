import { expect, type Page, test } from "./support/test";

/**
 * 준비 순서 바꾸기(#1364).
 *
 * <p>드래그는 jsdom으로 검증되지 않는다 — 좌표·충돌 판정이 전부 실제 레이아웃에 달려 있다.
 * 그래서 여기서는 <b>진짜 마우스</b>로 끌고, 서버로 나가는 배치까지 확인한다.
 *
 * <p><b>마우스 전용 스펙이다.</b> 길게 눌러 모드에 들어가는 길은 손가락에만 있고(#1223),
 * 그 손짓 자체는 일정 보드의 터치 스펙이 이미 본다 — 같은 훅을 쓴다.
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
  title: string;
  sectionLabel: string | null;
  displayOrder: number;
}

interface SectionOrder {
  label: string | null;
  itemIds: number[];
}

/** 서버를 흉내내되 상태를 들고 있는다 — 옮긴 결과가 다음 조회에 그대로 보여야 한다. */
async function mockPrep(page: Page, extra: StoredItem[] = []) {
  const items: StoredItem[] = [
    { id: 1, title: "충전기", sectionLabel: "캐리어", displayOrder: 0 },
    { id: 2, title: "옷", sectionLabel: "캐리어", displayOrder: 1 },
    { id: 3, title: "칫솔", sectionLabel: "세면백", displayOrder: 2 },
    ...extra,
  ];
  const captured: { orders: { category: string; sections: SectionOrder[] }[] } =
    { orders: [] };

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
  await page.route(`**/api/travel/trips/${TRIP_ID}`, (route) =>
    route.fulfill(
      ok({
        id: TRIP_ID,
        title: "일본 가을",
        destinationName: "오사카",
        destinationPlaceId: 21,
        startDate: "2026-10-24",
        endDate: "2026-10-27",
        timezone: "Asia/Tokyo",
        currency: "JPY",
        lat: null,
        lng: null,
        defaultNotifyMinutes: 15,
        morningSummaryEnabled: true,
        status: "UPCOMING",
        dDay: 49,
        totalDays: 4,
        activityCount: 0,
      }),
    ),
  );

  /** 서버와 같은 규칙으로 묶는다 — 묶음의 자리는 그 안의 최소 순서다. */
  const sectionsOf = () => {
    const sorted = [...items].sort((a, b) => a.displayOrder - b.displayOrder);
    const labels: (string | null)[] = [];
    sorted.forEach((row) => {
      if (!labels.includes(row.sectionLabel)) labels.push(row.sectionLabel);
    });
    const ordered = labels.includes(null)
      ? [null, ...labels.filter((label) => label !== null)]
      : labels;
    return ordered.map((label) => {
      const own = sorted.filter((row) => row.sectionLabel === label);
      return {
        label,
        total: own.length,
        done: 0,
        items: own.map((row) => ({
          id: row.id,
          title: row.title,
          done: false,
          sectionLabel: row.sectionLabel,
          quantity: null,
          dueDaysBefore: null,
          dueDate: null,
          overdue: false,
          url: null,
          memo: null,
          displayOrder: row.displayOrder,
        })),
      };
    });
  };

  await page.route(
    `**/api/travel/trips/${TRIP_ID}/prep/order`,
    async (route) => {
      const body = route.request().postDataJSON() as {
        category: string;
        sections: SectionOrder[];
      };
      captured.orders.push(body);
      let order = 0;
      body.sections.forEach((section) =>
        section.itemIds.forEach((id) => {
          const stored = items.find((row) => row.id === id)!;
          stored.sectionLabel = section.label;
          stored.displayOrder = order++;
        }),
      );
      return route.fulfill(ok(null));
    },
  );

  await page.route(`**/api/travel/trips/${TRIP_ID}/prep`, (route) =>
    route.fulfill(
      ok({
        tripId: TRIP_ID,
        startDate: "2026-10-24",
        dday: 49,
        total: items.length,
        done: 0,
        overdueCount: 0,
        groups: CATEGORIES.map((category) => ({
          category,
          total: category === "BAG" ? items.length : 0,
          done: 0,
          sections: category === "BAG" ? sectionsOf() : [],
        })),
      }),
    ),
  );

  return captured;
}

/** 손잡이를 잡아 목표 줄까지 끈다. 여러 번 나눠 움직여야 충돌 판정이 중간 위치를 읽는다. */
async function dragTo(page: Page, handleName: string, targetText: string) {
  await page.getByText(targetText, { exact: true }).waitFor();
  const handle = page.getByRole("button", { name: handleName });
  const from = await handle.boundingBox();
  const to = await page.getByText(targetText, { exact: true }).boundingBox();
  if (!from || !to) throw new Error("손잡이나 목표 줄을 찾지 못했다");

  await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2);
  await page.mouse.down();
  for (let i = 1; i <= 5; i++) {
    await page.mouse.move(
      from.x + from.width / 2,
      from.y + from.height / 2 + ((to.y - from.y) * i) / 5,
      { steps: 4 },
    );
  }
  await page.mouse.up();
}

test.describe("준비 순서 바꾸기", () => {
  test("행에 마우스를 올리면 손잡이가 나온다 — 길게 누를 필요가 없다", async ({
    page,
  }) => {
    await mockPrep(page);
    await page.goto(`/travel/trips/${TRIP_ID}/prep`);

    const handle = page.getByRole("button", { name: "충전기 순서 바꾸기" });
    await expect(handle).toBeAttached();

    // 평소엔 투명하다 — 줄마다 아이콘이 늘어서 있으면 목록이 읽히지 않는다.
    const tools = page.locator("[data-row-tools]").first();
    await expect(tools).toHaveCSS("opacity", "0");
    await page.getByText("충전기", { exact: true }).hover();
    await expect(tools).toHaveCSS("opacity", "1");

    // 데스크톱에는 들어갈 모드가 없다.
    await expect(page.getByText(/드래그 모드/)).toBeHidden();
  });

  test("같은 묶음 안에서 끌면 그 분류의 배치가 통째로 간다", async ({
    page,
  }) => {
    const captured = await mockPrep(page);
    await page.goto(`/travel/trips/${TRIP_ID}/prep`);

    await page.getByText("충전기", { exact: true }).hover();
    await dragTo(page, "충전기 순서 바꾸기", "옷");

    await expect.poll(() => captured.orders.length).toBe(1);
    expect(captured.orders[0]).toEqual({
      category: "BAG",
      sections: [
        { label: "캐리어", itemIds: [2, 1] },
        { label: "세면백", itemIds: [3] },
      ],
    });
  });

  test("다른 묶음의 줄로 끌면 그 묶음으로 옮겨간다", async ({ page }) => {
    const captured = await mockPrep(page);
    await page.goto(`/travel/trips/${TRIP_ID}/prep`);

    await page.getByText("옷", { exact: true }).hover();
    await dragTo(page, "옷 순서 바꾸기", "칫솔");

    // 순서와 묶음이 한 요청으로 나간다 — 옮기고 나서 정렬하는 두 걸음이 아니다.
    await expect.poll(() => captured.orders.length).toBe(1);
    expect(captured.orders[0]).toEqual({
      category: "BAG",
      sections: [
        { label: "캐리어", itemIds: [1] },
        { label: "세면백", itemIds: [3, 2] },
      ],
    });

    // 손을 뗀 순간 화면에도 반영된다(낙관적) — 왕복을 기다리지 않는다.
    const bag = page
      .locator("section")
      .filter({ has: page.getByRole("button", { name: /^짐 / }) });
    await expect(bag.getByRole("button", { name: "세면백 0/2" })).toBeVisible();
    await expect(bag.getByRole("button", { name: "캐리어 0/1" })).toBeVisible();
  });

  test("끌지 않고 버튼으로도 옮긴다 — 끌기의 대안이다 (WCAG 2.5.7)", async ({
    page,
  }) => {
    const captured = await mockPrep(page);
    await page.goto(`/travel/trips/${TRIP_ID}/prep`);

    await page.getByText("충전기", { exact: true }).hover();
    await page.getByRole("button", { name: "충전기 아래로" }).click();

    await expect.poll(() => captured.orders.length).toBe(1);
    expect(captured.orders[0].sections).toEqual([
      { label: "캐리어", itemIds: [2, 1] },
      { label: "세면백", itemIds: [3] },
    ]);
  });

  test("줄을 누르면 편집 시트가 열린다 — 손잡이만 끌기다", async ({ page }) => {
    await mockPrep(page);
    await page.goto(`/travel/trips/${TRIP_ID}/prep`);

    await page.getByRole("button", { name: "충전기", exact: true }).click();
    await expect(page.getByRole("dialog")).toBeVisible();
  });

  test("소제목을 끌면 묶음이 통째로 옮겨간다", async ({ page }) => {
    const captured = await mockPrep(page);
    await page.goto(`/travel/trips/${TRIP_ID}/prep`);

    const bag = page
      .locator("section")
      .filter({ has: page.getByRole("button", { name: /^짐 / }) });
    await expect(bag.getByRole("button", { name: "캐리어 0/2" })).toBeVisible();

    // 캐리어(2줄)를 세면백 자리로 끌어내린다.
    await bag.getByRole("button", { name: "캐리어 0/2" }).hover();
    await dragTo(page, "캐리어 묶음 순서 바꾸기", "칫솔");

    await expect.poll(() => captured.orders.length).toBe(1);
    // 안의 항목은 그대로 따라간다 — 묶음의 자리는 그 항목들이 만든다.
    expect(captured.orders[0]).toEqual({
      category: "BAG",
      sections: [
        { label: "세면백", itemIds: [3] },
        { label: "캐리어", itemIds: [1, 2] },
      ],
    });

    const headers = await bag
      .getByRole("button", { expanded: true })
      .evaluateAll((buttons) =>
        buttons.map((button) => button.getAttribute("aria-label")),
      );
    expect(headers).toEqual(["짐 0/3", "세면백 0/1", "캐리어 0/2"]);
  });

  test("묶음 없음은 자리를 지킨다 — 끌 손잡이가 없다", async ({ page }) => {
    await mockPrep(page, [
      { id: 4, title: "여권 지갑", sectionLabel: null, displayOrder: 3 },
    ]);
    await page.goto(`/travel/trips/${TRIP_ID}/prep`);

    const bag = page
      .locator("section")
      .filter({ has: page.getByRole("button", { name: /^짐 / }) });
    await expect(
      bag.getByRole("button", { name: "묶음 없음 0/1" }),
    ).toBeVisible();
    await bag.getByRole("button", { name: "묶음 없음 0/1" }).hover();

    await expect(
      page.getByRole("button", { name: "묶음 없음 묶음 순서 바꾸기" }),
    ).toBeHidden();
    // 이름 붙은 묶음에는 있다 — 없는 게 아니라 이 줄만 고정이다.
    await expect(
      page.getByRole("button", { name: "캐리어 묶음 순서 바꾸기" }),
    ).toBeAttached();
  });
});
