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
  upcomingItem,
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
 * 대시보드 · 통계 · 잔액 맞추기(#1261 · #1265).
 *
 * <p>v1에서는 이 테스트의 절반이 <b>없는 것을 확인하는 일</b>이었다 — 예정·미납·2축 요약이
 * 없는 것은 미완성이 아니라 결정이었기 때문이다(D-7). v1.5에서 예정이 생겨 그 자리가
 * 채워졌고, 이제 확인할 것은 <b>두 축이 섞이지 않았는가</b>다.
 */
describe("가계부 대시보드", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  it("이미 쓴 돈과 이번 달 수입을 보여준다", async () => {
    renderAt("/ledger", {
      dashboard: { spent: 1240000, scheduled: 180000, income: 3850000 },
    });

    expect(await screen.findByText("1,240,000")).toBeInTheDocument();
    expect(screen.getByText("3,850,000")).toBeInTheDocument();
    expect(screen.getByText(/2026년 8월 · 월 시작일 1일/)).toBeInTheDocument();
  });

  /**
   * 두 카드가 <b>서로 다른 값</b>을 말해야 한다. 카드로 쓴 돈은 소비에 한 번, 대금에 또 한 번
   * 세어지기 쉽고 그러면 「이번 달 얼마 쓰나」가 두 배가 된다(확정 명세 §8.2).
   */
  it("2축 요약 — 소비와 현금 유출이 다른 숫자를 답한다", async () => {
    renderAt("/ledger", {
      dashboard: {
        spent: 1240000,
        scheduled: 180000,
        balance: 1500000,
        remainingOutflow: 1062000,
      },
    });

    // 소비 축: 이미 쓴 돈 + 앞으로 쓸 돈.
    expect(await screen.findByText("이번 달 소비")).toBeInTheDocument();
    expect(screen.getByText("1,420,000")).toBeInTheDocument();
    // 현금 축: 통장 잔액 − 남은 예정 출금. 소비와 다른 숫자다.
    expect(screen.getByText("통장에서 나갈 돈")).toBeInTheDocument();
    expect(screen.getByText("438,000")).toBeInTheDocument();
    expect(
      screen.getByText(/예정 거래는 잔액을 바꾸지 않습니다/),
    ).toBeInTheDocument();
  });

  it("2단 게이지 — 확정분과 예정분을 따로 칠한다", async () => {
    renderAt("/ledger", {
      dashboard: { spent: 800000, scheduled: 200000 },
      budget: { totalAmount: 2000000 },
    });

    expect(
      await screen.findByLabelText(
        "예산 2,000,000 중 확정 800,000, 예정 200,000",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("확정 40% · 예정 10%")).toBeInTheDocument();
  });

  /** <b>「무시」에 해당하는 버튼이 없다.</b> 확정하거나 건너뛰어야만 사라진다(§6.4). */
  it("미납 경고에 닫기 버튼이 없다", async () => {
    renderAt("/ledger", {
      dashboard: { overdue: 1 },
      upcoming: [
        upcomingItem({
          title: "실손보험",
          amount: 42300,
          overdue: true,
          dday: -26,
          date: "2026-08-02",
          occurrenceDate: "2026-08-02",
        }),
      ],
    });

    const alert = within(await screen.findByRole("alert"));
    expect(alert.getByText(/미납 1건/)).toBeInTheDocument();
    expect(alert.getByRole("button", { name: "확정" })).toBeInTheDocument();
    expect(alert.getByRole("button", { name: "건너뛰기" })).toBeInTheDocument();
    // 「무시」에 해당하는 버튼이 없다. 눈에 거슬리는 게 목적이다.
    expect(alert.queryByRole("button", { name: /닫기|무시|해제/ })).toBeNull();
  });

  /** 순자산만 크게 보여주지 않는다 — 부채가 안 보이면 좋아 보이는 이유를 알 수 없다. */
  it("자산 요약은 총자산·부채·순자산 세 줄이다", async () => {
    renderAt("/ledger", {
      dashboard: { totalAssets: 6410300, liabilities: 1700500 },
    });

    expect(await screen.findByText("총자산")).toBeInTheDocument();
    expect(screen.getByText("부채")).toBeInTheDocument();
    expect(screen.getByText("순자산")).toBeInTheDocument();
    expect(screen.getByText("4,709,800")).toBeInTheDocument();
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
