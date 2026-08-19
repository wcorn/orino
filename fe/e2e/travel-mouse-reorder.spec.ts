import { expect, type Page, test } from "@playwright/test";

/**
 * 마우스로 일정 순서 바꾸기(#1223).
 *
 * <p>데스크톱에는 <b>드래그 모드가 없다.</b> 롱프레스는 스크롤과 집어 올리기를 가르기 위한
 * 손가락의 관용구고, 마우스에는 그 문제가 없어 배운 적 없는 동작이 된다. 대신 행에 마우스를
 * 올리면 손잡이가 나오고 거기서 곧바로 끈다.
 *
 * <p>이 파일은 chromium(데스크톱) 프로젝트에서만 돈다 — mobile-touch에서는 `(pointer: fine)`이
 * 거짓이라 손잡이 자체가 없다. 터치 쪽은 travel-board-drag.spec.ts가 본다.
 */

const TRIP_ID = 1;

const TOKYO = {
  placeId: 21,
  name: "도쿄",
  timezone: "Asia/Tokyo",
  currency: "JPY",
  countryCode: "JP",
  cityPlaceRef: null,
  lat: null,
  lng: null,
};

const DAYS = [
  {
    dayId: 501,
    dayIndex: 1,
    date: "2026-10-24",
    weekday: "토",
    activityCount: 3,
    baseCity: TOKYO,
    cityChanged: false,
    legIndex: 1,
    cityMemo: null,
    weather: null,
    stayTonight: null,
    stayCheckout: null,
  },
  {
    dayId: 502,
    dayIndex: 2,
    date: "2026-10-25",
    weekday: "일",
    activityCount: 0,
    baseCity: TOKYO,
    cityChanged: false,
    legIndex: 1,
    cityMemo: null,
    weather: null,
    stayTonight: null,
    stayCheckout: null,
  },
];

const ok = (data: unknown) => ({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ code: "OK", data }),
});

function activity(id: number, title: string, sortOrder: number) {
  return {
    id,
    tripId: TRIP_ID,
    title,
    activityDate: "2026-10-24",
    startTime: null,
    place: null,
    memo: null,
    url: null,
    notifyEnabled: false,
    notifyMinutes: null,
    departureNotifyEnabled: false,
    sortOrder,
    hasLog: false,
  };
}

interface Captured {
  orders: { date: string | null; activityIds: number[] }[][];
}

async function mockBoard(page: Page): Promise<Captured> {
  const captured: Captured = { orders: [] };

  await page.route("**/api/auth/reissue", (route) =>
    route.fulfill(ok({ accessToken: "mock-access-token" })),
  );
  await page.route("**/api/planner/reviews/summary", (route) =>
    route.fulfill(
      ok({
        today: "2026-10-24",
        counts: { now: 0, overdue: 0, upcoming: 0, doneToday: 0 },
        estimatedMinutes: 0,
        materials: [],
      }),
    ),
  );
  await page.route("**/api/travel/summary", (route) =>
    route.fulfill(ok({ ongoing: null, next: null, recentCompleted: null })),
  );
  await page.route("**/api/travel/trips/*/stays", (route) =>
    route.fulfill(ok([])),
  );

  await page.route(
    `**/api/travel/trips/${TRIP_ID}/activities/order`,
    async (route) => {
      const body = route.request().postDataJSON() as {
        moves: { date: string | null; activityIds: number[] }[];
      };
      captured.orders.push(body.moves);
      return route.fulfill(ok({ moves: [] }));
    },
  );

  await page.route(`**/api/travel/trips/${TRIP_ID}/board*`, (route) => {
    const url = new URL(route.request().url());
    const isArchive = url.searchParams.get("archive") === "true";
    const date = url.searchParams.get("date") ?? DAYS[0].date;
    return route.fulfill(
      ok({
        trip: {
          id: TRIP_ID,
          title: "도쿄 3박 4일",
          startDate: DAYS[0].date,
          endDate: DAYS[1].date,
          status: "UPCOMING",
          recordMode: false,
          cityCount: 1,
          countryCount: 1,
          singleCity: true,
        },
        days: DAYS,
        selectedDate: isArchive ? null : date,
        archiveCount: 0,
        activities:
          isArchive || date !== DAYS[0].date
            ? []
            : [
                activity(1, "센소지", 0),
                activity(2, "우에노", 1),
                activity(3, "디즈니씨", 2),
              ],
        moves: [],
        stayMove: null,
      }),
    );
  });

  return captured;
}

async function openBoard(page: Page) {
  await page.goto(`/travel/trips/${TRIP_ID}/board`);
  await expect(page.getByText("센소지", { exact: true })).toBeVisible();
}

test.describe("마우스로 순서 바꾸기", () => {
  test("행에 마우스를 올리면 손잡이가 나온다 — 길게 누를 필요가 없다", async ({
    page,
  }) => {
    await mockBoard(page);
    await openBoard(page);

    const handle = page.getByRole("button", { name: "센소지 순서 바꾸기" });
    await expect(handle).toBeAttached();
    // 평소엔 투명하다 — 줄마다 아이콘이 늘어서 있으면 목록이 읽히지 않는다.
    // (포커스로도 나와야 해서 감추는 대신 투명하게 둔다 — DOM에는 늘 있다.)
    const tools = page.locator("[data-row-tools]").first();
    await expect(tools).toHaveCSS("opacity", "0");

    await page.getByText("센소지", { exact: true }).hover();
    await expect(tools).toHaveCSS("opacity", "1");

    // 데스크톱에는 들어갈 모드가 없다.
    await expect(page.getByText("드래그 모드 · 순서를 바꾸면")).toBeHidden();
  });

  test("손잡이를 끌면 그 날짜의 전체 배열이 서버로 간다", async ({ page }) => {
    const captured = await mockBoard(page);
    await openBoard(page);

    const first = page.getByText("센소지", { exact: true });
    const third = page.getByText("디즈니씨", { exact: true });
    await first.hover();

    const handle = page.getByRole("button", { name: "센소지 순서 바꾸기" });
    const from = await handle.boundingBox();
    const to = await third.boundingBox();
    if (!from || !to) throw new Error("손잡이나 목표 행을 찾지 못했다");

    await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2);
    await page.mouse.down();
    // 여러 번 나눠 움직여야 dnd-kit의 충돌 판정이 중간 위치를 인식한다.
    for (let i = 1; i <= 5; i++) {
      await page.mouse.move(
        from.x + from.width / 2,
        from.y + from.height / 2 + ((to.y - from.y) * i) / 5,
        { steps: 4 },
      );
    }
    await page.mouse.up();

    await expect.poll(() => captured.orders.length).toBe(1);
    expect(captured.orders[0]).toEqual([
      { date: "2026-10-24", activityIds: [2, 3, 1] },
    ]);
  });

  test("끌지 않고 버튼으로도 옮긴다 — 끌기의 대안이다 (WCAG 2.5.7)", async ({
    page,
  }) => {
    const captured = await mockBoard(page);
    await openBoard(page);

    await page.getByText("센소지", { exact: true }).hover();
    await page.getByRole("button", { name: "센소지 아래로" }).click();

    await expect.poll(() => captured.orders.length).toBe(1);
    expect(captured.orders[0]).toEqual([
      { date: "2026-10-24", activityIds: [2, 1, 3] },
    ]);
  });

  test("행 본문을 누르면 상세로 간다 — 손잡이만 끌기다", async ({ page }) => {
    await mockBoard(page);
    await openBoard(page);

    await page.getByText("센소지", { exact: true }).click();

    await expect(page).toHaveURL(/\/travel\/activities\/1/);
  });
});
