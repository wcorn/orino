import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import type {
  ExpenseGroup,
  ExpenseRow,
  TripExpenses,
} from "@/features/travel/api/expenses";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

const API_BASE = "https://api.orino.dev/api";
const TRIP_ID = 12;

function row(partial: Partial<ExpenseRow> & { expenseId: number }): ExpenseRow {
  return {
    title: "이자카야",
    amount: 32000,
    fx: null,
    status: "CONFIRMED",
    category: null,
    paymentMethod: null,
    uncategorized: false,
    overdue: false,
    occurredOn: "2026-10-25",
    ...partial,
  };
}

function group(partial: Partial<ExpenseGroup> & { key: string }): ExpenseGroup {
  return {
    label: partial.key,
    dayNumber: null,
    date: null,
    cityName: null,
    sum: 0,
    rows: [],
    ...partial,
  };
}

function mockExpenses(partial: Partial<TripExpenses> = {}) {
  const data: TripExpenses = {
    tripId: TRIP_ID,
    status: "ONGOING",
    todayDayNumber: 2,
    budget: null,
    totals: { spent: 412000, scheduled: 0, days: 4, dailyAverage: null },
    unsortedCount: 0,
    groups: [
      group({
        key: "DAY-1",
        label: "10.24 (토) · 오사카",
        dayNumber: 1,
        cityName: "오사카",
      }),
      group({
        key: "DAY-2",
        label: "10.25 (일) · 오사카",
        dayNumber: 2,
        cityName: "오사카",
        sum: 32000,
        rows: [row({ expenseId: 4301 })],
      }),
    ],
    ...partial,
  };
  const saved: (number | null)[] = [];
  server.use(
    http.get(`${API_BASE}/travel/trips/:tripId/expenses`, () =>
      HttpResponse.json({ code: "OK", data }),
    ),
    http.put(`${API_BASE}/travel/trips/:tripId/budget`, async ({ request }) => {
      const body = (await request.json()) as { amount: number | null };
      saved.push(body.amount);
      return HttpResponse.json({ code: "OK", data: { amount: body.amount } });
    }),
  );
  return saved;
}

/** 지출 쓰기 세 개를 받아 적는다. 무엇이 어디로 갔는지가 이 화면의 단언이다. */
function mockWrites() {
  const created: Record<string, unknown>[] = [];
  const patched: { id: string; body: Record<string, unknown> }[] = [];
  const deleted: string[] = [];
  server.use(
    http.post(
      `${API_BASE}/travel/trips/:tripId/expenses`,
      async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>;
        created.push(body);
        return HttpResponse.json({
          code: "OK",
          data: row({ expenseId: 9001, ...body }),
        });
      },
    ),
    http.patch(
      `${API_BASE}/travel/trips/:tripId/expenses/:expenseId`,
      async ({ request, params }) => {
        const body = (await request.json()) as Record<string, unknown>;
        patched.push({ id: String(params.expenseId), body });
        return HttpResponse.json({
          code: "OK",
          data: row({ expenseId: Number(params.expenseId), ...body }),
        });
      },
    ),
    http.delete(
      `${API_BASE}/travel/trips/:tripId/expenses/:expenseId`,
      ({ params }) => {
        deleted.push(String(params.expenseId));
        return HttpResponse.json({ code: "OK", data: null });
      },
    ),
  );
  return { created, patched, deleted };
}

function renderExpenses() {
  return renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: [`/travel/trips/${TRIP_ID}/expenses`] },
  );
}

/** 예산 게이지. `role="img"`라 이름으로만 잡힌다 — 없으면 안 그려진 것이다. */
function gauge() {
  return screen.queryByRole("img", { name: /예산/ });
}

