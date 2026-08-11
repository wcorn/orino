import { expect, type Page, test } from "@playwright/test";

/**
 * 도시 경계 이동시간(§3.4, #1142).
 *
 * <p>여기서 확인하는 것은 <b>탭 한 번에 지도로 나가는가</b>다. 도시를 넘는 이동에는 도보/자동차를
 * 물어볼 이유가 없어 이동수단 시트를 건너뛰는데, "시트가 열리지 않는다"는 실제 클릭으로만
 * 확인된다 — 핸들러를 갈아끼운 검사는 시트가 열렸다 닫혔는지 구분하지 못한다.
 */

const TRIP_ID = 1;
const D1 = "2026-10-24";

const OSAKA = {
  placeId: 21,
  name: "오사카",
  timezone: "Asia/Tokyo",
  currency: "JPY",
  countryCode: "JP",
  cityPlaceRef: "ChIJ_osaka",
  lat: null,
  lng: null,
};

const ok = (data: unknown) => ({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ code: "OK", data }),
});

function activity(
  id: number,
  title: string,
  place: { name: string; lat: number; lng: number; cityPlaceRef: string },
) {
  return {
    id,
    tripId: TRIP_ID,
    title,
    activityDate: D1,
    startTime: null,
    place: {
      id: id + 100,
      name: place.name,
      address: `${place.name} 주소`,
      lat: place.lat,
      lng: place.lng,
      cityName: place.cityPlaceRef === "ChIJ_osaka" ? "오사카" : "교토",
      cityPlaceRef: place.cityPlaceRef,
    },
    memo: null,
    url: null,
    notifyEnabled: false,
    notifyMinutes: null,
    departureNotifyEnabled: false,
    sortOrder: id - 1,
    log: null,
    hasLog: false,
    outOfBaseCity: place.cityPlaceRef !== "ChIJ_osaka",
    canDepartureNotify: false,
  };
}

async function mockBoard(page: Page) {
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
  await page.route(`**/api/travel/trips/${TRIP_ID}/board*`, (route) =>
    route.fulfill(
      ok({
        trip: {
          id: TRIP_ID,
          title: "일본",
          startDate: D1,
          endDate: D1,
          status: "UPCOMING",
          recordMode: false,
          cityCount: 2,
          countryCount: 1,
          singleCity: false,
        },
        days: [
          {
            dayId: 501,
            dayIndex: 1,
            date: D1,
            weekday: "토",
            activityCount: 2,
            baseCity: OSAKA,
            cityChanged: false,
            legIndex: 1,
            cityMemo: null,
            weather: null,
            stayTonight: null,
            stayCheckout: null,
          },
        ],
        selectedDate: D1,
        archiveCount: 0,
        activities: [
          activity(1, "구로몬 시장", {
            name: "구로몬 시장",
            lat: 34.6656,
            lng: 135.5061,
            cityPlaceRef: "ChIJ_osaka",
          }),
          activity(2, "기요미즈데라", {
            name: "기요미즈데라",
            lat: 34.9949,
            lng: 135.785,
            cityPlaceRef: "ChIJ_kyoto",
          }),
        ],
        // 서버가 계산하지 않은 구간 — 수단도 소요 시간도 없이 온다.
        travelTimes: [
          {
            fromActivityId: 1,
            toActivityId: 2,
            mode: null,
            durationMinutes: null,
            distanceM: 42800,
            fallback: false,
            crossCity: true,
          },
        ],
        stayMove: null,
      }),
    ),
  );
}

test.describe("도시 경계 이동", () => {
  test("시간 대신 `도시 이동`만 뜨고, 탭하면 시트 없이 곧바로 지도로 나간다", async ({
    page,
    context,
  }) => {
    await mockBoard(page);
    await page.goto(`/travel/trips/${TRIP_ID}/board`);

    const row = page.getByRole("button", { name: "이동시간 도시 이동" });
    await expect(row).toBeVisible();
    // "약 42.8km"는 계획에 쓸 수 없는 숫자다 — 거리도 말하지 않는다.
    await expect(page.getByText(/km/)).toHaveCount(0);

    // 딥링크는 새 탭으로 열린다. 그 탭이 열리는 것 자체가 확인 대상이다.
    const opened = context.waitForEvent("page");
    await row.click();
    const mapTab = await opened;

    expect(mapTab.url()).toContain("google.com/maps/dir/");
    expect(mapTab.url()).toContain("travelmode=transit");
    // 도보/자동차를 물어볼 이유가 없다 — 이동수단 시트는 열리지 않는다.
    await expect(page.getByRole("dialog")).toHaveCount(0);
  });
});
