import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import {
  cardView,
  type LedgerMockOptions,
  mockLedgerApi,
  recurringView,
  statementView,
} from "@/features/ledger/ledgerFixtures";
import { renderWithRouter } from "@/test/render";

function renderAt(path: string, options: LedgerMockOptions = {}) {
  const sent = mockLedgerApi(options);
  renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: [path] },
  );
  return sent;
}

/**
 * 카드 청구서 · 정기 항목 · 예산(#1266).
 *
 * <p>청구서 화면에서 확인하는 것은 합계가 아니라 <b>산식</b>이다 — 카드사 앱과 다를 때
 * 어디가 다른지 알 수 있어야 하고, 그러려면 일곱 항목이 그대로 보여야 한다.
 */
describe("카드 청구서", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  it("산식을 항목째로 보여준다 — 합계만 주지 않는다", async () => {
    renderAt("/ledger/cards/3/statements", {
      cards: [cardView()],
      statements: [
        statementView({
          breakdown: {
            usage: 842000,
            installment: 100000,
            carriedOver: 62000,
            interestFee: 12000,
            adjustment: 0,
            refund: 0,
            discount: 5000,
            billed: 1011000,
            paid: 200000,
            remaining: 811000,
          },
          status: "PARTIAL",
        }),
      ],
    });

    // 스탯 스트립과 브레이크다운 둘 다 같은 이름을 쓴다 — 같은 값을 두 자리에서 읽는다.
    expect(await screen.findByText("사용 합계")).toBeInTheDocument();
    expect(screen.getAllByText("할부 회차").length).toBeGreaterThan(0);
    // 이월은 따로 보여야 한다 — 합계에 섞이면 「왜 이렇게 많지」에 답할 수 없다.
    expect(screen.getAllByText("이월 잔액").length).toBeGreaterThan(0);
    expect(screen.getByText("이자·수수료")).toBeInTheDocument();
    expect(screen.getByText("할인")).toBeInTheDocument();
    expect(screen.getByText("1,011,000")).toBeInTheDocument();
  });

  /** 「왜 매번 눌러야 하나」에 답하지 않으면 다음 버전에서 누가 자동 기록을 넣는다. */
  it("결제 처리 모달이 왜 자동이 아닌지 먼저 말한다", async () => {
    const user = userEvent.setup();
    renderAt("/ledger/cards/3/statements", {
      cards: [cardView()],
      statements: [statementView()],
    });

    await user.click(await screen.findByRole("button", { name: "결제 처리" }));

    expect(
      screen.getByText("카드 대금은 자동으로 적지 않습니다"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/모르는 걸 아는 척 적어두면 원장이 조용히 틀어집니다/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/결제 시 생성되는 이체는 지출로 계상하지 않습니다/),
    ).toBeInTheDocument();
  });

  it("전액 결제는 금액을 보내지 않는다 — 남은 전액이라는 뜻이다", async () => {
    const user = userEvent.setup();
    const sent = renderAt("/ledger/cards/3/statements", {
      cards: [cardView()],
      statements: [statementView()],
    });

    await user.click(await screen.findByRole("button", { name: "결제 처리" }));
    const dialog = within(screen.getByRole("dialog"));
    await user.click(dialog.getByRole("button", { name: "결제 처리" }));

    await screen.findByText("사용 합계");
    expect(sent.payments).toHaveLength(1);
    expect(sent.payments[0]).toMatchObject({ amount: null });
  });

  /** 이월은 지출이 아니다. 이 문장이 없으면 다음 달 지출이 부풀어 보인다. */
  it("일부 결제는 이월 금액과 「이월은 지출이 아니다」를 함께 알린다", async () => {
    const user = userEvent.setup();
    const sent = renderAt("/ledger/cards/3/statements", {
      cards: [cardView()],
      statements: [statementView()],
    });

    await user.click(await screen.findByRole("button", { name: "결제 처리" }));
    const dialog = within(screen.getByRole("dialog"));
    await user.click(dialog.getByRole("button", { name: /일부/ }));
    await user.type(dialog.getByLabelText("결제 금액"), "400000");

    expect(
      screen.getByText("442,000이 다음 청구서로 이월됩니다"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /이월 자체는 지출이 아니고, 리볼빙 수수료만 새 지출입니다/,
      ),
    ).toBeInTheDocument();

    await user.click(dialog.getByRole("button", { name: "결제 처리" }));

    await screen.findByText("사용 합계");
    expect(sent.payments[0]).toMatchObject({ amount: 400000 });
  });

  it("사이클이 없는 카드는 오류가 아니라 「미등록」이다", async () => {
    renderAt("/ledger/cards", {
      cards: [
        cardView({
          hasCycle: false,
          cycleStartDay: null,
          cycleCloseDay: null,
          paymentDay: null,
          currentStatement: null,
        }),
      ],
    });

    expect(await screen.findByText("사이클 미등록")).toBeInTheDocument();
  });
  /**
   * 카드 실적(#1267 · §7.6).
   *
   * <p>승인이냐 청구냐는 <b>카드 속성</b>이다. 전역 설정이면 카드 두 장을 쓰는 순간
   * 한쪽이 반드시 틀리고, 틀린 쪽은 「채웠다고 믿었는데 안 채워진」 형태로 드러난다.
   */
  it("실적 진행에 어느 기준인지가 함께 적힌다", async () => {
    renderAt("/ledger/cards", {
      cards: [
        cardView({
          usageGoal: {
            goalAmount: 500000,
            basis: "APPROVAL",
            counted: 480000,
            remaining: 20000,
            achieved: false,
          },
        }),
      ],
    });

    expect(await screen.findByText("승인 기준")).toBeInTheDocument();
    expect(screen.getByText("480,000 / 500,000")).toBeInTheDocument();
    expect(screen.getByText(/20,000원 더 쓰면/)).toBeInTheDocument();
  });

  it("조건을 안 걸어 둔 카드에는 실적을 그리지 않는다", async () => {
    renderAt("/ledger/cards", { cards: [cardView({ usageGoal: null })] });

    await screen.findByText("신한 Deep Dream");
    // 0%로 그리면 「하나도 못 채웠다」로 읽히는데 사실은 「조건이 없다」다.
    expect(screen.queryByText("실적")).toBeNull();
    expect(screen.queryByText("승인 기준")).toBeNull();
  });
});

