import { expect, type Page, test } from "@playwright/test";

/**
 * 지도 `이 날짜` / `전체` 토글(S-05, #1144).
 *
 * <p>여기서 확인하는 것은 <b>보던 모드가 새로고침에서 살아남는가</b>다. 상태를 URL이 소유해야
 * 한다는 규칙(§9.7)은 진짜 주소창과 진짜 새로고침으로만 증명된다.
 *
 * <p>마커 자체는 확인하지 않는다 — 구글 지도 SDK는 여기서 뜨지 않는다. 도시당 하나·첫 방문
 * 번호 규칙은 `lib/cityMarkers` 단위 테스트가, 핀을 실제로 찍는 일은 `TripMap` 테스트가 맡는다.
 */

const TRIP_ID = 1;
const D1 = "2026-10-24";
const D2 = "2026-10-25";

const ok = (data: unknown) => ({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ code: "OK", data }),
});

function city(placeId: number, name: string) {
  return {
    placeId,
    name,
    timezone: "Asia/Tokyo",
    currency: "JPY",
    countryCode: "JP",
    cityPlaceRef: `ChIJ_${placeId}`,
    lat: 35.68,
    lng: 139.76,
  };
}

/** 도쿄 → 닛코 → 도쿄. 구간 셋, 도시 둘. */
const CITY_LEGS = [
  {
    legIndex: 1,
    cityPlaceId: 21,
    cityName: "도쿄",
    days: 1,
    startDate: D1,
    endDate: D1,
    timezone: "Asia/Tokyo",
    lat: 35.68,
    lng: 139.76,
  },
  {
    legIndex: 2,
    cityPlaceId: 22,
    cityName: "닛코",
    days: 1,
    startDate: D2,
    endDate: D2,
    timezone: "Asia/Tokyo",
    lat: 36.75,
    lng: 139.6,
  },
];

async function mockTrip(page: Page) {
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
  await page.route("**/api/travel/trips/*/city-legs", (route) =>
    route.fulfill(ok(CITY_LEGS)),
  );

  await page.route(`**/api/travel/trips/${TRIP_ID}/board*`, (route) => {
    const date = new URL(route.request().url()).searchParams.get("date") ?? D1;
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
          {
            dayId: 501,
            dayIndex: 1,
            date: D1,
            weekday: "토",
            activityCount: 1,
            baseCity: city(21, "도쿄"),
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
            date: D2,
            weekday: "일",
            activityCount: 0,
            baseCity: city(22, "닛코"),
            cityChanged: true,
            legIndex: 2,
            cityMemo: null,
            weather: null,
            stayTonight: null,
            stayCheckout: null,
          },
        ],
        selectedDate: date,
        archiveCount: 0,
        activities:
          date === D1
            ? [
                {
                  id: 1,
                  tripId: TRIP_ID,
                  title: "센소지",
                  activityDate: D1,
                  startTime: "09:00",
                  place: {
                    id: 10,
                    name: "센소지",
                    address: "다이토구",
                    lat: 35.7147,
                    lng: 139.7966,
                    cityName: "도쿄",
                    cityPlaceRef: "ChIJ_21",
                  },
                  memo: null,
                  url: null,
                  notifyEnabled: false,
                  notifyMinutes: null,
                  departureNotifyEnabled: false,
                  sortOrder: 0,
                  log: null,
                  hasLog: false,
                  outOfBaseCity: false,
                  canDepartureNotify: false,
                },
              ]
            : [],
        moves: [],
        stayMove: null,
      }),
    );
  });
}

test.describe("지도 범위 토글", () => {
  test("전체로 바꾸면 새로고침해도 그대로고, 구간 행이 그 구간 첫날로 데려간다", async ({
    page,
  }) => {
    await mockTrip(page);
    await page.goto(`/travel/trips/${TRIP_ID}/map`);

    // 기본은 하루다.
    await expect(
      page.getByRole("heading", { name: "1일차 동선" }),
    ).toBeVisible();

    await page.getByRole("button", { name: "전체" }).click();
    await expect(
      page.getByRole("heading", { name: "여행 전체" }),
    ).toBeVisible();
    await expect(page.getByText("도시 2곳 · 구간 순서")).toBeVisible();

    // 상태를 URL이 갖는다 — 주소에 남고 새로고침을 넘긴다(§9.7).
    expect(page.url()).toContain("mode=all");
    await page.reload();
    await expect(
      page.getByRole("heading", { name: "여행 전체" }),
    ).toBeVisible();

    // 구간 행을 누르면 그 구간 첫날 보드가 열린다.
    await page.getByRole("button", { name: /닛코/ }).click();
    await expect(page.getByRole("tab").nth(1)).toHaveAttribute(
      "aria-selected",
      "true",
    );
  });
});
