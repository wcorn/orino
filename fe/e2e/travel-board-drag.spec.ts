import { expect, type Page, test } from "./support/test";

/**
 * 일정 보드 드래그 정렬(#1038).
 *
 * 드래그는 jsdom으로 검증되지 않는다 — 포인터 좌표·롱프레스 타이밍·충돌 판정이 전부
 * 실제 브라우저의 레이아웃에 달려 있다. 그래서 여기서는 <b>진짜 마우스 이벤트</b>로
 * 400ms 롱프레스와 드롭을 재현하고, 서버로 나가는 순서 배열까지 확인한다.
 *
 * (터치 감각 자체는 Android Chrome 실기기 확인이 따로 필요하다 — 자동화로 대체할 수 없다.)
 *
 * <p><b>터치 전용 스펙이다</b>(#1223). 길게 눌러 모드에 들어가는 길은 손가락에만 있다 —
 * 스크롤과 집어 올리기를 가를 방법이 그것뿐이기 때문이다. 마우스는 모드 없이 손잡이로 끌고,
 * 그쪽은 travel-mouse-reorder.spec.ts가 본다. 그래서 이 파일은 mobile-touch 프로젝트에서만 돈다.
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

/** 서버가 받은 요청을 모아 둔다. 테스트가 "무엇이 나갔는지"를 본다. */
interface Captured {
  orders: { date: string | null; activityIds: number[] }[][];
  updates: { id: string; body: Record<string, unknown> }[];
}