describe("정기 항목", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  /** 해지해도 사라지지 않는다 — 연간 고정비 회고에 「올해 넉 달 냈다」가 있어야 한다. */
  it("해지한 항목이 「종료됨」으로 목록에 남는다", async () => {
    renderAt("/ledger/recurring", {
      recurring: [
        recurringView(),
        recurringView({
          id: 13,
          name: "왓챠",
          status: "ENDED",
          endedOn: "2026-07-01",
          nextDate: null,
        }),
      ],
    });

    expect(await screen.findByText("왓챠")).toBeInTheDocument();
    expect(screen.getByText("종료됨")).toBeInTheDocument();
    expect(
      screen.getByText(/해지한 항목은 목록에서 사라지지 않고/),
    ).toBeInTheDocument();
  });

  it("점검 신호가 카드로 뜬다 — 목록이 아니라 점검 도구다", async () => {
    renderAt("/ledger/recurring", {
      recurring: [recurringView()],
      recurringSignals: {
        priceIncreased: [
          {
            recurringId: 12,
            name: "넷플릭스 프리미엄",
            from: 12000,
            to: 17000,
            changedOn: "2026-03-01",
          },
        ],
        noEndDate: [12],
      },
    });

    expect(await screen.findByText("최근 인상 1건")).toBeInTheDocument();
    expect(
      screen.getByText("넷플릭스 프리미엄 12,000 → 17,000"),
    ).toBeInTheDocument();
    expect(screen.getByText("무기한 항목 1개")).toBeInTheDocument();
  });

  /** 변동은 예상액이다 — 고지서가 오면 고쳐야 한다는 뜻으로 물결표를 붙인다. */
  it("변동 금액은 물결표로 표기한다", async () => {
    renderAt("/ledger/recurring", {
      recurring: [
        recurringView({
          name: "전기요금",
          amountType: "VARIABLE",
          amount: 152000,
        }),
      ],
    });

    expect(await screen.findByText("~152,000")).toBeInTheDocument();
  });
});

describe("예산", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  it("2단 게이지와 하루 사용 가능액을 보여준다", async () => {
    renderAt("/ledger/budget", {
      budget: { totalAmount: 1500000, spent: 1240000, scheduled: 180000 },
    });

    expect(
      await screen.findByLabelText(
        "예산 1,500,000 중 확정 1,240,000, 예정 180,000",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("하루 사용 가능액")).toBeInTheDocument();
  });

  /** 초과는 경고가 아니라 사실이다. 색으로만 말하면 지나친다. */
  it("예산을 넘긴 카테고리에 「초과」 배지가 붙는다", async () => {
    renderAt("/ledger/budget", {
      budget: { totalAmount: 1500000, spent: 900000 },
      budgetCategories: [
        {
          categoryId: 21,
          name: "식비",
          amount: 500000,
          spent: 620000,
          scheduled: 0,
        },
      ],
    });

    expect(await screen.findByText("식비")).toBeInTheDocument();
    expect(screen.getByText("초과")).toBeInTheDocument();
  });

  it("여행 경비가 월 예산과 분리된다고 알린다", async () => {
    renderAt("/ledger/budget", { budget: { totalAmount: 1500000 } });

    expect(
      await screen.findByText("여행 경비 예산은 월 예산과 분리됩니다"),
    ).toBeInTheDocument();
  });
});
