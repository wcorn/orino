import { expect, type Page, test } from "./support/test";

/**
 * 경비 한 바퀴(S-11, #1407).
 *
 * <p>여기서 확인하는 것은 <b>여행 중 하루의 한 흐름</b>이다 — 금액만 찍어 적고, 잘못 적은
 * 것을 눌러 고치고, 지난 예정을 확정하고, 필요 없는 줄을 지운다. 통합 테스트가 각 조각을
 * 보는 것과 달리 이 스펙은 그 조각들이 <b>한 화면에서 이어지는지</b>를 본다.
 *
 * <p>그리고 이 화면이 <b>가계부 없이 도는지</b>를 본다. 예전에는 저장이 가계부 API로
 * 나갔고 줄을 누르면 가계부 지출 상세로 떠났다 — 이제 둘 다 여행 안에서 끝난다.
 */

const TRIP_ID = 1;
const START = "2026-10-24";
const END = "2026-10-26";
/** 오늘은 2일차다. 어제 잡아 둔 예정이 「지난 예정」이 되게 기간 가운데에 둔다. */
const TODAY = "2026-10-25";

const ok = (data: unknown) => ({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ code: "OK", data }),
});

type Status = "CONFIRMED" | "SCHEDULED";

interface StoredExpense {
  id: number;
  occurredOn: string;
  title: string | null;
  amount: number;
  category: string | null;
  paymentMethod: string | null;
  status: Status;
}

/**
 * 서버를 흉내내되 <b>상태를 들고 있는다</b>. 경비는 적고 → 고치고 → 지우는 흐름이 이어져야
 * 성립하는 화면이라, 고정 응답으로는 「적었는데 목록에 없다」를 못 잡는다.
 */
