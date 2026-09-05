import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { ExpenseQuickSheet } from "./ExpenseQuickSheet";

const API_BASE = "https://api.orino.dev/api";

function mockLedger(rate: number | null = 9.4166) {
  const created: Record<string, unknown>[] = [];
  server.use(
    http.get(`${API_BASE}/ledger/assets`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          groups: [
            {
              id: null,
              name: null,
              kind: "ETC",
              assets: [
                { id: 1, name: "급여통장", type: "CHECKING" },
                { id: 2, name: "여행카드", type: "CREDIT_CARD" },
              ],
            },
          ],
          hidden: [],
          totalAssets: 0,
          liabilities: 0,
          netWorth: 0,
        },
      }),
    ),
    http.get(`${API_BASE}/ledger/categories`, () =>
      HttpResponse.json({
        code: "OK",
        data: [
          { id: 21, flow: "EXPENSE", name: "식비", parentId: null },
          { id: 22, flow: "EXPENSE", name: "교통", parentId: null },
          { id: 23, flow: "EXPENSE", name: "관광", parentId: null },
        ],
      }),
    ),
    http.get(`${API_BASE}/ledger/fx/rate`, ({ request }) =>
      HttpResponse.json({
        code: "OK",
        data: {
          currency: new URL(request.url).searchParams.get("currency"),
          rate,
          referenceDate: "2026-10-27",
        },
      }),
    ),
    http.post(`${API_BASE}/ledger/transactions`, async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>;
      created.push(body);
      return HttpResponse.json({
        code: "OK",
        data: {
          transaction: { id: 99, ...body },
          savedAs: "CONFIRMED",
        },
      });
    }),
  );
  return created;
}

function renderSheet(
  props: Partial<Parameters<typeof ExpenseQuickSheet>[0]> = {},
) {
  return renderWithRouter(
    <Providers>
      <ExpenseQuickSheet
        open
        onOpenChange={() => {}}
        tripId={12}
        cityName="오사카"
        cityCurrency="JPY"
        dayNumber={4}
        occurredOn="2026-10-27"
        onSaved={() => {}}
        {...props}
      />
    </Providers>,
  );
}

/**
 * 빠른 입력(§6.1). 이 시트가 지키는 것은 하나다 — <b>30초 안에 끝난다</b>.
 *
 * <p>그래서 여기 있는 테스트 대부분은 「무엇을 안 골라도 저장되나」와 「기본값이 맞나」다.
 * 카테고리를 고르느라 기록을 포기하는 것이 이 기능의 유일한 실패 방식이다.
 */
