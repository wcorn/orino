import { expect, type Page, test } from "@playwright/test";

/**
 * 구간 입력 화면(S-03, #1132).
 *
 * <p>여기서 확인하는 것은 <b>일수로 넣은 구간이 진짜 브라우저에서 날짜로 펴지는가</b>다.
 * 단위 테스트가 계산을 보증해도, 실제 번들에서 도시 시트 → 구간 목록 → 저장 요청까지
 * 이어지는 길은 브라우저에서만 드러난다.
 */

const ok = (data: unknown) => ({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ code: "OK", data }),
});

const CITIES = [
  {
    googlePlaceId: "ChIJ_tokyo",
    name: "도쿄",
    address: "일본 도쿄도",
    lat: 35.6764,
    lng: 139.65,
    timezone: "Asia/Tokyo",
    currency: "JPY",
  },
  {
    googlePlaceId: "ChIJ_honolulu",
    name: "호놀룰루",
    address: "미국 하와이",
    lat: 21.3069,
    lng: -157.8583,
    timezone: "Pacific/Honolulu",
    currency: "USD",
  },
];

async function mockForm(page: Page): Promise<Record<string, unknown>[]> {
  const created: Record<string, unknown>[] = [];

  await page.route("**/api/auth/reissue", (route) =>
    route.fulfill(ok({ accessToken: "mock-access-token" })),
  );
  await page.route("**/api/travel/places/cities*", (route) => {
    const q = new URL(route.request().url()).searchParams.get("q") ?? "";
    return route.fulfill(ok(CITIES.filter((city) => city.name.includes(q))));
  });
  await page.route("**/api/travel/trips", (route) => {
    if (route.request().method() !== "POST") return route.continue();
    created.push(route.request().postDataJSON() as Record<string, unknown>);
    return route.fulfill(ok({ id: 9, title: "일본" }));
  });
  await page.route("**/api/travel/trips/9/board*", (route) =>
    route.fulfill(
      ok({
        trip: {
          id: 9,
          title: "일본",
          startDate: "2026-10-24",
          endDate: "2026-10-27",
          status: "UPCOMING",
          recordMode: false,
          cityCount: 2,
          countryCount: 2,
          singleCity: false,
        },
        days: [],
        selectedDate: null,
        archiveCount: 0,
        activities: [],
        travelTimes: [],
        stayMove: null,
      }),
    ),
  );

  return created;
}

/** 구간 추가 → 시트에서 검색 → 결과 선택. 도시를 정하는 유일한 경로다. */
async function addLeg(page: Page, cityName: string) {
  await page.getByRole("button", { name: "구간 추가" }).click();
  await page.getByLabel("도시 검색").fill(cityName);
  await page.getByRole("button", { name: "검색" }).click();
  await page.getByRole("button", { name: new RegExp(cityName) }).click();
}

test.describe("구간 입력", () => {
  test("일수로 넣은 구간이 날짜로 펴져 보인다", async ({ page }) => {
    await mockForm(page);
    await page.goto("/travel/trips/new");

    await page.getByLabel("여행 제목").fill("일본");
    await page.getByLabel("시작일").fill("2026-10-24");
    await page.getByLabel("종료일").fill("2026-10-27");

    await addLeg(page, "도쿄");
    await page.getByRole("button", { name: "도쿄 일수 늘리기" }).click();
    await addLeg(page, "호놀룰루");

    // 2일 + 1일 = 3일, 기간은 4일 — 남은 하루는 마지막 도시가 이어 쓴다.
    await expect(page.getByText("10.24 – 10.25")).toBeVisible();
    await expect(page.getByText("10.26 – 10.27")).toBeVisible();
    await expect(page.getByText(/1일 남음/)).toBeVisible();

    // 타임존이 둘이라는 사실을 그 자리에서 말한다.
    await expect(page.getByText("타임존이 2개예요")).toBeVisible();
  });

  test("순서를 바꾸면 날짜도 따라 바뀌고, 고른 그대로 저장 요청에 실린다", async ({
    page,
  }) => {
    const created = await mockForm(page);
    await page.goto("/travel/trips/new");

    await page.getByLabel("여행 제목").fill("일본");
    await page.getByLabel("시작일").fill("2026-10-24");
    await page.getByLabel("종료일").fill("2026-10-27");

    await addLeg(page, "도쿄");
    await page.getByRole("button", { name: "도쿄 일수 늘리기" }).click();
    await addLeg(page, "호놀룰루");
    await page.getByRole("button", { name: "호놀룰루 위로" }).click();

    await expect(page.getByText("10.24")).toBeVisible();
    await page.getByRole("button", { name: "만들기" }).click();

    await expect(page).toHaveURL(/\/travel\/trips\/9\/board/);
    expect(created).toHaveLength(1);
    expect(created[0].legs).toEqual([
      { cityGooglePlaceId: "ChIJ_honolulu", days: 1 },
      { cityGooglePlaceId: "ChIJ_tokyo", days: 2 },
    ]);
    // 타임존·통화는 도시가 갖는다 — 여행이 따로 들고 있지 않다.
    expect(created[0]).not.toHaveProperty("timezone");
  });

  test("기간을 넘긴 구간은 잘린다고 미리 말한다", async ({ page }) => {
    await mockForm(page);
    await page.goto("/travel/trips/new");

    await page.getByLabel("시작일").fill("2026-10-24");
    await page.getByLabel("종료일").fill("2026-10-25");

    await addLeg(page, "도쿄");
    await page.getByRole("button", { name: "도쿄 일수 늘리기" }).click();
    await addLeg(page, "호놀룰루");

    await expect(page.getByText("기간을 넘겨 잘려요")).toBeVisible();
    await expect(page.getByText(/1일 초과/)).toBeVisible();
  });
});
