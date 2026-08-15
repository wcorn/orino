import { expect, type Page, test } from "@playwright/test";

/**
 * 숙소 배지·시트(S-04, #1143).
 *
 * <p>여기서 확인하는 것은 <b>등록한 것이 곧바로 배지에 보이는가</b>다. 숙소는 어느 날짜에
 * 붙는지를 저장하지 않고 기간에서 파생하므로, 저장 → 보드 무효화 → 배지까지가 한 흐름으로
 * 이어져야 한다. 한 군데만 끊겨도 "저장했는데 아무 일도 안 일어난" 화면이 된다.
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

interface Stay {
  stayId: number;
  name: string;
  placeId: number | null;
  checkInDate: string;
  checkOutDate: string;
  checkInTime: string | null;
  checkOutTime: string | null;
  bookingUrl: string | null;
  memo: string | null;
  nights: number;
}

/**
 * 서버를 흉내내되 <b>상태를 들고 있는다</b> — 등록한 숙소가 다음 보드 조회에 반영되어야
 * 이 흐름이 성립한다. 날짜 판정(`checkIn <= day < checkOut`)도 서버와 같은 규칙으로 한다.
 */
async function mockTrip(page: Page) {
  const stays: Stay[] = [];
  let nextId = 76;

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

  await page.route(`**/api/travel/trips/${TRIP_ID}/stays`, async (route) => {
    if (route.request().method() === "POST") {
      const body = route.request().postDataJSON() as Omit<
        Stay,
        "stayId" | "nights"
      >;
      const stay: Stay = { ...body, stayId: nextId++, nights: 1 };
      stays.push(stay);
      return route.fulfill(ok(stay));
    }
    return route.fulfill(ok(stays));
  });

  await page.route("**/api/travel/stays/*", (route) => {
    if (route.request().method() === "DELETE") {
      const id = Number(route.request().url().split("/").pop());
      stays.splice(
        stays.findIndex((s) => s.stayId === id),
        1,
      );
      return route.fulfill(ok(null));
    }
    return route.fulfill(ok(stays[0]));
  });

  await page.route(`**/api/travel/trips/${TRIP_ID}/board*`, (route) => {
    const url = new URL(route.request().url());
    const date = url.searchParams.get("date") ?? D1;
    const tonightOn = (day: string) =>
      stays.find((s) => s.checkInDate <= day && day < s.checkOutDate) ?? null;
    const checkoutOn = (day: string) =>
      stays.find((s) => s.checkOutDate === day) ?? null;

    return route.fulfill(
      ok({
        trip: {
          id: TRIP_ID,
          title: "도쿄",
          startDate: D1,
          endDate: D2,
          status: "UPCOMING",
          recordMode: false,
          cityCount: 1,
          countryCount: 1,
          singleCity: true,
        },
        days: [D1, D2].map((day, index) => {
          const tonight = tonightOn(day);
          const checkout = checkoutOn(day);
          return {
            dayId: 501 + index,
            dayIndex: index + 1,
            date: day,
            weekday: index === 0 ? "토" : "일",
            activityCount: 0,
            baseCity: TOKYO,
            cityChanged: false,
            legIndex: 1,
            cityMemo: null,
            weather: null,
            stayTonight: tonight && {
              stayId: tonight.stayId,
              name: tonight.name,
              sameCity: true,
              checkInTime: tonight.checkInTime,
              isCheckInDay: tonight.checkInDate === day,
            },
            stayCheckout: checkout && {
              stayId: checkout.stayId,
              name: checkout.name,
              checkOutTime: checkout.checkOutTime,
            },
          };
        }),
        selectedDate: date,
        archiveCount: 0,
        activities: [],
        moves: [],
        stayMove: null,
      }),
    );
  });
}

test.describe("숙소", () => {
  test("등록하면 배지에 뜨고, 삭제하면 `숙소 추가`로 돌아간다", async ({
    page,
  }) => {
    await mockTrip(page);
    await page.goto(`/travel/trips/${TRIP_ID}/board`);

    // 아직 아무것도 없다.
    const addButton = page.getByRole("button", { name: "숙소 추가" });
    await expect(addButton).toBeVisible();

    await addButton.click();
    await page.getByLabel("이름").fill("도톤보리 호텔");
    await page.getByLabel("체크인", { exact: true }).fill(D1);
    await page.getByLabel("체크아웃", { exact: true }).fill(D2);
    await page.getByLabel("체크인 시각").fill("15:00");
    await page.getByRole("button", { name: "저장" }).click();

    // 저장 → 보드 무효화 → 배지. 체크인하는 날이라 시각이 함께 붙는다.
    const badge = page.getByRole("button", {
      name: "숙소 도톤보리 호텔 · 오늘 체크인 15:00",
    });
    await expect(badge).toBeVisible();

    await badge.click();
    const sheet = page.getByRole("dialog");
    await expect(sheet.getByText("도톤보리 호텔")).toBeVisible();

    await sheet.getByRole("button", { name: "삭제" }).click();
    await expect(
      page.getByText("이 숙소가 붙어 있던 날짜에서 모두 사라집니다."),
    ).toBeVisible();
    await page.getByRole("button", { name: "삭제" }).click();

    await expect(page.getByRole("button", { name: "숙소 추가" })).toBeVisible();
  });
});
