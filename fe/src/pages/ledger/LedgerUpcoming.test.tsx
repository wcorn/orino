import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import {
  type LedgerMockOptions,
  mockLedgerApi,
  transactionView,
  upcomingItem,
} from "@/features/ledger/ledgerFixtures";
import { todayIso } from "@/features/ledger/lib/period";
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
 * 예정 화면과 통합 타임라인(#1265).
 *
 * <p>확인하는 것은 「예정이 보이나」가 아니라 <b>네 출처가 한 목록에 모이되 같은 돈이 두 번
 * 세어지지 않는가</b>다. 직접 예약은 원장에도 있고 예정 목록에도 있어서, 그대로 합치면
 * 한 건이 두 줄이 된다.
 */
describe("예정 화면", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  it("네 출처가 한 목록에 종류 배지와 함께 나온다", async () => {
    renderAt("/ledger/upcoming", {
      upcoming: [
        upcomingItem({ title: "넷플릭스 프리미엄" }),
        upcomingItem({
          kind: "ONE_OFF",
          title: "재산세",
          amount: 500000,
          transactionId: 31,
          recurringId: null,
          occurrenceDate: null,
          date: "2026-09-03",
        }),
        upcomingItem({
          kind: "CARD_PAYMENT",
          title: "카드 대금 · 신한 Deep Dream",
          amount: 842000,
          isTransfer: true,
          flow: "TRANSFER",
          statementId: 7,
          recurringId: null,
          occurrenceDate: null,
          date: "2026-09-14",
        }),
        upcomingItem({
          kind: "INSTALLMENT",
          title: "할부 2회차 · 신한 Deep Dream",
          amount: 100000,
          isTransfer: true,
          flow: "TRANSFER",
          installmentId: 3,
          recurringId: null,
          occurrenceDate: null,
          date: "2026-10-14",
        }),
      ],
    });

    expect(await screen.findByText("넷플릭스 프리미엄")).toBeInTheDocument();
    expect(screen.getByText("재산세")).toBeInTheDocument();
    expect(screen.getByText("카드 대금 · 신한 Deep Dream")).toBeInTheDocument();
    expect(
      screen.getByText("할부 2회차 · 신한 Deep Dream"),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "전체 4" })).toBeInTheDocument();
  });

  it("종류 칩으로 한 출처만 볼 수 있다", async () => {
    const user = userEvent.setup();
    renderAt("/ledger/upcoming", {
      upcoming: [
        upcomingItem({ title: "넷플릭스 프리미엄" }),
        upcomingItem({
          kind: "CARD_PAYMENT",
          title: "카드 대금 · 신한",
          recurringId: null,
          occurrenceDate: null,
          statementId: 7,
        }),
      ],
    });

    await user.click(
      await screen.findByRole("button", { name: /카드 대금 1/ }),
    );

    expect(screen.getByText("카드 대금 · 신한")).toBeInTheDocument();
    expect(screen.queryByText("넷플릭스 프리미엄")).toBeNull();
  });

  /** 월말 숫자만 보면 괜찮아 보이는 달이 있다. 바닥이 언제인지가 그 자리에서 읽혀야 한다. */
  it("최저 예상 잔액과 그 이유를 경고로 보여준다", async () => {
    renderAt("/ledger/upcoming", {
      dashboard: { balance: 1500000 },
      upcoming: [upcomingItem({ title: "청약 이체", amount: 200000 })],
    });

    expect(await screen.findByText("최저 예상 잔액")).toBeInTheDocument();
    expect(
      screen.getByText(/「청약 이체」 직후가 가장 낮은 지점/),
    ).toBeInTheDocument();
  });

  it("미납 경고에 닫기 버튼이 없다", async () => {
    renderAt("/ledger/upcoming", {
      dashboard: { overdue: 1 },
      upcoming: [
        upcomingItem({
          title: "실손보험",
          amount: 42300,
          overdue: true,
          dday: -26,
        }),
      ],
    });

    // 최저 잔액 경고도 alert이라 미납 쪽을 제목으로 집어 든다.
    const heading = await screen.findByText(/미납 1건/);
    const alert = within(heading.closest('[role="alert"]') as HTMLElement);
    expect(alert.getByRole("button", { name: "건너뛰기" })).toBeInTheDocument();
    expect(alert.queryByRole("button", { name: /닫기|무시|해제/ })).toBeNull();
  });
});

