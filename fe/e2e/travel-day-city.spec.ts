import { expect, type Page, test } from "@playwright/test";

/**
 * 날짜 탭의 기준 도시(S-04, #1133).
 *
 * <p>여기서 확인하는 것은 <b>두 롱프레스가 서로를 먹지 않는가</b>다. 일정 행은 400ms에
 * 드래그 모드로 들어가고 날짜 탭은 450ms에 시트를 여는데, 둘 다 "누르고 기다린다"는 같은
 * 손짓이라 실제 포인터로만 갈리는지 알 수 있다.
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
const NIKKO = { ...TOKYO, placeId: 22, name: "닛코" };

const ok = (data: unknown) => ({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ code: "OK", data }),
});

function day(
  dayId: number,
  dayIndex: number,
  date: string,
  weekday: string,
  overrides: Record<string, unknown> = {},
) {
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
    ...overrides,
  };
}

interface Captured {
  dayUpdates: { dayId: string; body: Record<string, unknown> }[];
}

async function mockBoard(page: Page): Promise<Captured> {
  const captured: Captured = { dayUpdates: [] };

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
  await page.route("**/api/travel/days/*", (route) => {
    const dayId = route.request().url().split("/").pop() ?? "";
    captured.dayUpdates.push({
      dayId,
      body: route.request().postDataJSON() as Record<string, unknown>,
    });
    return route.fulfill(ok([]));
  });
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
          cityCount: 2,
          countryCount: 1,
          singleCity: false,
        },
        days: [
          day(501, 1, D1, "토"),
          day(502, 2, D2, "일", {
            baseCity: NIKKO,
            cityChanged: true,
            legIndex: 2,
            cityMemo: "코인로커에 짐 보관",
          }),
        ],
        selectedDate: isArchive ? null : date,
        archiveCount: 0,
        activities:
          date !== D1 || isArchive
            ? []
            : [
                {
                  id: 1,
                  tripId: TRIP_ID,
                  title: "센소지",
                  activityDate: D1,
                  startTime: "09:00",
                  place: null,
                  memo: null,
                  url: null,
                  notifyEnabled: false,
                  notifyMinutes: null,
                  departureNotifyEnabled: false,
                  sortOrder: 0,
                  log: null,
                  hasLog: false,
                  outOfBaseCity: false,
                  canDepartureNotify: true,
                },
              ],
        travelTimes: [],
        stayMove: null,
      }),
    );
  });

  return captured;
}

/** 손가락으로 누르고 기다린다. 움직이지 않는 것이 이 제스처의 전부다. */
async function press(page: Page, locator: string, ms: number) {
  const box = await page.locator(locator).first().boundingBox();
  if (!box) throw new Error("대상을 찾지 못했다");
  const x = box.x + box.width / 2;
  const y = box.y + box.height / 2;
  await page.mouse.move(x, y);
  await page.mouse.down();
  await page.waitForTimeout(ms);
  await page.mouse.up();
}

test.describe("날짜 탭 · 기준 도시", () => {
  test("탭이 도시를 말하고 도시가 바뀌는 날짜에 메모가 붙는다", async ({
    page,
  }) => {
    await mockBoard(page);
    await page.goto(`/travel/trips/${TRIP_ID}/board`);

    await expect(page.getByRole("tab").nth(0)).toContainText("1 도쿄");
    await expect(page.getByRole("tab").nth(1)).toContainText("2 닛코");
    // 부제는 보고 있는 날짜의 도시를 따른다.
    await expect(page.getByText(/도쿄 · Asia\/Tokyo · JPY/)).toBeVisible();

    await page.getByRole("tab").nth(1).click();
    await expect(page.getByText("코인로커에 짐 보관")).toBeVisible();
  });

  test("450ms 누르면 기준 도시 시트가 열린다", async ({ page }) => {
    await mockBoard(page);
    await page.goto(`/travel/trips/${TRIP_ID}/board`);
    await expect(page.getByRole("tab").nth(0)).toBeVisible();

    await press(page, '[role="tab"]', 600);

    const sheet = page.getByRole("dialog");
    await expect(sheet).toBeVisible();
    await expect(sheet).toContainText("지금은 도쿄");
  });

  test("일정 행을 누르면 드래그 모드일 뿐 도시 시트는 열리지 않는다", async ({
    page,
  }) => {
    await mockBoard(page);
    await page.goto(`/travel/trips/${TRIP_ID}/board`);
    await expect(page.getByText("센소지")).toBeVisible();

    await press(page, 'text="센소지"', 600);

    await expect(page.getByRole("button", { name: "완료" })).toBeVisible();
    await expect(page.getByRole("dialog")).toBeHidden();
  });

  test("시트에서 도시를 고르면 그 날짜만 바꾸는 요청이 나간다", async ({
    page,
  }) => {
    const captured = await mockBoard(page);
    await page.goto(`/travel/trips/${TRIP_ID}/board`);
    await expect(page.getByRole("tab").nth(0)).toBeVisible();

    await press(page, '[role="tab"]', 600);
    const sheet = page.getByRole("dialog");
    await sheet.getByRole("button", { name: "닛코" }).click();
    await sheet.getByRole("button", { name: "저장" }).click();

    await expect(page.getByRole("dialog")).toBeHidden();
    expect(captured.dayUpdates).toEqual([
      { dayId: "501", body: { baseCityPlaceId: 22 } },
    ]);
  });
});
