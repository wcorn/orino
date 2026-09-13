import { expect, type Page, test } from "./support/test";

/**
 * 일정 상세에서 장소 바꾸기(#1396).
 *
 * <p>상세 → 검색(교체 모드) → 선택 → 상세로 돌아와 <b>새 장소가 보이는가</b>까지 한 번에 본다.
 * 화면 두 개를 오가는 흐름이라 단위 테스트로는 브라우저 이력·재조회가 실제로 맞물리는지 모른다.
 */

const D1 = "2026-10-24";

const ok = (data: unknown) => ({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ code: "OK", data }),
});

const KYOTO_PLACE = {
  id: 11,
  name: "기요미즈데라",
  address: "교토시",
  lat: 34.9949,
  lng: 135.785,
  cityName: "교토",
  adminArea: null,
  cityPlaceRef: "ChIJ_kyoto",
};

const SENSO_PLACE = {
  id: 30,
  name: "센소지",
  address: "도쿄도 다이토구",
  lat: 35.7147,
  lng: 139.7966,
  cityName: "도쿄",
  adminArea: null,
  cityPlaceRef: null,
};

function activity(place: unknown) {
  return {
    id: 1,
    tripId: 3,
    title: "오전 사찰",
    activityDate: D1,
    startTime: "09:00",
    place,
    memo: "입장권 미리",
    url: null,
    notifyEnabled: false,
    notifyMinutes: null,
    departureNotifyEnabled: false,
    sortOrder: 0,
    log: null,
    hasLog: false,
    outOfBaseCity: false,
    canDepartureNotify: false,
  };
}

async function mockTrip(page: Page) {
  const saved: Record<string, unknown>[] = [];
  let current: unknown = KYOTO_PLACE;

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
  await page.route("**/api/travel/places/*", (route) =>
    route.fulfill(
      ok({
        ...(current as object),
        googlePlaceId: null,
        openingHours: null,
        phone: null,
        category: null,
        rating: null,
        manualEntry: false,
      }),
    ),
  );
  // 위의 상세 조회보다 나중에 등록해야 검색이 이긴다(나중 등록이 먼저 매칭된다).
  await page.route("**/api/travel/places/search*", (route) =>
    route.fulfill(
      ok([
        {
          id: null,
          googlePlaceId: "ChIJ_senso",
          name: "센소지",
          category: "불교사찰",
          address: "도쿄도 다이토구",
          rating: 4.6,
          lat: 35.7147,
          lng: 139.7966,
        },
      ]),
    ),
  );

  await page.route("**/api/travel/activities/1", (route) => {
    if (route.request().method() === "PUT") {
      saved.push(route.request().postDataJSON() as Record<string, unknown>);
      current = SENSO_PLACE;
    }
    return route.fulfill(ok(activity(current)));
  });

  await page.route("**/api/travel/trips/3", (route) =>
    route.fulfill(
      ok({
        id: 3,
        title: "일본",
        destinationName: "교토",
        destinationPlaceId: null,
        startDate: D1,
        endDate: D1,
        timezone: "Asia/Tokyo",
        currency: "JPY",
        lat: null,
        lng: null,
        defaultNotifyMinutes: 15,
        morningSummaryEnabled: true,
        status: "UPCOMING",
        dDay: 10,
        totalDays: 1,
        activityCount: 1,
        cities: {
          names: ["교토"],
          count: 1,
          today: null,
          movedFrom: null,
          todayDayIndex: null,
          todayTimezone: null,
          todayCurrency: null,
        },
      }),
    ),
  );

  await page.route("**/api/travel/trips/3/board*", (route) =>
    route.fulfill(
      ok({
        trip: {
          id: 3,
          title: "일본",
          startDate: D1,
          endDate: D1,
          status: "UPCOMING",
          recordMode: false,
          cityCount: 1,
          countryCount: 1,
          singleCity: true,
        },
        days: [
          {
            dayId: 1,
            dayIndex: 1,
            date: D1,
            weekday: "토",
            activityCount: 1,
            baseCity: {
              placeId: 21,
              name: "교토",
              timezone: "Asia/Tokyo",
              currency: "JPY",
              countryCode: "JP",
              cityPlaceRef: "ChIJ_kyoto",
              lat: null,
              lng: null,
            },
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
        activities: [activity(current)],
        moves: [],
        stayMove: null,
      }),
    ),
  );

  return saved;
}

test.describe("일정 상세 · 장소 바꾸기", () => {
  test("장소 변경 → 검색에서 선택 → 상세로 돌아와 새 장소가 보인다", async ({
    page,
  }) => {
    const saved = await mockTrip(page);
    await page.goto("/travel/activities/1");

    await expect(page.getByText("기요미즈데라", { exact: true })).toBeVisible();
    await page.getByRole("button", { name: "장소 변경" }).click();

    await expect(
      page.getByText("“오전 사찰” 일정의 장소를 바꿔요"),
    ).toBeVisible();
    const search = page.getByLabel("장소 검색");
    await search.fill("센소지");
    await search.press("Enter");
    await page.getByRole("button", { name: "선택" }).click();

    // 상세로 돌아와 새 장소를 다시 읽어 보여준다. 제목·메모는 그대로다.
    await expect(page).toHaveURL(/\/travel\/activities\/1$/);
    await expect(page.getByText("센소지", { exact: true })).toBeVisible();
    await expect(page.getByLabel("제목")).toHaveValue("오전 사찰");
    await expect(page.getByLabel("메모")).toHaveValue("입장권 미리");

    expect(saved).toHaveLength(1);
    expect(saved[0]).toMatchObject({
      googlePlaceId: "ChIJ_senso",
      placeId: null,
      cityPlaceId: 21,
      title: "오전 사찰",
      memo: "입장권 미리",
    });
  });
});