describe("ExpenseQuickSheet", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
    localStorage.clear();
  });

  it("금액만 적어도 저장된다 — 카테고리는 나중에 채운다", async () => {
    const created = mockLedger();
    const user = userEvent.setup();
    renderSheet();

    await user.type(await screen.findByLabelText("금액"), "1200");
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(created).toHaveLength(1));
    expect(created[0]).toMatchObject({
      type: "EXPENSE",
      occurredOn: "2026-10-27",
      tripId: 12,
      // 안 고른 카테고리는 null로 간다 — 미분류는 화면에 「정리할 내역」으로 남는다.
      categoryId: null,
    });
  });

  it("저장은 가계부 엔드포인트로 나간다 — 여행 전용 지출 API는 없다", async () => {
    mockLedger();
    // `server.use`는 나중에 등록한 것이 이긴다 — 잡아채는 핸들러를 뒤에 둔다.
    let calledPath: string | null = null;
    server.use(
      http.post(`${API_BASE}/ledger/transactions`, async ({ request }) => {
        calledPath = new URL(request.url).pathname;
        return HttpResponse.json({
          code: "OK",
          data: { transaction: { id: 99 }, savedAs: "CONFIRMED" },
        });
      }),
    );
    const user = userEvent.setup();
    renderSheet();

    await user.type(await screen.findByLabelText("금액"), "1200");
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(calledPath).toBe("/api/ledger/transactions"));
  });

  it("통화 기본값이 오늘 도시를 따른다 — 오사카면 엔이다", async () => {
    const created = mockLedger();
    const user = userEvent.setup();
    renderSheet({ cityCurrency: "JPY" });

    expect(
      await screen.findByRole("button", { name: "JPY ¥" }),
    ).toHaveAttribute("aria-pressed", "true");

    await user.type(screen.getByLabelText("금액"), "1200");
    // 환산 줄은 「굳는다」고 말한다 — 다녀온 여행의 총액이 매일 바뀌면 안 된다.
    expect(
      await screen.findByText("11,300원 · 오늘 환율로 굳습니다"),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(created).toHaveLength(1));
    // 환율은 비워 보낸다. 서버가 오늘 고시로 채우고 그 거래에 고정한다(§4.3).
    expect(created[0]).toMatchObject({
      fx: { currency: "JPY", amount: 1200, rate: null },
    });
    expect(created[0].amount).toBeUndefined();
  });

  it("원화 도시면 원으로 시작하고 환산 없이 amount로 보낸다", async () => {
    const created = mockLedger();
    const user = userEvent.setup();
    renderSheet({ cityCurrency: "KRW", cityName: "인천공항" });

    await user.type(await screen.findByLabelText("금액"), "4500");
    expect(
      screen.getByText("4,500원 · 오늘 환율로 굳습니다"),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(created).toHaveLength(1));
    expect(created[0]).toMatchObject({ amount: 4500 });
    expect(created[0].fx).toBeUndefined();
  });

  it("결제수단은 직전에 쓴 것으로 시작한다 — 여행 중엔 같은 카드를 계속 쓴다", async () => {
    const created = mockLedger();
    const user = userEvent.setup();
    const { unmount } = renderSheet();

    // 처음엔 첫 자산으로 시작한다.
    await user.click(await screen.findByRole("button", { name: "여행카드" }));
    await user.type(screen.getByLabelText("금액"), "1200");
    await user.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(created).toHaveLength(1));
    unmount();

    // 다시 열면 방금 고른 것이 미리 골라져 있다.
    renderSheet();
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "여행카드" })).toHaveAttribute(
        "aria-pressed",
        "true",
      ),
    );
  });

  it("금액이 비었으면 저장할 수 없다", async () => {
    mockLedger();
    renderSheet();

    expect(await screen.findByRole("button", { name: "저장" })).toBeDisabled();
  });

  it("환율을 못 받으면 환산을 지어내지 않는다", async () => {
    mockLedger(null);
    const user = userEvent.setup();
    renderSheet({ cityCurrency: "JPY" });

    await user.type(await screen.findByLabelText("금액"), "1200");

    expect(
      screen.getByText("금액을 적으면 원화가 여기 나와요"),
    ).toBeInTheDocument();
    // 그래도 저장은 된다 — 서버가 저장 시점에 채운다.
    expect(screen.getByRole("button", { name: "저장" })).toBeEnabled();
  });

  it("고른 카테고리를 다시 누르면 해제된다 — 선택 없이도 저장이 되니까", async () => {
    const created = mockLedger();
    const user = userEvent.setup();
    renderSheet();

    const chip = await screen.findByRole("button", { name: "식비" });
    await user.click(chip);
    expect(chip).toHaveAttribute("aria-pressed", "true");
    await user.click(chip);
    expect(chip).toHaveAttribute("aria-pressed", "false");

    await user.type(screen.getByLabelText("금액"), "1200");
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(created).toHaveLength(1));
    expect(created[0]).toMatchObject({ categoryId: null });
  });

  it("어디에서 몇 일차인지 말해 준다", async () => {
    mockLedger();
    renderSheet();

    const sheet = within(await screen.findByRole("dialog"));
    expect(sheet.getByText("오사카 · 4일차 · 30초 안에 끝나게")).toBeVisible();
  });
});