async function mockBoard(page: Page): Promise<Captured> {
  const captured: Captured = { orders: [], updates: [] };

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
  // 보드는 숙소 목록을 함께 읽는다(#1143). 이 화면에서 확인할 것은 아니라 비워 둔다.
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

  await page.route("**/api/travel/activities/*", async (route) => {
    if (route.request().method() === "PUT") {
      captured.updates.push({
        id: route.request().url().split("/").pop() ?? "",
        body: route.request().postDataJSON() as Record<string, unknown>,
      });
      return route.fulfill(ok(activity(1, "센소지", 0)));
    }
    return route.fulfill(ok(null));
  });

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

/** 움직이지 않고 400ms 눌러 드래그 모드에 들어간다(모드 진입은 드래그가 아니다). */
async function enterDragMode(page: Page, rowText: string) {
  const box = await page.getByText(rowText, { exact: true }).boundingBox();
  await page.mouse.move(box!.x + box!.width / 2, box!.y + box!.height / 2);
  await page.mouse.down();
  await page.waitForTimeout(500);
  await page.mouse.up();
  await expect(
    page.getByText(
      "드래그 모드 · 순서를 바꾸면 이동시간과 알림을 다시 계산해요",
    ),
  ).toBeVisible();
}

/** 드래그 모드에서 행을 끌어 목표 위로 옮겨 놓는다. */
async function dragRowTo(page: Page, from: string, to: string) {
  const a = await page.getByText(from, { exact: true }).boundingBox();
  const b = await page.getByText(to, { exact: true }).boundingBox();
  if (!a || !b) throw new Error("행을 찾지 못했다");

  await page.mouse.move(a.x + a.width / 2, a.y + a.height / 2);
  await page.mouse.down();
  // 여러 번 나눠 움직여야 dnd-kit의 충돌 판정이 중간 위치를 인식한다.
  for (let i = 1; i <= 5; i++) {
    await page.mouse.move(
      b.x + b.width / 2,
      a.y + a.height / 2 + ((b.y - a.y) * i) / 5,
      { steps: 4 },
    );
  }
  await page.mouse.up();
}

test.describe("일정 보드 드래그", () => {
  test("움직이지 않고 길게 누르면 드래그 모드에 들어간다", async ({ page }) => {
    await mockBoard(page);
    await page.goto(`/travel/trips/${TRIP_ID}/board`);
    await expect(page.getByText("센소지")).toBeVisible();

    await enterDragMode(page, "센소지");

    // 행 우측이 이동 조작으로 바뀐다.
    await expect(page.getByLabel("센소지 위로")).toBeVisible();
    await expect(page.getByLabel("센소지 삭제")).toBeHidden();
  });

  test("모드에 들어온 직후의 첫 탭이 삼켜지지 않는다", async ({ page }) => {
    const captured = await mockBoard(page);
    await page.goto(`/travel/trips/${TRIP_ID}/board`);
    await expect(page.getByText("센소지")).toBeVisible();

    await enterDragMode(page, "센소지");
    // 진입 직후 한 번만 누른다 — 드래그 라이브러리가 클릭을 삼키면 여기서 걸린다.
    await page.getByLabel("우에노 위로").click();

    await expect.poll(() => captured.orders.length).toBeGreaterThan(0);
  });

  test("행을 끌어 순서를 바꾸면 그 날짜의 전체 배열이 서버로 간다", async ({
    page,
  }) => {
    const captured = await mockBoard(page);
    await page.goto(`/travel/trips/${TRIP_ID}/board`);
    await expect(page.getByText("센소지")).toBeVisible();

    await enterDragMode(page, "센소지");
    await dragRowTo(page, "센소지", "디즈니씨");

    await expect.poll(() => captured.orders.length).toBeGreaterThan(0);
    const moves = captured.orders[0];
    expect(moves).toHaveLength(1);
    expect(moves[0].date).toBe("2026-10-24");
    // 부분이 아니라 그 날짜의 전체 순서를 보낸다.
    expect(moves[0].activityIds).toHaveLength(3);
    expect(moves[0].activityIds[2]).toBe(1);
  });

  test("드래그 모드에서 화살표로도 한 칸씩 옮긴다", async ({ page }) => {
    const captured = await mockBoard(page);
    await page.goto(`/travel/trips/${TRIP_ID}/board`);
    await expect(page.getByText("센소지")).toBeVisible();

    await enterDragMode(page, "센소지");

    await page.getByLabel("우에노 위로").click();

    await expect.poll(() => captured.orders.length).toBeGreaterThan(0);
    expect(captured.orders.at(-1)![0].activityIds.slice(0, 2)).toEqual([2, 1]);
  });

  test("달력의 날짜 칸에 떨어뜨리면 그 날짜로 옮긴다", async ({ page }) => {
    const captured = await mockBoard(page);
    await page.goto(`/travel/trips/${TRIP_ID}/board`);
    await expect(page.getByText("센소지")).toBeVisible();

    await enterDragMode(page, "센소지");

    const row = page.getByText("센소지", { exact: true });
    const tab = page.getByRole("tab", { name: /10\.25/ });
    const a = await row.boundingBox();
    const b = await tab.boundingBox();

    await page.mouse.move(a!.x + a!.width / 2, a!.y + a!.height / 2);
    await page.mouse.down();
    for (let i = 1; i <= 6; i++) {
      await page.mouse.move(
        a!.x + ((b!.x + b!.width / 2 - a!.x) * i) / 6,
        a!.y + ((b!.y + b!.height / 2 - a!.y) * i) / 6,
        { steps: 4 },
      );
    }
    await page.mouse.up();

    await expect.poll(() => captured.updates.length).toBeGreaterThan(0);
    expect(captured.updates[0].body.activityDate).toBe("2026-10-25");
  });

  test("드래그 모드에서는 행을 눌러도 상세로 가지 않는다", async ({ page }) => {
    await mockBoard(page);
    await page.goto(`/travel/trips/${TRIP_ID}/board`);
    await expect(page.getByText("센소지")).toBeVisible();

    await enterDragMode(page, "센소지");

    await page.getByText("센소지", { exact: true }).click();

    // 상세로 이동하지 않고 보드에 머문다.
    await expect(page).toHaveURL(new RegExp(`/travel/trips/${TRIP_ID}/board`));
    await expect(
      page.getByText(
        "드래그 모드 · 순서를 바꾸면 이동시간과 알림을 다시 계산해요",
      ),
    ).toBeVisible();
  });

  test("완료를 누르면 드래그 모드에서 나온다", async ({ page }) => {
    await mockBoard(page);
    await page.goto(`/travel/trips/${TRIP_ID}/board`);
    await expect(page.getByText("센소지")).toBeVisible();

    await enterDragMode(page, "센소지");
    await expect(page.getByLabel("센소지 위로")).toBeVisible();

    await page.getByRole("button", { name: "완료" }).click();

    await expect(page.getByLabel("센소지 위로")).toBeHidden();
    await expect(page.getByLabel("센소지 삭제")).toBeVisible();
  });
});