describe("통합 타임라인", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  /**
   * 원장에 없는 예정(정기 회차·카드 대금)이 같은 스크롤 위에 있어야 한다. 원장만 그리면
   * 「14일에 카드값이 빠진다」가 이 화면에서 통째로 빠진다.
   */
  it("파생 예정이 원장 줄과 한 타임라인에 섞인다", async () => {
    renderAt("/ledger/transactions", {
      transactions: [transactionView()],
      upcoming: [
        upcomingItem({
          kind: "CARD_PAYMENT",
          title: "카드 대금 · 신한 Deep Dream",
          amount: 842000,
          isTransfer: true,
          flow: "TRANSFER",
          statementId: 7,
          recurringId: null,
          occurrenceDate: null,
          date: "2026-09-14",
          dday: 17,
        }),
      ],
    });

    expect(await screen.findByText("스타벅스 역삼")).toBeInTheDocument();
    expect(screen.getByText("카드 대금 · 신한 Deep Dream")).toBeInTheDocument();
    expect(screen.getByText("D-17")).toBeInTheDocument();
    // 이체 성격은 소비가 아니다 — 배지가 하나 더 붙는다.
    expect(screen.getAllByText("이체").length).toBeGreaterThan(0);
  });

  /** 직접 예약은 원장에 이미 있다. 파생 쪽에서 또 넣으면 한 건이 두 줄이 된다. */
  it("직접 예약이 두 번 그려지지 않는다", async () => {
    renderAt("/ledger/transactions", {
      transactions: [
        transactionView({
          id: 31,
          title: "재산세",
          status: "SCHEDULED",
          occurredOn: "2026-09-03",
          amount: 500000,
        }),
      ],
      upcoming: [
        upcomingItem({
          kind: "ONE_OFF",
          title: "재산세",
          amount: 500000,
          transactionId: 31,
          recurringId: null,
          occurrenceDate: null,
          date: "2026-09-03",
        }),
      ],
    });

    expect(await screen.findAllByText("재산세")).toHaveLength(1);
  });

  it("확정만을 고르면 파생 예정도 사라진다", async () => {
    const user = userEvent.setup();
    renderAt("/ledger/transactions", {
      transactions: [transactionView()],
      upcoming: [upcomingItem({ title: "넷플릭스 프리미엄" })],
    });

    await screen.findByText("넷플릭스 프리미엄");
    await user.click(screen.getByRole("button", { name: "확정만" }));

    expect(screen.queryByText("넷플릭스 프리미엄")).toBeNull();
    expect(screen.getByText("스타벅스 역삼")).toBeInTheDocument();
  });

  /** 예정을 고치려고 모달을 열게 하지 않는다 — 그 줄에서 끝나야 하는 일이다. */
  it("예정 줄에서 이번 회차 금액만 고친다", async () => {
    const user = userEvent.setup();
    const sent = renderAt("/ledger/transactions", {
      transactions: [transactionView()],
      upcoming: [upcomingItem({ title: "넷플릭스 프리미엄" })],
    });

    await screen.findByText("넷플릭스 프리미엄");
    await user.click(screen.getAllByRole("button", { name: "금액 수정" })[0]);
    const input = screen.getByLabelText("이번 회차 금액");
    await user.clear(input);
    await user.type(input, "22000");
    await user.click(screen.getByRole("button", { name: "적용" }));

    await screen.findByText("넷플릭스 프리미엄");
    expect(sent.occurrenceActions).toContainEqual(
      expect.objectContaining({
        recurringId: 12,
        occurrenceDate: "2026-08-31",
        action: "AMOUNT",
        amount: 22000,
      }),
    );
  });

  it("캘린더는 과거 확정과 미래 예정을 나눠 그린다", async () => {
    const user = userEvent.setup();
    // 캘린더는 「이번 달」의 칸만 그린다. 날짜를 박아 두면 그 달이 지나는 순간
    // 어느 칸에도 붙지 못해 깨진다 — 실제로 그렇게 깨졌다.
    const month = todayIso().slice(0, 7);
    renderAt("/ledger/transactions", {
      calendarDays: [
        { date: `${month}-05`, income: 3850000 },
        { date: `${month}-06`, expense: 4500 },
        { date: `${month}-07`, scheduledExpense: 17000 },
      ],
    });

    await user.click(await screen.findByRole("tab", { name: /캘린더/ }));

    expect(await screen.findByText("+3,850,000")).toBeInTheDocument();
    expect(screen.getByText("−4,500")).toBeInTheDocument();
    expect(screen.getByText("예정 17,000")).toBeInTheDocument();
  });

  /**
   * 예상 잔액 곡선(#1267 · §8.4).
   *
   * <p>월말 숫자 하나로는 「중간에 모자라지 않나」에 답할 수 없다. 25일에 청약이 빠지고
   * 월말에 급여가 들어오는 달은 <b>월말만 보면 멀쩡해 보인다</b>.
   */
  it("잔액이 마이너스가 되는 날을 미리 알린다", async () => {
    renderAt("/ledger/upcoming", {
      balanceCurve: {
        currentBalance: 300000,
        points: [
          { date: "2026-08-28", delta: 0, balance: 300000 },
          { date: "2026-09-06", delta: -520000, balance: -220000 },
          { date: "2026-09-25", delta: 3850000, balance: 3630000 },
        ],
        minBalance: {
          date: "2026-09-06",
          amount: -220000,
          reason: "재산세",
        },
        firstNegativeDate: "2026-09-06",
      },
    });

    expect(await screen.findByText("예상 잔액 곡선")).toBeInTheDocument();
    expect(
      screen.getByText(/잔액이 마이너스가 되는 날.*9월 6일/),
    ).toBeInTheDocument();
  });

  it("마이너스가 없으면 경고를 띄우지 않는다", async () => {
    renderAt("/ledger/upcoming", {
      balanceCurve: {
        currentBalance: 3000000,
        points: [
          { date: "2026-08-28", delta: 0, balance: 3000000 },
          { date: "2026-09-14", delta: -280000, balance: 2720000 },
        ],
        firstNegativeDate: null,
      },
    });

    await screen.findByText("예상 잔액 곡선");
    expect(screen.queryByText(/마이너스가 됩니다/)).toBeNull();
  });
});
