import { expect, type Page, test } from "@playwright/test";

/**
 * 링크 워크스페이스 한 바퀴(#1245) — 발급 → 목록 반영 → 상세 → 목적지 교체 → 이력.
 *
 * <p>이 스펙은 <b>세 프로젝트 전부</b>에서 돈다(chromium · built · mobile-touch).
 * 모바일에서 행이 두 줄로 접히고 QR이 상세로 밀리므로, 어느 쪽에서도 성립하는 것만 단언한다 —
 * 주소·목적지·복사·이동은 폭과 무관하게 같아야 한다.
 *
 * <p>클립보드는 <b>직접 갈아끼운다</b>. 헤드리스 브라우저의 권한 상태가 프로젝트마다 달라
 * 실제 `navigator.clipboard`에 기대면 단언이 환경 따라 갈린다 — 여기서 확인할 것은 권한이
 * 아니라 <b>무엇을 복사하려 했는가</b>다(명세 §4.1).
 */

const AUG = "https://img.orino.dev/note-images/2026/aug.jpg";
const SEP = "https://img.orino.dev/note-images/2026/sep.jpg";
const SHORT_URL = "https://s.orino.dev/9dwqr";

const ok = (data: unknown) => ({
  status: 200,
  contentType: "application/json",
  body: JSON.stringify({ code: "OK", data }),
});

function summaryCard(slug: string, targetUrl: string) {
  return {
    slug,
    shortUrl: `https://s.orino.dev/${slug}`,
    targetUrl,
    memo: null,
    tags: [],
    custom: false,
    favorite: false,
    state: "ACTIVE",
    hasPassword: false,
    visitCount: 0,
    lastVisitedAt: null,
  };
}

function detail(targetUrl: string, history: unknown[]) {
  return {
    ...summaryCard("9dwqr", targetUrl),
    createdAt: "2026-08-24T00:00:00Z",
    expiresAt: null,
    og: null,
    targetHistory: history,
  };
}

interface Captured {
  copied: string[];
  patches: Record<string, unknown>[];
}

async function mockLinks(page: Page): Promise<Captured> {
  const captured: Captured = { copied: [], patches: [] };

  // 클립보드를 우리 것으로 바꾼다. 화면이 무엇을 넣으려 했는지만 본다.
  await page.addInitScript(() => {
    const copied: string[] = [];
    (window as unknown as { __copied: string[] }).__copied = copied;
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: {
        writeText: (text: string) => {
          copied.push(text);
          return Promise.resolve();
        },
      },
    });
  });

  await page.route("**/api/auth/reissue", (route) =>
    route.fulfill(ok({ accessToken: "mock-access-token" })),
  );
  await page.route("**/api/planner/reviews/summary", (route) =>
    route.fulfill(
      ok({
        today: "2026-08-24",
        counts: { now: 0, overdue: 0, upcoming: 0, doneToday: 0 },
        estimatedMinutes: 0,
        materials: [],
      }),
    ),
  );
  await page.route("**/api/shortlinks/summary", (route) =>
    route.fulfill(
      ok({ total: 1, visitsThisWeek: 0, baseUrl: "https://s.orino.dev" }),
    ),
  );
  await page.route("**/api/shortlinks/tags", (route) => route.fulfill(ok([])));
  await page.route("**/api/shortlinks/og-preview*", (route) =>
    route.fulfill(ok({ ok: false, title: null, imageUrl: null })),
  );
  await page.route("**/api/shortlinks/9dwqr/stats*", (route) =>
    route.fulfill(
      ok({
        totalVisits: 0,
        botVisits: 0,
        last7Days: 0,
        lastVisitedAt: null,
        daily: [{ date: "2026-08-24", count: 0 }],
        referrers: [],
        devices: [],
        countries: [],
      }),
    ),
  );

  let issued = false;
  let currentTarget = AUG;
  let history: unknown[] = [
    { targetUrl: AUG, reason: "최초 발급", changedAt: "2026-08-24T00:00:00Z" },
  ];

  await page.route("**/api/shortlinks/9dwqr", (route) => {
    if (route.request().method() === "PATCH") {
      const body = route.request().postDataJSON() as Record<string, unknown>;
      captured.patches.push(body);
      currentTarget = String(body.targetUrl ?? currentTarget);
      history = [
        {
          targetUrl: currentTarget,
          reason: body.targetChangeReason ?? null,
          changedAt: "2026-08-24T01:00:00Z",
        },
        ...history,
      ];
      return route.fulfill(ok(detail(currentTarget, history)));
    }
    return route.fulfill(ok(detail(currentTarget, history)));
  });

  // 목록·발급은 같은 경로라 메서드로 가른다. 발급 뒤에는 목록에 그 링크가 보여야 한다.
  await page.route("**/api/shortlinks?*", (route) => handleList(route));
  await page.route("**/api/shortlinks", (route) => handleList(route));

  function handleList(route: Parameters<Parameters<Page["route"]>[1]>[0]) {
    if (route.request().method() === "POST") {
      issued = true;
      return route.fulfill(
        ok({ ...summaryCard("9dwqr", AUG), qrPayload: SHORT_URL }),
      );
    }
    return route.fulfill(
      ok({
        counts: {
          all: issued ? 1 : 0,
          active: issued ? 1 : 0,
          inactive: 0,
        },
        favorites: [],
        recent: issued ? [summaryCard("9dwqr", currentTarget)] : [],
      }),
    );
  }

  return captured;
}