async function mockExpenses(page: Page) {
  const expenses: StoredExpense[] = [];
  let nextId = 100;

  await page.route("**/api/auth/reissue", (route) =>
    route.fulfill(ok({ accessToken: "mock-access-token" })),
  );
  await page.route("**/api/travel/summary", (route) =>
    route.fulfill(
      ok({
        ongoing: null,
        next: null,
        recentCompleted: null,
        trips: [],
        completedCount: 0,
      }),
    ),
  );
  await page.route("**/api/planner/reviews/summary", (route) =>
    route.fulfill(
      ok({
        today: TODAY,
        counts: { now: 0, overdue: 0, upcoming: 0, doneToday: 0 },
        estimatedMinutes: 0,
        materials: [],
      }),
    ),
  );

  const dayNumber = (date: string) =>
    Math.round(
      (Date.parse(`${date}T00:00:00Z`) - Date.parse(`${START}T00:00:00Z`)) /
        86_400_000,
    ) + 1;

  const view = (stored: StoredExpense) => ({
    expenseId: stored.id,
    title: stored.title,
    amount: stored.amount,
    fx: null,
    status: stored.status,
    category: stored.category,
    paymentMethod: stored.paymentMethod,
    uncategorized: stored.category === null,
    // 지난 예정 — 서버와 같은 규칙이다. 올려 주는 배치가 없으므로 표시만 한다(§4.3).
    overdue: stored.status === "SCHEDULED" && stored.occurredOn < TODAY,
    occurredOn: stored.occurredOn,
  });

  /** 기간 안의 날짜는 지출이 없어도 내려간다 — 화면이 「아직 적은 게 없어요」를 그린다. */
  const groups = () =>
    ["2026-10-24", "2026-10-25", "2026-10-26"].map((date) => {
      const rows = expenses.filter((e) => e.occurredOn === date);
      return {
        key: `DAY-${dayNumber(date)}`,
        label: `${Number(date.slice(5, 7))}.${date.slice(8)} · 오사카`,
        dayNumber: dayNumber(date),
        date,
        cityName: "오사카",
        sum: rows.reduce((total, e) => total + e.amount, 0),
        rows: rows.map(view),
      };
    });

  const sumOf = (status: Status) =>
    expenses
      .filter((e) => e.status === status)
      .reduce((total, e) => total + e.amount, 0);

  await page.route(`**/api/travel/trips/${TRIP_ID}/expenses`, async (route) => {
    if (route.request().method() === "POST") {
      const body = route.request().postDataJSON() as Partial<StoredExpense> & {
        occurredOn: string;
      };
      const stored: StoredExpense = {
        id: nextId++,
        occurredOn: body.occurredOn,
        title: body.title ?? null,
        amount: body.amount ?? 0,
        category: body.category ?? null,
        paymentMethod: body.paymentMethod ?? null,
        // 안 보내면 날짜가 정한다 — 오늘 이후면 예정이다.
        status:
          body.status ?? (body.occurredOn > TODAY ? "SCHEDULED" : "CONFIRMED"),
      };
      expenses.push(stored);
      return route.fulfill(ok(view(stored)));
    }
    return route.fulfill(
      ok({
        tripId: TRIP_ID,
        status: "ONGOING",
        todayDayNumber: dayNumber(TODAY),
        budget: {
          amount: 800000,
          spent: sumOf("CONFIRMED"),
          scheduled: sumOf("SCHEDULED"),
          remaining: 800000 - sumOf("CONFIRMED"),
          daysLeft: 2,
          dailyAllowance: Math.floor((800000 - sumOf("CONFIRMED")) / 2),
        },
        totals: {
          spent: sumOf("CONFIRMED"),
          scheduled: sumOf("SCHEDULED"),
          days: 3,
          dailyAverage: null,
        },
        unsortedCount: expenses.filter((e) => e.category === null).length,
        groups: groups(),
      }),
    );
  });

  await page.route(
    `**/api/travel/trips/${TRIP_ID}/expenses/*`,
    async (route) => {
      const id = Number(route.request().url().split("/").pop());
      const index = expenses.findIndex((e) => e.id === id);

      if (route.request().method() === "DELETE") {
        expenses.splice(index, 1);
        return route.fulfill(ok(null));
      }

      const body = route.request().postDataJSON() as Record<string, unknown>;
      const stored = expenses[index];
      if (typeof body.amount === "number") stored.amount = body.amount;
      if (typeof body.occurredOn === "string")
        stored.occurredOn = body.occurredOn;
      if (typeof body.status === "string")
        stored.status = body.status as Status;
      if (typeof body.title === "string") stored.title = body.title;
      if (body.clearTitle === true) stored.title = null;
      if (typeof body.category === "string") stored.category = body.category;
      if (body.clearCategory === true) stored.category = null;
      if (typeof body.paymentMethod === "string")
        stored.paymentMethod = body.paymentMethod;
      if (body.clearPaymentMethod === true) stored.paymentMethod = null;
      return route.fulfill(ok(view(stored)));
    },
  );

  await page.route(`**/api/travel/trips/${TRIP_ID}`, (route) =>
    route.fulfill(
      ok({
        id: TRIP_ID,
        title: "일본 가을",
        destinationName: "오사카",
        destinationPlaceId: 21,
        startDate: START,
        endDate: END,
        timezone: "Asia/Tokyo",
        currency: "JPY",
        lat: null,
        lng: null,
        defaultNotifyMinutes: 15,
        morningSummaryEnabled: true,
        status: "ONGOING",
        dDay: 0,
        totalDays: 3,
        activityCount: 0,
      }),
    ),
  );

  /** 시트를 열 때만 부르는 보드. 통화 기본값이 여기서 온다. */
  await page.route(`**/api/travel/trips/${TRIP_ID}/board*`, (route) =>
    route.fulfill(
      ok({
        tripId: TRIP_ID,
        selectedDate: TODAY,
        days: [
          {
            dayId: 1,
            date: TODAY,
            dayNumber: 2,
            baseCity: {
              placeId: 21,
              name: "오사카",
              cityName: "오사카",
              timezone: "Asia/Tokyo",
              currency: "JPY",
            },
            activities: [],
            stay: null,
          },
        ],
      }),
    ),
  );

  return expenses;
}

