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

describe("자산 화면", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  it("총자산·부채·순자산을 세 줄로 보여준다 — 순자산만 크게 두지 않는다", async () => {
    renderAt("/ledger/assets");

    expect(await screen.findByText("총자산")).toBeInTheDocument();
    expect(screen.getByText("부채")).toBeInTheDocument();
    expect(screen.getByText("순자산")).toBeInTheDocument();
  });

  it("체크카드는 0이 아니라 「잔액 없음」이다", async () => {
    renderAt("/ledger/assets", {
      assets: [
        assetView(),
        assetView({
          id: 2,
          name: "체크카드",
          type: "DEBIT_CARD",
          balance: null,
          linkedAssetId: 1,
          linkedAssetName: "급여통장",
        }),
      ],
    });

    // 0을 적으면 「돈이 없다」로 읽히는데, 사실은 잔액이라는 개념이 없는 자산이다.
    expect(await screen.findByText(/잔액 없음/)).toHaveTextContent(
      "급여통장에서 출금",
    );
  });

  it("신용카드 사용액은 부채로 보인다", async () => {
    renderAt("/ledger/assets", {
      assets: [
        assetView({
          id: 3,
          name: "신한 Deep Dream",
          type: "CREDIT_CARD",
          balance: null,
          unpaidAmount: 180000,
        }),
      ],
    });

    expect(await screen.findByText("−180,000")).toBeInTheDocument();
  });
});

describe("내역 화면", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  it("확정만을 고르면 예정이 사라진다", async () => {
    const user = userEvent.setup();
    renderAt("/ledger/transactions", {
      transactions: [
        transactionView(),
        transactionView({
          id: 11,
          status: "SCHEDULED",
          occurredOn: "2026-09-30",
          title: "넷플릭스",
          amount: 17000,
        }),
      ],
    });

    expect(await screen.findByText("스타벅스 역삼")).toBeInTheDocument();
    expect(screen.getByText("넷플릭스")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "확정만" }));

    await waitFor(() => expect(screen.queryByText("넷플릭스")).toBeNull());
    expect(screen.getByRole("button", { name: "확정만" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("필터의 진실은 URL이다 — 주소로 바로 들어가도 그대로 걸린다", async () => {
    // 별도 상태가 아니라 쿼리에 두는 이유가 이것이다. 새로고침·뒤로가기에서
    // 화면과 주소가 어긋나지 않는다(링크 워크스페이스 선례).
    renderAt("/ledger/transactions?status=SCHEDULED", {
      transactions: [
        transactionView(),
        transactionView({
          id: 11,
          status: "SCHEDULED",
          occurredOn: "2026-09-30",
          title: "넷플릭스",
        }),
      ],
    });

    expect(await screen.findByText("넷플릭스")).toBeInTheDocument();
    expect(screen.queryByText("스타벅스 역삼")).toBeNull();
    expect(screen.getByRole("button", { name: "예정만" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("기준선은 「전체」일 때만 그린다", async () => {
    const user = userEvent.setup();
    renderAt("/ledger/transactions", {
      transactions: [
        transactionView(),
        transactionView({
          id: 11,
          status: "SCHEDULED",
          occurredOn: "2026-09-30",
          title: "넷플릭스",
        }),
      ],
    });

    expect(await screen.findByText("오늘 · 아래는 예정")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "예정만" }));

    // 한쪽만 보는 중이면 나눌 것이 없다.
    await waitFor(() =>
      expect(screen.queryByText("오늘 · 아래는 예정")).toBeNull(),
    );
  });

  it("미분류는 배지로 눈에 걸린다", async () => {
    renderAt("/ledger/transactions", {
      transactions: [transactionView({ categoryId: null, categoryName: null })],
    });

    expect(await screen.findByText("미분류")).toBeInTheDocument();
  });

  it("이체는 지출·수입과 따로 센다", async () => {
    renderAt("/ledger/transactions", {
      transactions: [transactionView()],
    });

    // 합계 바에 세 값이 나란히 있다 — 이체가 지출에 섞이면 이 모듈이 무너진다.
    expect(await screen.findByText(/지출 4,500/)).toBeInTheDocument();
    expect(screen.getByText(/이체 0/)).toBeInTheDocument();
  });

  it("환불은 돈이 돌아온 것이다 — 지출이 하나 더 생긴 것처럼 보이지 않는다", async () => {
    renderAt("/ledger/transactions", {
      transactions: [
        transactionView({
          id: 12,
          type: "INCOME",
          source: "REFUND",
          refundOfId: 10,
          amount: 10000,
          title: "장보기",
        }),
      ],
    });

    const row = (await screen.findByText("장보기")).closest("li");
    // 「지출이 줄었다」는 합계의 이야기다. 이 줄에서 일어난 일은 잔액이 늘어난 것이고,
    // −10,000으로 그리면 같은 돈을 두 번 쓴 것처럼 읽힌다.
    expect(within(row as HTMLElement).getByText("+10,000")).toBeInTheDocument();
    expect(within(row as HTMLElement).getByText("환불")).toBeInTheDocument();
  });

  it("외화 거래는 원화가 본문이고 외화는 보조 표기다", async () => {
    renderAt("/ledger/transactions", {
      transactions: [
        transactionView({
          amount: 11213,
          title: "이치란 라멘",
          fx: { currency: "JPY", amount: 1280, rate: 8.7604 },
        }),
      ],
    });

    // 날짜 그룹 헤더에도 같은 합계가 찍히므로 그 줄 안에서 찾는다.
    const row = (await screen.findByText("이치란 라멘")).closest("li");
    expect(within(row as HTMLElement).getByText(/−11,213/)).toBeInTheDocument();
    expect(
      within(row as HTMLElement).getByText("1280 JPY"),
    ).toBeInTheDocument();
  });
});

describe("설정 화면", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  it("월 시작일이 예산 기간에만 쓰인다고 알린다", async () => {
    renderAt("/ledger/settings", { monthStartDay: 25 });

    expect(
      await screen.findByText(/카드 결제일과 정기 항목 주기는 이 값에 따라/),
    ).toBeInTheDocument();
  });
});
