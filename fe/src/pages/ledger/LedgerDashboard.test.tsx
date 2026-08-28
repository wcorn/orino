import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import {
  assetView,
  type LedgerMockOptions,
  mockLedgerApi,
  transactionView,
} from "@/features/ledger/ledgerFixtures";
import { renderWithRouter } from "@/test/render";

function renderAt(path: string, options: LedgerMockOptions = {}) {
  mockLedgerApi(options);
  return renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: [path] },
  );
}

/**
 * 대시보드 · 통계 · 잔액 맞추기(#1261).
 *
 * <p>대시보드 테스트의 절반은 <b>없는 것을 확인하는 일</b>이다. v1에 예정·미납·2축 요약이
 * 없는 것은 미완성이 아니라 결정이고(D-7), 나중에 누가 「빈 카드라도 넣자」고 하면
 * 이 테스트가 막는다.
 */
describe("가계부 대시보드", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  it("이미 쓴 돈과 이번 달 수입을 보여준다", async () => {
    renderAt("/ledger", {
      dashboard: { spent: 1240000, income: 3850000 },
    });

    expect(await screen.findByText("1,240,000")).toBeInTheDocument();
    expect(screen.getByText("3,850,000")).toBeInTheDocument();
    expect(screen.getByText(/2026년 8월 · 월 시작일 1일/)).toBeInTheDocument();
  });

  it("예정·미납·2축 요약을 그리지 않는다 — 빈 카드가 있으면 고장난 것처럼 보인다", async () => {
    renderAt("/ledger", { dashboard: { spent: 1240000 } });

    await screen.findByText("1,240,000");
    expect(screen.queryByText(/앞으로 쓸 돈/)).toBeNull();
    expect(screen.queryByText(/미납/)).toBeNull();
    expect(screen.queryByText(/다가오는 결제/)).toBeNull();
    expect(screen.queryByText(/월말/)).toBeNull();
  });

  it("정리할 내역이 없으면 그 줄도 없다 — 0건은 할 일이 아니다", async () => {
    renderAt("/ledger", { dashboard: { uncategorized: 0 } });

    await screen.findByText("이미 쓴 돈");
    expect(screen.queryByText(/정리할 내역/)).toBeNull();
  });

  it("정리할 내역을 누르면 미분류만 남는다", async () => {
    const user = userEvent.setup();
    renderAt("/ledger", {
      dashboard: { uncategorized: 1 },
      transactions: [
        transactionView(),
        transactionView({
          id: 11,
          title: "현금 지출",
          categoryId: null,
          categoryName: null,
        }),
      ],
    });

    await user.click(await screen.findByRole("link", { name: /정리하기/ }));

    expect(await screen.findByText("현금 지출")).toBeInTheDocument();
    // 카테고리가 이미 있는 건은 정리 대상이 아니다.
    expect(screen.queryByText("스타벅스 역삼")).toBeNull();
    expect(
      screen.getByText(/카테고리가 없는 건만 보는 중/),
    ).toBeInTheDocument();
  });
});

describe("카테고리 통계", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  it("도넛 가운데에 이미 쓴 돈이 있고 순위 막대가 따라 붙는다", async () => {
    renderAt("/ledger/stats", {
      stats: {
        total: 400000,
        byCategory: [
          {
            categoryId: 21,
            categoryName: "식비",
            amount: 300000,
            count: 4,
            share: 0.75,
          },
          {
            categoryId: 22,
            categoryName: "카페/간식",
            amount: 100000,
            count: 2,
            share: 0.25,
          },
        ],
        previousTotal: 300000,
      },
    });

    expect(await screen.findByText("이미 쓴 돈")).toBeInTheDocument();
    expect(screen.getByText("400,000")).toBeInTheDocument();
    expect(screen.getByText("식비")).toBeInTheDocument();
    expect(screen.getByText("75%")).toBeInTheDocument();
  });

  it("미분류도 한 칸을 차지한다", async () => {
    renderAt("/ledger/stats", {
      stats: {
        total: 100000,
        byCategory: [
          {
            categoryId: null,
            categoryName: null,
            amount: 100000,
            count: 1,
            share: 1,
          },
        ],
      },
    });

    expect(await screen.findByText("미분류")).toBeInTheDocument();
  });

  it("관점 전환 토글을 그리지 않는다 — v2다", async () => {
    renderAt("/ledger/stats", {
      stats: {
        total: 100000,
        byCategory: [
          {
            categoryId: 21,
            categoryName: "식비",
            amount: 100000,
            count: 1,
            share: 1,
          },
        ],
      },
    });

    await screen.findByText("식비");
    // 할부가 없으면 두 관점이 같은 값이라, 토글이 아무 일도 안 하는 것처럼 보인다.
    expect(screen.queryByText("소비 기준")).toBeNull();
    expect(screen.queryByText("청구 기준")).toBeNull();
  });

  it("지난 달과 견줘 준다", async () => {
    renderAt("/ledger/stats", {
      stats: {
        total: 400000,
        byCategory: [
          {
            categoryId: 21,
            categoryName: "식비",
            amount: 400000,
            count: 1,
            share: 1,
          },
        ],
        previousTotal: 300000,
      },
    });

    expect(await screen.findByText(/지난 달 300,000/)).toBeInTheDocument();
    expect(screen.getByText(/\+100,000/)).toBeInTheDocument();
  });
});

describe("잔액 맞추기", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  it("차액을 미리 보여주고 조정한다", async () => {
    const user = userEvent.setup();
    renderAt("/ledger/assets/1", {
      assets: [assetView({ balance: 970000 })],
      reconcileDifference: -20000,
    });

    await user.click(
      await screen.findByRole("button", { name: /잔액 맞추기/ }),
    );

    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("실제 잔액"), "950000");

    // 저장하기 전에 무엇이 일어날지 보여준다.
    expect(
      within(dialog).getByText(/차이 −20,000 — 이만큼을 조정 거래로 남깁니다/),
    ).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "맞추기" }));

    expect(await screen.findByText("잔액을 맞췄어요")).toBeInTheDocument();
  });

  it("이미 맞으면 조정 거래를 만들지 않았다고 알린다", async () => {
    const user = userEvent.setup();
    renderAt("/ledger/assets/1", {
      assets: [assetView({ balance: 970000 })],
      reconcileDifference: 0,
    });

    await user.click(
      await screen.findByRole("button", { name: /잔액 맞추기/ }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("실제 잔액"), "970000");

    expect(
      within(dialog).getByText(/이미 맞아요 — 조정 거래를 만들지 않습니다/),
    ).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "맞추기" }));

    await waitFor(() =>
      expect(
        screen.getByText("이미 맞아요 — 조정할 것이 없었어요"),
      ).toBeInTheDocument(),
    );
  });

  it("잔액이 없는 자산에는 맞추기 버튼이 없다", async () => {
    renderAt("/ledger/assets/1", {
      assets: [
        assetView({
          type: "CREDIT_CARD",
          name: "신한 Deep Dream",
          balance: null,
          unpaidAmount: 180000,
        }),
      ],
    });

    await screen.findByRole("heading", { name: "신한 Deep Dream" });
    expect(screen.queryByRole("button", { name: /잔액 맞추기/ })).toBeNull();
  });
});
