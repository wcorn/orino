import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import { renderWithRouter } from "@/test/render";

import {
  assetView,
  type LedgerMockOptions,
  mockLedgerApi,
} from "./ledgerFixtures";

function renderLedger(
  options: LedgerMockOptions = {},
  path = "/ledger/transactions",
) {
  const created = mockLedgerApi(options);
  renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: [path] },
  );
  return created;
}

/** 모달을 연다. 화면 어디서든 `N` 하나로 열리는 것이 이 모듈의 약속이다. */
async function openModal(user: ReturnType<typeof userEvent.setup>) {
  await user.keyboard("n");
  return screen.findByRole("dialog");
}

/**
 * 거래 입력 모달(#1260).
 *
 * <p>이 테스트가 지키는 것은 <b>입력이 막히지 않는다</b>는 한 가지다 — 카테고리가 없어도,
 * 환율을 못 가져와도, 날짜가 미래여도 저장은 된다. 막는 순간 사람은 이 기능을 안 쓴다.
 */
describe("거래 입력 모달", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
    localStorage.clear();
  });

  it("`N` 키로 어디서든 열린다", async () => {
    const user = userEvent.setup();
    renderLedger();

    await screen.findByRole("heading", { name: "내역" });
    expect(screen.queryByRole("dialog")).toBeNull();

    await user.keyboard("n");

    expect(await screen.findByRole("dialog")).toBeInTheDocument();
  });

  it("글자를 치는 중에는 `N`이 단축키가 아니다", async () => {
    const user = userEvent.setup();
    renderLedger();

    const search = await screen.findByLabelText("내역 검색");
    await user.click(search);
    await user.keyboard("nn");

    expect(search).toHaveValue("nn");
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("금액만 적고 저장할 수 있다 — 미분류를 막지 않는다", async () => {
    const user = userEvent.setup();
    const created = renderLedger();

    const dialog = await openModal(user);
    await user.type(within(dialog).getByLabelText("금액"), "4500");
    await user.click(within(dialog).getByRole("button", { name: "저장" }));

    await waitFor(() => expect(created).toHaveLength(1));
    expect(created[0]).toMatchObject({
      type: "EXPENSE",
      amount: 4500,
      categoryId: null,
    });
  });

  it("계산기로 영수증을 더해 넣는다", async () => {
    const user = userEvent.setup();
    const created = renderLedger();

    const dialog = await openModal(user);
    await user.type(within(dialog).getByLabelText("금액"), "12000");
    await user.click(within(dialog).getByRole("button", { name: "계산기 +" }));
    await user.type(within(dialog).getByLabelText("금액"), "3000");

    expect(within(dialog).getByText("= 15,000")).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "저장" }));
    await waitFor(() => expect(created).toHaveLength(1));
    expect(created[0]).toMatchObject({ amount: 15000 });
  });

  it("미래 날짜로 저장하면 예정으로 저장됐다고 알린다", async () => {
    const user = userEvent.setup();
    renderLedger();

    const dialog = await openModal(user);
    await user.type(within(dialog).getByLabelText("금액"), "17000");
    const date = within(dialog).getByLabelText("날짜");
    await user.clear(date);
    await user.type(date, "2026-09-30");
    await user.click(within(dialog).getByRole("button", { name: "저장" }));

    // 조용히 넘기면 「입력이 안 됐다」로 읽힌다.
    expect(
      await screen.findByText("미래 날짜라 예정으로 저장했어요"),
    ).toBeInTheDocument();
  });

  it("자동완성 후보가 지난번 카테고리·자산을 함께 보여준다", async () => {
    const user = userEvent.setup();
    renderLedger({
      suggestions: [
        {
          title: "스타벅스 역삼",
          type: "EXPENSE",
          categoryId: 21,
          categoryName: "식비",
          assetId: 1,
          assetName: "급여통장",
          amount: 4500,
        },
      ],
    });

    const dialog = await openModal(user);
    await user.type(within(dialog).getByLabelText("내용"), "스타");

    const suggestion = await within(dialog).findByRole("button", {
      name: /스타벅스 역삼/,
    });
    expect(suggestion).toHaveTextContent("식비 · 급여통장");
  });

  describe("외화", () => {
    it("통화를 고르면 원화 환산액이 따라 붙는다", async () => {
      const user = userEvent.setup();
      const created = renderLedger();

      const dialog = await openModal(user);
      await user.type(within(dialog).getByLabelText("금액"), "1280");
      await user.click(
        within(dialog).getByRole("button", { name: /통화 — 현재 원/ }),
      );
      await user.click(await screen.findByRole("menuitem", { name: "JPY" }));

      // 1,280 × 8.7604 = 11,213
      expect(await within(dialog).findByText(/≈ 11,213원/)).toBeInTheDocument();

      await user.click(within(dialog).getByRole("button", { name: "저장" }));
      await waitFor(() => expect(created).toHaveLength(1));
      // 원화 금액은 서버가 확정한다 — 화면이 계산한 값을 함께 보내지 않는다.
      expect(created[0]).toMatchObject({
        fx: { currency: "JPY", amount: 1280, rate: 8.7604 },
      });
      expect(created[0].amount).toBeUndefined();
    });

    it("환율을 못 가져와도 저장이 막히지 않는다 — 원화로 적힌다", async () => {
      const user = userEvent.setup();
      const created = renderLedger({ fxRate: null });

      const dialog = await openModal(user);
      await user.type(within(dialog).getByLabelText("금액"), "12000");
      await user.click(
        within(dialog).getByRole("button", { name: /통화 — 현재 원/ }),
      );
      await user.click(await screen.findByRole("menuitem", { name: "JPY" }));

      expect(
        await within(dialog).findByText(/환율을 가져오지 못했어요/),
      ).toBeInTheDocument();

      const save = within(dialog).getByRole("button", { name: "저장" });
      expect(save).toBeEnabled();
      await user.click(save);

      await waitFor(() => expect(created).toHaveLength(1));
      // 반쪽 근거를 남기지 않는다 — 통화를 붙이지 않고 원화 금액만 보낸다.
      expect(created[0]).toMatchObject({ amount: 12000 });
      expect(created[0].fx).toBeUndefined();
    });
  });

  it("저장 후 계속 입력을 켜면 모달이 닫히지 않는다", async () => {
    const user = userEvent.setup();
    renderLedger();

    const dialog = await openModal(user);
    await user.click(within(dialog).getByLabelText("저장 후 계속 입력"));
    await user.type(within(dialog).getByLabelText("금액"), "4500");
    await user.click(within(dialog).getByRole("button", { name: "저장" }));

    await waitFor(() =>
      expect(within(dialog).getByLabelText("금액")).toHaveValue(""),
    );
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("숨긴 자산은 고를 수 없다 — 해지한 카드로 오늘 결제할 수는 없다", async () => {
    const user = userEvent.setup();
    renderLedger({
      assets: [
        assetView(),
        assetView({ id: 2, name: "해지한 카드", hidden: true, balance: null }),
      ],
    });

    const dialog = await openModal(user);
    await user.click(within(dialog).getByLabelText("자산"));

    expect(
      await screen.findByRole("option", { name: "급여통장" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "해지한 카드" })).toBeNull();
  });
});