/** 화면 폭에 따라 헤더 버튼과 FAB 중 하나만 보인다 — 보이는 쪽을 누른다. */
function addButton(page: Page) {
  return page
    .getByRole("button", { name: "지출 적기" })
    .filter({ visible: true })
    .first();
}

test.describe("경비", () => {
  test("적고 · 고치고 · 확정하고 · 지운다 — 가계부 없이 한 화면에서", async ({
    page,
  }) => {
    const stored = await mockExpenses(page);
    await page.goto(`/travel/trips/${TRIP_ID}/expenses`);

    await expect(page.getByRole("heading", { name: "경비" })).toBeVisible();

    // 1) 금액만 찍고 저장한다 — 분류를 고르느라 기록을 포기하지 않는다(§6.1).
    await addButton(page).click();
    const sheet = page.getByRole("dialog");
    await expect(sheet.getByText("지출 적기")).toBeVisible();
    await sheet.getByLabel("금액").fill("32000");
    await sheet.getByRole("button", { name: "KRW ₩" }).click();
    await sheet.getByRole("button", { name: "저장" }).click();

    await expect(sheet).toBeHidden();
    const row = page.getByRole("button", { name: /제목 없음/ });
    await expect(row).toBeVisible();
    // 분류가 비었으니 정리 줄이 뜬다.
    await expect(page.getByText("정리할 내역 1건")).toBeVisible();

    // 2) 줄을 눌러 고친다 — 가계부로 나가지 않는다(D-35를 뒤집었다).
    await expect(page.getByRole("link", { name: /제목 없음/ })).toHaveCount(0);
    await row.click();
    await expect(sheet.getByText("지출 고치기")).toBeVisible();
    await expect(sheet.getByLabel("금액")).toHaveValue("32000");
    await sheet.getByLabel("무엇에").fill("이자카야");
    await sheet.getByRole("button", { name: "식비" }).click();
    await sheet.getByLabel("결제수단").fill("국민 체크");
    await sheet.getByRole("button", { name: "저장" }).click();

    await expect(sheet).toBeHidden();
    await expect(page.getByRole("button", { name: /이자카야/ })).toBeVisible();
    // 분류를 채웠으니 정리 줄이 사라진다.
    await expect(page.getByText("정리할 내역")).toHaveCount(0);

    // 3) 지난 예정을 사람이 확정한다 — 올려 주는 배치를 만들지 않았다(§4.3).
    stored.push({
      id: 900,
      occurredOn: START,
      title: "숙소 잔금",
      amount: 80000,
      category: "STAY",
      paymentMethod: null,
      status: "SCHEDULED",
    });
    await page.reload();
    await page.getByRole("button", { name: /10\.24 · 오사카/ }).click();
    await expect(page.getByText("지난 예정")).toBeVisible();
    await page.getByRole("button", { name: "확정" }).click();
    await expect(page.getByText("지난 예정")).toHaveCount(0);

    // 4) 지운다 — 상쇄 거래를 만들지 않는다(§4.4).
    await page.getByRole("button", { name: /이자카야/ }).click();
    await sheet.getByRole("button", { name: "지우기" }).click();

    await expect(page.getByRole("button", { name: /이자카야/ })).toHaveCount(0);
    await expect(page.getByText("지웠어요")).toBeVisible();
  });

  test("하단 각주가 가계부를 말하지 않는다 — 더 이상 읽기 뷰가 아니다", async ({
    page,
  }) => {
    await mockExpenses(page);
    await page.goto(`/travel/trips/${TRIP_ID}/expenses`);

    await expect(
      page.getByText(/여기 적은 지출은 이 여행 안에만 남습니다/),
    ).toBeVisible();
    await expect(page.getByText(/가계부 원장/)).toHaveCount(0);
  });
});
