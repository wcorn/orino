import { expect, type Page, test } from "./support/test";

/**
 * 검색 기준 도시 칩(S-06, #1145).
 *
 * <p>여기서 확인하는 것은 <b>고른 도시가 새로고침에서 살아남는가</b>다. 상태를 URL이 소유해야
 * 한다는 규칙(§9.7)은 진짜 주소창과 진짜 새로고침으로만 증명된다 — 메모리 라우터 위에서는
 * 컴포넌트 state와 구분되지 않는다.
 */

const TRIP_ID = 1;
const D1 = "2026-10-24";
const D2 = "2026-10-25";

function city(placeId: number, name: string, cityPlaceRef: string) {
  return {
    placeId,
    name,
    timezone: "Asia/Tokyo",
    currency: "JPY",
    countryCode: "JP",
    cityPlaceRef,
    lat: 34.6937,
    lng: 135.5023,
  };
}

const OSAKA = city(21, "오사카", "ChIJ_osaka");
const KYOTO = city(22, "교토", "ChIJ_kyoto");

const ok = (data: unknown) => ({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ code: "OK", data }),
});

async function mockTrip(page: Page) {
  const searches: URL[] = [];
  const created: Record<string, unknown>[] = [];

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

  await page.route("**/api/travel/places/search*", (route) => {
    searches.push(new URL(route.request().url()));
    return route.fulfill(
      ok([
        {
          id: null,
          googlePlaceId: "ChIJ_nishiki",
          name: "니시키 시장",
          category: "시장",
          address: "교토부 교토시",
          rating: 4.3,
          lat: 35.0049,
          lng: 135.7649,
        },
      ]),
    );
  });

  await page.route(`**/api/travel/trips/${TRIP_ID}/activities`, (route) => {
    created.push(route.request().postDataJSON() as Record<string, unknown>);
    return route.fulfill(ok({ id: 1 }));
  });

  await page.route(`**/api/travel/trips/${TRIP_ID}/board*`, (route) =>
    route.fulfill(
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
            activityCount: 0,
            baseCity: OSAKA,
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
            baseCity: KYOTO,
            cityChanged: true,
            legIndex: 2,
            cityMemo: null,
            weather: null,
            stayTonight: null,
            stayCheckout: null,
          },
        ],
        selectedDate: D1,
        archiveCount: 0,
        activities: [],
        moves: [],
        stayMove: null,
      }),
    ),
  );

  return { searches, created };
}

test.describe("검색 기준 도시", () => {
  test("도시를 바꾸면 새로고침해도 그대로고, 담을 때 그 도시가 함께 간다", async ({
    page,
  }) => {
    const { searches, created } = await mockTrip(page);
    await page.goto(`/travel/trips/${TRIP_ID}/places?q=시장`);

    // 보던 날짜(1일차)가 오사카라 기준도 오사카다.
    await expect(
      page.getByRole("button", { name: "검색 기준 도시 오사카" }),
    ).toBeVisible();
    await expect
      .poll(() => searches.at(-1)?.searchParams.get("city"))
      .toBe("21");

    await page.getByRole("button", { name: "검색 기준 도시 오사카" }).click();
    await page
      .getByRole("dialog")
      .getByRole("button", { name: "교토" })
      .click();

    await expect(
      page.getByRole("button", { name: "검색 기준 도시 교토" }),
    ).toBeVisible();
    await expect
      .poll(() => searches.at(-1)?.searchParams.get("city"))
      .toBe("22");

    // 상태를 URL이 갖는다 — 주소에 남고 새로고침을 넘긴다(§9.7).
    expect(page.url()).toContain("city=22");
    expect(page.url()).toContain("q=");
    await page.reload();
    await expect(
      page.getByRole("button", { name: "검색 기준 도시 교토" }),
    ).toBeVisible();
    await expect(page.getByLabel("장소 검색")).toHaveAttribute(
      "placeholder",
      "교토 주변 장소 검색",
    );

    // 담으면 그 도시 식별자가 함께 저장된다 — 보관함 도시 그룹이 여기서 살아난다.
    await page.getByRole("button", { name: "담기" }).click();
    await page.getByRole("combobox", { name: "날짜" }).click();
    await page.getByRole("option", { name: /2일차/ }).click();
    await page.getByRole("button", { name: "저장" }).click();

    await expect.poll(() => created.length).toBe(1);
    expect(created[0].cityPlaceId).toBe(22);
    expect(created[0].googlePlaceId).toBe("ChIJ_nishiki");
  });
});