describe("TripExpensesPage", () => {
  // 오프라인 테스트가 단언에서 실패해도 옆 테스트가 오프라인으로 시작하지 않게 한다.
  afterEach(() => {
    Object.defineProperty(navigator, "onLine", {
      configurable: true,
      value: true,
    });
    window.dispatchEvent(new Event("online"));
  });

  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
    server.use(
      http.get(`${API_BASE}/travel/summary`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            ongoing: null,
            next: null,
            recentCompleted: null,
            trips: [],
            completedCount: 0,
          },
        }),
      ),
    );
  });

  it("브레드크럼이 여행 이름을 말하고, 그 이름이 그 여행의 보드로 간다", async () => {
    mockExpenses();
    renderExpenses();

    const crumb = within(
      await screen.findByRole("navigation", { name: "현재 위치" }),
    );
    expect(crumb.getByRole("link", { name: "여행" })).toHaveAttribute(
      "href",
      "/travel/trips",
    );
    expect(crumb.getByRole("link", { name: "일본 가을" })).toHaveAttribute(
      "href",
      `/travel/trips/${TRIP_ID}/board`,
    );
    expect(crumb.getByText("경비")).toBeVisible();
  });

  it("가계부 위의 읽기 뷰라는 각주는 더 이상 사실이 아니다", async () => {
    mockExpenses();
    renderExpenses();

    await screen.findByRole("navigation", { name: "현재 위치" });
    expect(screen.queryByText(/가계부 원장/)).toBeNull();
    expect(
      screen.getByText(/여기 적은 지출은 이 여행 안에만 남습니다/),
    ).toBeVisible();
  });

  it("예산을 안 정했으면 게이지를 그리지 않는다 — 0으로 꾸미지 않는다", async () => {
    mockExpenses({ budget: null });
    renderExpenses();

    expect(
      await screen.findByText(/아직 예산을 정하지 않았어요/),
    ).toBeInTheDocument();
    // 「0원 중 41.2만」을 그리지 않기 위한 규칙이다(§5.3).
    expect(gauge()).not.toBeInTheDocument();
    expect(screen.queryByText(/하루 쓸 수 있는 돈/)).not.toBeInTheDocument();
    // 얼마 썼는지는 예산 없이도 답이 있다.
    expect(screen.getByText("41.2만")).toBeInTheDocument();
  });

  it("예산이 있으면 두 겹 게이지와 하루 쓸 수 있는 돈을 그린다", async () => {
    mockExpenses({
      budget: {
        amount: 800000,
        spent: 412000,
        scheduled: 80000,
        remaining: 388000,
        daysLeft: 4,
        dailyAllowance: 97000,
      },
    });
    renderExpenses();

    expect(await screen.findByText("80만 중 41.2만")).toBeInTheDocument();
    expect(screen.getByText("쓴 돈 52% · 예정 10%")).toBeInTheDocument();
    expect(
      screen.getByText(/남은 4일 · 하루 쓸 수 있는 돈/),
    ).toBeInTheDocument();
    expect(screen.getByText("9.7만")).toBeInTheDocument();
    // 게이지 2층이 무엇인지 각주가 받는다. 이체라는 개념이 없어져 카드 대금 문구는 빠졌다.
    expect(
      screen.getByText(/연한 칸은 아직 안 나간 예정이에요/),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/카드 대금 납부는 여기 들어가지 않아요/),
    ).toBeNull();

    const bar = gauge()!;
    // 폭 계산은 여행이 가진 gaugeWidths 한 곳에서만 한다.
    expect(bar.children[0]).toHaveStyle({ width: "51.5%" });
    expect(bar.children[1]).toHaveStyle({ width: "10%" });
  });

  it("다녀온 뒤에는 하루 평균이 그 자리를 받는다", async () => {
    mockExpenses({
      status: "COMPLETED",
      todayDayNumber: null,
      budget: {
        amount: 800000,
        spent: 823000,
        scheduled: 0,
        remaining: -23000,
        daysLeft: null,
        dailyAllowance: null,
      },
      totals: { spent: 823000, scheduled: 0, days: 6, dailyAverage: 137000 },
    });
    renderExpenses();

    expect(await screen.findByText(/총 82.3만/)).toBeInTheDocument();
    expect(screen.getByText(/2.3만 초과/)).toBeInTheDocument();
    expect(screen.getByText(/총 6일 · 하루 평균/)).toBeInTheDocument();
    expect(screen.getByText("13.7만")).toBeInTheDocument();
    // 둘이 동시에 차지 않는다.
    expect(screen.queryByText(/하루 쓸 수 있는 돈/)).not.toBeInTheDocument();
  });

  it("행을 누르면 여행 안에서 편집 시트가 열린다 — 가계부로 나가지 않는다", async () => {
    mockExpenses();
    const user = userEvent.setup();
    renderExpenses();

    const rowButton = await screen.findByRole("button", { name: /이자카야/ });
    // 링크였다면 여기서 가계부로 나갔다(D-35를 뒤집었다).
    expect(screen.queryByRole("link", { name: /이자카야/ })).toBeNull();

    await user.click(rowButton);

    const sheet = within(await screen.findByRole("dialog"));
    expect(sheet.getByText("지출 고치기")).toBeVisible();
    expect(sheet.getByLabelText("금액")).toHaveValue("32000");
  });

  it("기본 펼침은 오늘 하나뿐이다", async () => {
    mockExpenses();
    renderExpenses();

    // 오늘(2일차)의 줄은 보이고, 1일차는 접혀 있다.
    expect(await screen.findByText("이자카야")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /10\.24 \(토\) · 오사카/ }),
    ).toHaveAttribute("aria-expanded", "false");
    expect(
      screen.getByRole("button", { name: /10\.25 \(일\) · 오사카/ }),
    ).toHaveAttribute("aria-expanded", "true");
  });

  it("빈 날짜는 「아직 적은 게 없어요」라고 말한다", async () => {
    mockExpenses();
    const user = userEvent.setup();
    renderExpenses();

    await user.click(
      await screen.findByRole("button", { name: /10\.24 \(토\) · 오사카/ }),
    );

    expect(screen.getByText("아직 적은 게 없어요")).toBeInTheDocument();
  });

  it("미분류가 있을 때만 정리 줄을 보여준다", async () => {
    mockExpenses({ unsortedCount: 0 });
    const { unmount } = renderExpenses();
    await screen.findByText("이자카야");
    expect(screen.queryByText(/정리할 내역/)).not.toBeInTheDocument();
    unmount();

    mockExpenses({ unsortedCount: 3 });
    renderExpenses();
    expect(await screen.findByText(/정리할 내역 3건/)).toBeInTheDocument();
    expect(screen.getByText("분류만 채우면 끝나요")).toBeInTheDocument();
  });

  it("예산을 저장하면 그 값이 그대로 간다", async () => {
    const saved = mockExpenses({ budget: null });
    const user = userEvent.setup();
    renderExpenses();

    // 「예산 정하기」는 두 자리에 있다 — 헤더와, 예산이 없을 때의 카드 안(§10.2).
    // 둘 다 같은 모달을 열므로 앞의 것으로 연다.
    await user.click(
      (await screen.findAllByRole("button", { name: "예산 정하기" }))[0],
    );
    const modal = within(await screen.findByRole("dialog"));
    await user.type(modal.getByLabelText("예산 총액"), "800000");
    await user.click(modal.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(saved).toEqual([800000]));
  });

  it("0은 저장 버튼을 열지 않는다 — 「안 정함」과 구분되지 않는다", async () => {
    mockExpenses({ budget: null });
    const user = userEvent.setup();
    renderExpenses();

    await user.click(
      (await screen.findAllByRole("button", { name: "예산 정하기" }))[0],
    );
    const modal = within(await screen.findByRole("dialog"));
    await user.type(modal.getByLabelText("예산 총액"), "0");

    // 서버도 400으로 막지만, 누른 뒤에 듣는 것과 누르기 전에 아는 것은 다르다.
    expect(modal.getByRole("button", { name: "저장" })).toBeDisabled();
  });

  it("지출 적기 입구가 헤더와 FAB 둘 다 있다", async () => {
    mockExpenses();
    renderExpenses();

    // 데스크톱은 헤더, 모바일은 FAB — 같은 시트를 연다.
    await screen.findByText("이자카야");
    const entries = screen.getAllByRole("button", { name: /지출 적기/ });
    expect(entries).toHaveLength(2);
  });

  it("오프라인이면 입력 진입 자체를 막는다 — 큐잉하지 않는다", async () => {
    mockExpenses();
    renderExpenses();
    await screen.findByText("이자카야");

    Object.defineProperty(navigator, "onLine", {
      configurable: true,
      value: false,
    });
    window.dispatchEvent(new Event("offline"));

    expect(
      await screen.findByText("오프라인 · 경비 조회만 가능합니다"),
    ).toBeVisible();
    // FAB은 아예 사라지고, 헤더 버튼은 잠긴다.
    const entries = screen.queryAllByRole("button", { name: /지출 적기/ });
    expect(entries).toHaveLength(1);
    expect(entries[0]).toBeDisabled();
    // 예산이 없을 때는 카드 안에도 같은 버튼이 있다(§10.2) — 둘 다 잠긴다.
    for (const button of screen.getAllByRole("button", {
      name: "예산 정하기",
    })) {
      expect(button).toBeDisabled();
    }
    // 보는 것은 그대로 된다.
    expect(screen.getByText("이자카야")).toBeVisible();
  });

  it("외화는 원화 옆에 보조로만 붙는다", async () => {
    mockExpenses({
      groups: [
        group({
          key: "DAY-2",
          label: "10.25 (일) · 오사카",
          dayNumber: 2,
          sum: 11300,
          rows: [
            row({
              expenseId: 4301,
              title: "점심 라멘",
              amount: 11300,
              fx: { currency: "JPY", amount: 1200, rate: 9.4166 },
            }),
          ],
        }),
      ],
    });
    renderExpenses();

    const rowButton = await screen.findByRole("button", { name: /점심 라멘/ });
    expect(rowButton).toHaveTextContent("JPY 1,200");
    // 본문 금액은 언제나 서버가 확정한 원화다.
    expect(rowButton).toHaveTextContent("11,300원");
  });

  it("고친 값이 PATCH로 간다 — 보낸 것만 바뀐다", async () => {
    mockExpenses({
      groups: [
        group({
          key: "DAY-2",
          label: "10.25 (일) · 오사카",
          dayNumber: 2,
          sum: 32000,
          rows: [
            row({
              expenseId: 4301,
              category: "FOOD",
              paymentMethod: "국민 체크",
            }),
          ],
        }),
      ],
    });
    const { patched } = mockWrites();
    const user = userEvent.setup();
    renderExpenses();

    await user.click(await screen.findByRole("button", { name: /이자카야/ }));
    const sheet = within(await screen.findByRole("dialog"));
    // 시트가 그 줄의 값으로 열린다 — 편집 화면을 따로 만들지 않았다.
    expect(sheet.getByLabelText("무엇에")).toHaveValue("이자카야");
    expect(sheet.getByLabelText("결제수단")).toHaveValue("국민 체크");
    expect(sheet.getByRole("button", { name: "식비" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );

    await user.clear(sheet.getByLabelText("무엇에"));
    await user.type(sheet.getByLabelText("무엇에"), "이자카야 2차");
    await user.click(sheet.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(patched).toHaveLength(1));
    expect(patched[0].id).toBe("4301");
    expect(patched[0].body).toMatchObject({
      title: "이자카야 2차",
      amount: 32000,
      category: "FOOD",
      paymentMethod: "국민 체크",
    });
  });

  it("분류를 해제하면 clearCategory로 간다 — null은 「안 보냈다」와 같다", async () => {
    mockExpenses({
      groups: [
        group({
          key: "DAY-2",
          label: "10.25 (일) · 오사카",
          dayNumber: 2,
          sum: 32000,
          rows: [row({ expenseId: 4301, category: "FOOD" })],
        }),
      ],
    });
    const { patched } = mockWrites();
    const user = userEvent.setup();
    renderExpenses();

    await user.click(await screen.findByRole("button", { name: /이자카야/ }));
    const sheet = within(await screen.findByRole("dialog"));
    await user.click(sheet.getByRole("button", { name: "식비" }));
    await user.click(sheet.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(patched).toHaveLength(1));
    expect(patched[0].body).toMatchObject({ clearCategory: true });
  });

  it("지우면 DELETE가 가고 되돌리기를 준다 — 상쇄 거래를 만들지 않는다", async () => {
    mockExpenses();
    const { deleted, created } = mockWrites();
    const user = userEvent.setup();
    renderExpenses();

    await user.click(await screen.findByRole("button", { name: /이자카야/ }));
    const sheet = within(await screen.findByRole("dialog"));
    await user.click(sheet.getByRole("button", { name: "지우기" }));

    await waitFor(() => expect(deleted).toEqual(["4301"]));
    // 되돌리기는 같은 값으로 다시 적는 것이다 — 복원 API를 두지 않는다(§4.4).
    // 버튼 이름에 남은 초가 붙는다("실행취소 5") — 정확히 맞추지 않는다.
    await user.click(await screen.findByRole("button", { name: /실행취소/ }));
    await waitFor(() => expect(created).toHaveLength(1));
    expect(created[0]).toMatchObject({
      occurredOn: "2026-10-25",
      title: "이자카야",
      amount: 32000,
      status: "CONFIRMED",
    });
  });

  it("지난 예정에만 「확정」이 붙고, 누르면 상태만 올라간다", async () => {
    mockExpenses({
      groups: [
        group({
          key: "DAY-2",
          label: "10.25 (일) · 오사카",
          dayNumber: 2,
          sum: 112000,
          rows: [
            row({ expenseId: 4301 }),
            row({
              expenseId: 4302,
              title: "숙소 잔금",
              amount: 80000,
              status: "SCHEDULED",
              overdue: true,
            }),
          ],
        }),
      ],
    });
    const { patched } = mockWrites();
    const user = userEvent.setup();
    renderExpenses();

    // 확정된 줄에는 안 붙는다 — 버튼은 하나뿐이다.
    const confirms = await screen.findAllByRole("button", { name: "확정" });
    expect(confirms).toHaveLength(1);
    expect(screen.getByText("지난 예정")).toBeVisible();

    await user.click(confirms[0]);

    // 올려 주는 배치가 없으므로 이 길이 유일하다(§4.3).
    await waitFor(() => expect(patched).toHaveLength(1));
    expect(patched[0]).toEqual({ id: "4302", body: { status: "CONFIRMED" } });
  });

  it("결제수단 후보는 그 여행에서 이미 쓴 값뿐이다 — 전역 목록을 받지 않는다", async () => {
    mockExpenses({
      groups: [
        group({
          key: "DAY-2",
          label: "10.25 (일) · 오사카",
          dayNumber: 2,
          sum: 43300,
          rows: [
            row({ expenseId: 4301, paymentMethod: "국민 체크" }),
            row({ expenseId: 4302, title: "라멘", paymentMethod: "현금" }),
          ],
        }),
      ],
    });
    mockWrites();
    const user = userEvent.setup();
    renderExpenses();

    await user.click(await screen.findByRole("button", { name: /이자카야/ }));
    const sheet = await screen.findByRole("dialog");
    const options = [...sheet.querySelectorAll("datalist option")].map(
      (option) => option.getAttribute("value"),
    );
    expect(options).toEqual(["현금", "국민 체크"]);
  });

  it("환율을 못 받은 채 저장된 건은 0원이라고 말하지 않는다", async () => {
    mockExpenses({
      groups: [
        group({
          key: "DAY-2",
          label: "10.25 (일) · 오사카",
          dayNumber: 2,
          sum: 0,
          rows: [
            row({
              expenseId: 4301,
              title: "라멘",
              amount: 0,
              fx: { currency: "JPY", amount: 1200, rate: null },
            }),
          ],
        }),
      ],
    });
    renderExpenses();

    const rowButton = await screen.findByRole("button", { name: /라멘/ });
    expect(rowButton).toHaveTextContent("환율 미입력");
    expect(rowButton).not.toHaveTextContent("0원");
  });
});
