import { expect, type Page, test } from "./support/test";

/**
 * 하루 통째로 교체(S-04, #1368).
 *
 * <p>여기서 확인하는 것은 <b>고른 날짜가 화면에 실제로 건너오는가</b>다. 요청 본문만 보면
 * 두 날짜를 제대로 보냈는지까지밖에 알 수 없다 — 교체는 보드를 다시 읽어야 눈에 보이고,
 * 캐시 무효화를 좁히는 순간 요청은 맞는데 화면은 그대로인 상태가 된다.
 */

const TRIP_ID = 1;
const D1 = "2026-10-24";
const D2 = "2026-10-25";

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

const ok = (data: unknown) => ({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ code: "OK", data }),
});

function activity(id: number, title: string, date: string, sortOrder: number) {
  return {
    id,
    tripId: TRIP_ID,
    title,
    activityDate: date,
    startTime: null,
    place: null,
    memo: null,
    url: null,
    notifyEnabled: false,
    notifyMinutes: null,
    departureNotifyEnabled: false,
    sortOrder,
    log: null,
    hasLog: false,
    outOfBaseCity: false,
    canDepartureNotify: true,
  };
}

function day(dayId: number, dayIndex: number, date: string, weekday: string) {
  return {
    dayId,
    dayIndex,
    date,
    weekday,
    activityCount: 0,
    baseCity: TOKYO,
    cityChanged: false,
    legIndex: 1,
    cityMemo: null,
    weather: null,
    stayTonight: null,
    stayCheckout: null,
  };
}

interface Captured {
  swaps: Record<string, unknown>[];
}

/**
 * 서버처럼 <b>상태를 들고</b> 흉내낸다. 교체 요청이 오면 실제로 두 날짜를 맞바꾸므로,
 * 그 뒤의 조회는 바뀐 목록을 돌려준다 — 화면이 다시 읽었는지가 여기서 갈린다.
 */
async function mockBoard(page: Page): Promise<Captured> {
  const captured: Captured = { swaps: [] };
  const byDate: Record<string, ReturnType<typeof activity>[]> = {
    [D1]: [activity(1, "센소지", D1, 0), activity(2, "스카이트리", D1, 1)],
    [D2]: [activity(3, "하코네 온천", D2, 0)],
  };

  await page.route("**/api/auth/reissue", (route) =>
    route.fulfill(ok({ accessToken: "mock-access-token" })),
  );
  await page.route("**/api/planner/reviews/summary", (route) =>
    route.fulfill(
      ok({
        today: D1,
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
    `**/api/travel/trips/${TRIP_ID}/activities/swap`,
    (route) => {
      const body = route.request().postDataJSON() as {
        date: string;
        withDate: string;
      };
      captured.swaps.push(body);
      const here = byDate[body.date] ?? [];
      byDate[body.date] = byDate[body.withDate] ?? [];
      byDate[body.withDate] = here;
      return route.fulfill(ok(null));
    },
  );

  await page.route(`**/api/travel/trips/${TRIP_ID}/board*`, (route) => {
    const url = new URL(route.request().url());
    const isArchive = url.searchParams.get("archive") === "true";
    const date = url.searchParams.get("date") ?? D1;
    return route.fulfill(
      ok({
        trip: {
          id: TRIP_ID,
          title: "일본",
          startDate: D1,
          endDate: D2,
          status: "UPCOMING",
          recordMode: false,
          cityCount: 1,
          countryCount: 1,
          singleCity: true,
        },
        days: [
          { ...day(501, 1, D1, "토"), activityCount: byDate[D1].length },
          { ...day(502, 2, D2, "일"), activityCount: byDate[D2].length },
        ],
        selectedDate: isArchive ? null : date,
        archiveCount: 0,
        activities: isArchive ? [] : (byDate[date] ?? []),
        moves: [],
        stayMove: null,
      }),
    );
  });

  return captured;
}

test.describe("하루 통째로 교체", () => {
  test("고른 날짜의 일정이 건너오고 이쪽 일정은 그리로 간다", async ({
    page,
  }) => {
    const captured = await mockBoard(page);
    await page.goto(`/travel/trips/${TRIP_ID}/board`);
    await expect(page.getByText("센소지")).toBeVisible();

    await page.getByRole("button", { name: "여행 메뉴" }).click();
    await page.getByRole("menuitem", { name: "다른 날짜와 교체" }).click();

    const sheet = page.getByRole("dialog");
    // 보고 있는 1일차는 목록에 없고, 건너올 일정 수가 함께 보인다.
    await expect(sheet.getByRole("button", { name: /일차/ })).toHaveCount(1);
    await expect(sheet.getByRole("button", { name: /2일차/ })).toContainText(
      "일정 1개",
    );

    await sheet.getByRole("button", { name: /2일차/ }).click();

    await expect(page.getByRole("dialog")).toBeHidden();
    expect(captured.swaps).toEqual([{ date: D1, withDate: D2 }]);
    // 보드를 다시 읽어 2일차의 일정이 이 날짜에 서 있다.
    await expect(page.getByText("하코네 온천")).toBeVisible();
    await expect(page.getByText("센소지")).toBeHidden();

    // 건너간 쪽도 순서를 지킨 채 통째로 옮겨갔다.
    await page.getByRole("tab").nth(1).click();
    await expect(page.getByText("센소지")).toBeVisible();
    await expect(page.getByText("스카이트리")).toBeVisible();
  });
});
