import { expect, type Page, test } from "@playwright/test";

/**
 * 도시를 넘는 구간의 이동(#1208).
 *
 * <p>이 파일이 원래 지키던 것은 그 반대였다 — 도시를 넘으면 <b>계산하지 않고</b> 시트 없이 곧바로
 * 지도로 나간다(§3.4, #1142). 자동 계산을 걷어내면서 그 규칙이 사라졌다. 비행기·신칸센이야말로
 * 미리 정해 두는 이동이라, 지금은 <b>여기가 적는 자리</b>다.
 *
 * <p>실제 클릭으로 확인한다 — 시트가 열리는지, 고른 수단이 요청에 실려 나가는지는 핸들러를
 * 갈아끼운 검사로는 구분되지 않는다.
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
  // 보드는 숙소 목록을 함께 읽는다(#1143). 이 화면에서 확인할 것은 아니라 비워 둔다.
  await page.route("**/api/travel/trips/*/stays", (route) =>
    route.fulfill(ok([])),
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
        // 아직 아무것도 적지 않은 구간 — 도시를 넘어도 다를 것이 없다.
        moves: [
          {
            fromActivityId: 1,
            toActivityId: 2,
            toStayId: null,
            mode: null,
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
}

test.describe("도시를 넘는 구간의 이동", () => {
  test("도시를 넘어도 시트가 열리고, 고른 수단과 적은 시간이 그대로 저장된다", async ({
    page,
  }) => {
    const saved: unknown[] = [];
    await page.route(`**/api/travel/trips/${TRIP_ID}/moves`, async (route) => {
      saved.push(route.request().postDataJSON());
      return route.fulfill(ok(null));
    });
    await mockBoard(page);
    await page.goto(`/travel/trips/${TRIP_ID}/board`);

    // 예전에는 여기가 `도시 이동` 넉 자였다. 지금은 적으라고 말한다.
    const row = page.getByRole("button", { name: "이동 이동 추가" });
    await expect(row).toBeVisible();
    await row.click();

    const sheet = page.getByRole("dialog");
    await expect(sheet).toBeVisible();
    await expect(sheet.getByText("구로몬 시장 → 기요미즈데라")).toBeVisible();

    await sheet.getByRole("button", { name: "기차" }).click();
    await sheet.getByLabel("이동수단 이름").fill("특급 하루카");
    await sheet.getByLabel("소요 시간(분)").fill("75");
    await sheet.getByRole("button", { name: "저장" }).click();

    await expect.poll(() => saved.length).toBe(1);
    expect(saved[0]).toMatchObject({
      fromActivityId: 1,
      toActivityId: 2,
      mode: "TRAIN",
      name: "특급 하루카",
      durationMinutes: 75,
    });
  });

  test("시간을 확인하러 나가는 통로는 대중교통 딥링크다", async ({
    page,
    context,
  }) => {
    // 앱이 계산하지 않으므로 사용자는 어딘가에서 시간을 봐야 한다. 그 통로가 이 버튼이다.
    await mockBoard(page);
    await page.goto(`/travel/trips/${TRIP_ID}/board`);

    await page.getByRole("button", { name: "이동 이동 추가" }).click();
    const sheet = page.getByRole("dialog");

    const opened = context.waitForEvent("page");
    await sheet
      .getByRole("button", { name: /구글 지도에서 시간 확인/ })
      .click();
    const mapTab = await opened;

    expect(mapTab.url()).toContain("google.com/maps/dir/");
    expect(mapTab.url()).toContain("travelmode=transit");
  });
});
