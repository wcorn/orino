import { expect, type Page, test } from "./support/test";

/**
 * 스와이프 액션(#1038). 지우기·보관함은 <b>누르는 즉시 요청이 나간다</b>(#1432) — 예전에는
 * 실행취소를 위해 5초 보류했다.
 *
 * 스와이프는 <b>터치 포인터</b>로만 재현된다 — 마우스는 스와이프하지 않고 버튼을 쓴다.
 * 그래서 이 파일은 `mobile-touch` 프로젝트에서 의미가 있고, 데스크톱 실행에서는
 * 버튼 경로만 확인한다.
 */

const TRIP_ID = 1;
const DAY = "2026-10-24";

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

function activity(id: number, title: string, sortOrder: number) {
  return {
    id,
    tripId: TRIP_ID,
    title,
    activityDate: DAY,
    startTime: null,
    place: null,
    memo: null,
    url: null,
    notifyEnabled: false,
    notifyMinutes: null,
    departureNotifyEnabled: false,
    sortOrder,
    hasLog: false,
  };
}

interface Captured {
  deletes: string[];
  updates: Record<string, unknown>[];
}

async function mockBoard(page: Page): Promise<Captured> {
  const captured: Captured = { deletes: [], updates: [] };
  // 진짜 서버처럼 지우거나 보관함으로 내린 일정은 그날 목록에서 뺀다 — 요청 뒤 보드를
  // 다시 받아도 돌아오지 않아야 한다.
  const gone = new Set<string>();

  await page.route("**/api/auth/reissue", (route) =>
    route.fulfill(ok({ accessToken: "mock-access-token" })),
  );
  await page.route("**/api/planner/reviews/summary", (route) =>
    route.fulfill(
      ok({
        today: DAY,
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
  await page.route("**/api/travel/activities/*", (route) => {
    const method = route.request().method();
    const id = route.request().url().split("/").pop() ?? "";
    if (method === "DELETE") {
      captured.deletes.push(id);
      gone.add(id);
      return route.fulfill(ok(null));
    }
    const body = route.request().postDataJSON() as Record<string, unknown>;
    captured.updates.push(body);
    if (body.activityDate === null) gone.add(id);
    return route.fulfill(ok(activity(1, "센소지", 0)));
  });
  await page.route(`**/api/travel/trips/${TRIP_ID}/board*`, (route) => {
    const isArchive = new URL(route.request().url()).searchParams.get(
      "archive",
    );
    return route.fulfill(
      ok({
        trip: {
          id: TRIP_ID,
          title: "도쿄",
          startDate: DAY,
          endDate: DAY,
          status: "UPCOMING",
          recordMode: false,
          cityCount: 1,
          countryCount: 1,
          singleCity: true,
        },
        days: [
          {
            dayId: 501,
            dayIndex: 1,
            date: DAY,
            weekday: "토",
            activityCount: 2,
            baseCity: TOKYO,
            cityChanged: false,
            legIndex: 1,
            cityMemo: null,
            weather: null,
            stayTonight: null,
            stayCheckout: null,
          },
        ],
        selectedDate: isArchive === "true" ? null : DAY,
        archiveCount: 0,
        activities:
          isArchive === "true"
            ? []
            : [activity(1, "센소지", 0), activity(2, "우에노", 1)].filter(
                (a) => !gone.has(String(a.id)),
              ),
        moves: [],
        stayMove: null,
      }),
    );
  });

  return captured;
}

/** 손가락으로 행을 가로로 민다. */
async function swipeRow(page: Page, text: string, dx: number) {
  const box = await page.getByText(text, { exact: true }).boundingBox();
  if (!box) throw new Error("행을 찾지 못했다");
  const y = box.y + box.height / 2;
  const startX = box.x + box.width / 2;

  await page.dispatchEvent(`text="${text}"`, "pointerdown", {
    pointerType: "touch",
    clientX: startX,
    clientY: y,
    isPrimary: true,
  });
  for (let i = 1; i <= 4; i++) {
    await page.dispatchEvent(`text="${text}"`, "pointermove", {
      pointerType: "touch",
      clientX: startX + (dx * i) / 4,
      clientY: y,
      isPrimary: true,
    });
  }
  await page.dispatchEvent(`text="${text}"`, "pointerup", {
    pointerType: "touch",
    clientX: startX + dx,
    clientY: y,
    isPrimary: true,
  });
}

test.describe("스와이프", () => {
  test("왼쪽으로 밀면 곧바로 삭제 요청이 나간다 — 5초를 기다리지 않는다", async ({
    page,
  }) => {
    const captured = await mockBoard(page);
    await page.goto(`/travel/trips/${TRIP_ID}/board`);
    await expect(page.getByText("센소지")).toBeVisible();

    await swipeRow(page, "센소지", -120);

    await expect(page.getByText("센소지", { exact: true })).toBeHidden();
    // 5초 보류가 남아 있으면 이 짧은 대기 안에 요청이 나가지 않는다.
    await expect.poll(() => captured.deletes, { timeout: 2000 }).toEqual(["1"]);
    await expect(page.getByText('"센소지"을(를) 삭제했어요.')).toBeVisible();
    await expect(page.getByRole("button", { name: /실행취소/ })).toHaveCount(0);
    // 다시 받아 온 보드에서도 돌아오지 않는다.
    await expect(page.getByText("센소지", { exact: true })).toBeHidden();
  });

  test("오른쪽으로 밀면 곧바로 보관함으로 간다", async ({ page }) => {
    const captured = await mockBoard(page);
    await page.goto(`/travel/trips/${TRIP_ID}/board`);
    await expect(page.getByText("센소지")).toBeVisible();

    await swipeRow(page, "센소지", 120);

    await expect(page.getByText("센소지", { exact: true })).toBeHidden();
    await expect
      .poll(() => captured.updates.map((u) => u.activityDate), {
        timeout: 2000,
      })
      .toEqual([null]);
    await expect(
      page.getByText('"센소지"을(를) 보관함으로 옮겼어요.'),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: /실행취소/ })).toHaveCount(0);
  });

  test("살짝만 밀면 아무 일도 없다", async ({ page }) => {
    const captured = await mockBoard(page);
    await page.goto(`/travel/trips/${TRIP_ID}/board`);
    await expect(page.getByText("센소지")).toBeVisible();

    await swipeRow(page, "센소지", -40);

    await expect(page.getByText("센소지")).toBeVisible();
    expect(captured.deletes).toHaveLength(0);
  });
});