async function copiedTexts(page: Page): Promise<string[]> {
  return page.evaluate(
    () => (window as unknown as { __copied: string[] }).__copied,
  );
}

test.describe("링크 워크스페이스", () => {
  test("붙여넣고 Enter → 목록에 뜨고 서버가 준 주소가 복사된다", async ({
    page,
  }) => {
    await mockLinks(page);
    await page.goto("/links");

    await page.getByLabel("빠른 발급 URL").fill(AUG);
    await page.getByLabel("빠른 발급 URL").press("Enter");

    // 목록에 행이 뜬다(폭과 무관하게 주소는 보인다).
    await expect(page.getByText("9dwqr")).toBeVisible();
    // 낙관적으로 만든 값이 아니라 서버 응답으로만 복사한다.
    await expect.poll(() => copiedTexts(page)).toEqual([SHORT_URL]);
  });

  test("행을 눌러 상세로 가고, 목적지를 갈아끼우면 주소는 그대로 이력만 는다", async ({
    page,
  }) => {
    const captured = await mockLinks(page);
    await page.goto("/links");

    await page.getByLabel("빠른 발급 URL").fill(AUG);
    await page.getByLabel("빠른 발급 URL").press("Enter");
    await expect(page.getByText("9dwqr")).toBeVisible();

    await page.getByRole("button", { name: /s\.orino\.dev/ }).click();
    await expect(page).toHaveURL(/\/links\/9dwqr$/);
    await expect(page.getByRole("link", { name: "링크 목록" })).toBeVisible();

    await page.getByRole("button", { name: /목적지 수정/ }).click();
    await page.getByLabel("새 목적지 URL").fill(SEP);
    await page.getByLabel(/교체 사유/).fill("서명 만료로 재발급");
    await page.getByRole("button", { name: "바꾸기" }).click();

    const history = page.getByRole("list", { name: "목적지 교체 이력" });
    await expect(history.getByRole("listitem")).toHaveCount(2);
    await expect(history.getByText("서명 만료로 재발급")).toBeVisible();
    // 이 화면의 존재 이유 — 주소는 그대로다.
    await expect(page.getByText("9dwqr")).toBeVisible();
    expect(captured.patches).toEqual([
      { targetUrl: SEP, targetChangeReason: "서명 만료로 재발급" },
    ]);
  });

  test("복사 버튼은 복사만 한다 — 행 이동이 함께 일어나지 않는다", async ({
    page,
  }) => {
    await mockLinks(page);
    await page.goto("/links");

    await page.getByLabel("빠른 발급 URL").fill(AUG);
    await page.getByLabel("빠른 발급 URL").press("Enter");
    await expect(page.getByText("9dwqr")).toBeVisible();
    await expect.poll(() => copiedTexts(page)).toHaveLength(1);

    await page.getByRole("button", { name: "주소 복사", exact: true }).click();

    await expect.poll(() => copiedTexts(page)).toHaveLength(2);
    // 상세로 갔다면 목록 제목이 사라진다.
    await expect(page).toHaveURL(/\/links$/);
  });
});
