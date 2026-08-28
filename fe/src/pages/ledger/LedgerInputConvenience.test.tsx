import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import {
  type LedgerMockOptions,
  mockLedgerApi,
  transactionView,
} from "@/features/ledger/ledgerFixtures";
import { renderWithRouter } from "@/test/render";

function renderAt(path: string, options: LedgerMockOptions = {}) {
  const captured = mockLedgerApi(options);
  renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: [path] },
  );
  return captured;
}

const TEMPLATE = {
  id: 5,
  name: "출근 커피",
  txType: "EXPENSE",
  amount: 4500,
  assetId: 1,
  assetName: "급여통장",
  categoryId: 21,
  categoryName: "식비",
  title: "출근 커피",
  useCount: 12,
};

/**
 * 입력 편의(#1270).
 *
 * <p>입력 마찰이 곧 이탈이다 — 성공 지표(주 5일 기록 · 1건당 30초)는 첫 입력이 아니라
 * <b>200번째 입력</b>에서 갈린다. 그래서 여기서 확인하는 것은 「기능이 있다」가 아니라
 * <b>손이 덜 간다</b>는 쪽이다.
 */
describe("빠른 입력 템플릿", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  it("대시보드 칩을 한 번 누르면 기록된다", async () => {
    const user = userEvent.setup();
    renderAt("/ledger", { templates: [TEMPLATE] });

    const chip = await screen.findByRole("button", { name: /출근 커피/ });
    expect(chip).toHaveTextContent("4,500");

    await user.click(chip);

    expect(await screen.findByText("저장했어요")).toBeInTheDocument();
  });

  it("템플릿이 없으면 빠른 입력 자리도 없다", async () => {
    renderAt("/ledger", { templates: [] });

    await screen.findByText("이미 쓴 돈");
    expect(screen.queryByText("빠른 입력")).toBeNull();
  });

  it("입력 모달에서 지금 값을 템플릿으로 저장한다", async () => {
    const user = userEvent.setup();
    renderAt("/ledger/transactions");

    await screen.findByRole("heading", { name: "내역" });
    await user.keyboard("n");
    const dialog = await screen.findByRole("dialog");

    // 저장할 값이 없으면 버튼도 없다 — 빈 템플릿을 만들 이유가 없다.
    expect(
      within(dialog).queryByRole("button", { name: "템플릿으로 저장" }),
    ).toBeNull();

    await user.type(within(dialog).getByLabelText("금액"), "4500");
    await user.click(
      within(dialog).getByRole("button", { name: "템플릿으로 저장" }),
    );

    expect(
      await screen.findByText("템플릿으로 저장했어요"),
    ).toBeInTheDocument();
  });
});

describe("내역 복사", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  it("행 메뉴에서 오늘 날짜로 복사한다", async () => {
    const user = userEvent.setup();
    const captured = renderAt("/ledger/transactions", {
      transactions: [transactionView()],
    });

    await user.click(
      await screen.findByRole("button", { name: /스타벅스 역삼 메뉴/ }),
    );
    await user.click(
      await screen.findByRole("menuitem", { name: "오늘 날짜로 복사" }),
    );

    await waitFor(() => expect(captured.duplicated).toHaveLength(1));
    expect(captured.duplicated[0]).toMatchObject({ useToday: true });
    expect(await screen.findByText("복사했어요")).toBeInTheDocument();
  });

  it("원본 날짜를 그대로 쓸 수도 있다", async () => {
    const user = userEvent.setup();
    const captured = renderAt("/ledger/transactions", {
      transactions: [transactionView()],
    });

    await user.click(
      await screen.findByRole("button", { name: /스타벅스 역삼 메뉴/ }),
    );
    await user.click(
      await screen.findByRole("menuitem", { name: "원본 날짜로 복사" }),
    );

    await waitFor(() => expect(captured.duplicated).toHaveLength(1));
    expect(captured.duplicated[0]).toMatchObject({ useToday: false });
  });
});

describe("다건 입력", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  it("줄을 더해 적고 저장 전에 합계를 보여준다", async () => {
    const user = userEvent.setup();
    const captured = renderAt("/ledger/transactions/bulk");

    await screen.findByRole("heading", { name: "여러 건 적기" });

    const amounts = () => screen.getAllByLabelText("금액");
    await user.type(amounts()[0], "4500");
    await user.click(screen.getByRole("button", { name: /줄 추가/ }));
    await user.type(amounts()[1], "12000");

    // 명세서 총액과 맞춰 보는 것이 이 화면의 마지막 확인이다.
    expect(screen.getByText(/2건 · 합계 16,500/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "전부 저장" }));

    await waitFor(() => expect(captured.bulkSent).toHaveLength(1));
    expect(captured.bulkSent[0]).toHaveLength(2);
    expect(await screen.findByText("2건을 저장했어요")).toBeInTheDocument();
  });

  it("한 줄이라도 잘못되면 전부 저장하지 않는다고 알린다", async () => {
    const user = userEvent.setup();
    renderAt("/ledger/transactions/bulk", { bulkFails: true });

    await screen.findByRole("heading", { name: "여러 건 적기" });
    await user.type(screen.getAllByLabelText("금액")[0], "4500");
    await user.click(screen.getByRole("button", { name: "전부 저장" }));

    // 「7건 성공 3건 실패」로 말하지 않는다 — 서버가 한 트랜잭션으로 처리한다.
    expect(
      await screen.findByText("한 줄이라도 잘못되면 전부 저장하지 않아요."),
    ).toBeInTheDocument();
  });

  it("빈 줄만 있으면 저장할 수 없다", async () => {
    renderAt("/ledger/transactions/bulk");

    await screen.findByRole("heading", { name: "여러 건 적기" });
    expect(screen.getByRole("button", { name: "전부 저장" })).toBeDisabled();
  });

  it("마지막 한 줄은 지울 수 없다 — 빈 화면에서는 다시 시작할 방법이 없다", async () => {
    renderAt("/ledger/transactions/bulk");

    await screen.findByRole("heading", { name: "여러 건 적기" });
    expect(screen.getByRole("button", { name: "줄 삭제" })).toBeDisabled();
  });
});

describe("영수증", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  it("행 메뉴에서 열면 붙어 있는 영수증이 보인다", async () => {
    const user = userEvent.setup();
    renderAt("/ledger/transactions", {
      transactions: [transactionView()],
      receipts: [
        {
          id: 1,
          objectKey: "ledger/receipts/1/a.jpg",
          url: "https://img.orino.dev/note-images/ledger/receipts/1/a.jpg",
          contentType: "image/jpeg",
          byteSize: 1024,
          displayOrder: 0,
        },
      ],
    });

    await user.click(
      await screen.findByRole("button", { name: /스타벅스 역삼 메뉴/ }),
    );
    await user.click(await screen.findByRole("menuitem", { name: /영수증/ }));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByRole("img", { name: "영수증" })).toHaveAttribute(
      "src",
      "https://img.orino.dev/note-images/ledger/receipts/1/a.jpg",
    );
  });

  it("붙인 것이 없으면 그렇다고 말한다", async () => {
    const user = userEvent.setup();
    renderAt("/ledger/transactions", {
      transactions: [transactionView()],
      receipts: [],
    });

    await user.click(
      await screen.findByRole("button", { name: /스타벅스 역삼 메뉴/ }),
    );
    await user.click(await screen.findByRole("menuitem", { name: /영수증/ }));

    expect(
      await screen.findByText("아직 붙인 영수증이 없어요."),
    ).toBeInTheDocument();
  });
});
