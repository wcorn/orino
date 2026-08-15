import { expect, type Page, test } from "@playwright/test";

/**
 * 일정 상세의 도시 경계 안내(S-07, #1168).
 *
 * <p>여기서 확인하는 것은 <b>못 켜는 스위치가 실제로 안 눌리는가</b>다. `disabled` 속성만
 * 보는 검사는 눌렀을 때 상태가 바뀌지 않는지까지는 말해 주지 않는다.
 */

const D1 = "2026-10-24";

const ok = (data: unknown) => ({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ code: "OK", data }),
});

const OSAKA_PLACE = {
  id: 12,
  name: "구로몬 시장",
  address: "오사카시",
  lat: 34.6656,
  lng: 135.5061,
  cityName: "오사카",
  cityPlaceRef: "ChIJ_osaka",
};

const KYOTO_PLACE = {
  id: 11,
  name: "기요미즈데라",
  address: "교토시",
  lat: 34.9949,
  lng: 135.785,
  cityName: "교토",
  cityPlaceRef: "ChIJ_kyoto",
};

function activity(id: number, title: string, place: unknown) {
  return {
    id,
    tripId: 3,
    title,
    activityDate: D1,
    startTime: "09:00",
    place,
    memo: null,
    url: null,
    notifyEnabled: false,
    notifyMinutes: null,
    departureNotifyEnabled: false,
    sortOrder: id,
    log: null,
    hasLog: false,
    outOfBaseCity: true,
    // 직전에 장소 있는 일정이 있으므로 true다. #1208 전에는 "도시를 넘는다"는 이유로
    // 서버가 false를 내려 스위치가 막혀 있었다 — 그 조건이 빠졌다.
    canDepartureNotify: true,
  };
}

async function mockTrip(page: Page) {
  const saved: Record<string, unknown>[] = [];

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
        ...KYOTO_PLACE,
        googlePlaceId: null,
        openingHours: null,
        phone: null,
        category: null,
        rating: null,
        manualEntry: false,
      }),
    ),
  );

  await page.route("**/api/travel/activities/1", (route) => {
    if (route.request().method() === "PUT") {
      saved.push(route.request().postDataJSON() as Record<string, unknown>);
      return route.fulfill(ok(activity(1, "기요미즈데라", KYOTO_PLACE)));
    }
    return route.fulfill(ok(activity(1, "기요미즈데라", KYOTO_PLACE)));
  });

  await page.route("**/api/travel/trips/3", (route) =>
    route.fulfill(
      ok({
        id: 3,
        title: "일본",
        destinationName: "오사카",
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
        activityCount: 2,
        cities: {
          names: ["오사카", "교토"],
          count: 2,
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
          cityCount: 2,
          countryCount: 1,
          singleCity: false,
        },
        days: [
          {
            dayId: 1,
            dayIndex: 1,
            date: D1,
            weekday: "토",
            activityCount: 2,
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
        activities: [
          activity(9, "구로몬 시장", OSAKA_PLACE),
          activity(1, "기요미즈데라", KYOTO_PLACE),
        ],
        // 수단은 골랐지만 소요 시간을 아직 안 적은 구간(#1208).
        moves: [
          {
            fromActivityId: 9,
            toActivityId: 1,
            toStayId: null,
            mode: "TRAIN",
            name: null,
            durationMinutes: null,
            url: null,
            memo: null,
          },
        ],
        stayMove: null,
      }),
    ),
  );

  return saved;
}

test.describe("일정 상세 · 도시를 넘어 들어오는 일정", () => {
  test("도시를 넘어도 출발 알림을 켤 수 있고, 시간을 안 적었으면 왜 안 오는지 적혀 있다", async ({
    page,
  }) => {
    // #1208 전에는 도시를 넘으면 소요 시간을 못 구해 스위치가 막혀 있었다.
    // 지금은 사용자가 적을 수 있으므로 막을 이유가 없다 — 다만 아직 안 적었으면
    // 알림이 서지 않고, 그 이유를 말해 주지 않으면 그냥 고장으로 읽힌다.
    await mockTrip(page);
    await page.goto("/travel/activities/1");

    // 부제가 며칠째의 어느 도시인지 말한다.
    await expect(page.getByText("1일차 · 교토 · 10.24")).toBeVisible();
    await expect(
      page.getByText("보드에서 이동 시간을 적으면 알림이 잡혀요"),
    ).toBeVisible();
    // 도시 경계 안내는 사라졌다 — 넘어도 되는 일이 됐다.
    await expect(page.getByText(/도시 경계를 넘어/)).toHaveCount(0);

    const departure = page.getByRole("switch", { name: "출발 알림" });
    await expect(departure).toBeEnabled();
    await departure.click();
    await expect(departure).toHaveAttribute("aria-checked", "true");

    await expect(page.getByRole("switch", { name: "일정 알림" })).toBeEnabled();
  });
});
