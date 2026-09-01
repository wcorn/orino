import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import {
  type LedgerMockOptions,
  mockLedgerApi,
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
 * 셀렉트 고르기.
 *
 * <p>네이티브 `<select>`가 아니라 리스트박스라 `selectOptions`가 통하지 않는다 —
 * 사람이 하는 대로 열고 고른다.
 */
async function choose(
  user: ReturnType<typeof userEvent.setup>,
  field: string,
  option: string,
) {
  await user.click(screen.getByRole("combobox", { name: field }));
  await user.click(await screen.findByRole("option", { name: option }));
}

/** 파일을 고른 뒤 열 맞추기까지 가는 공통 동선. */
async function pickFileAndMap(user: ReturnType<typeof userEvent.setup>) {
  const file = new File(
    ["날짜,내용,금액\n2026-08-10,스타벅스,-5500\n"],
    "card.csv",
    {
      type: "text/csv",
    },
  );
  await user.upload(await screen.findByLabelText("가져올 파일"), file);

  // 스테퍼와 매핑 제목에 같은 말이 있다 — 폼이 떴는지는 자산 셀렉트로 본다.
  await screen.findByRole("combobox", { name: "자산" });
  await choose(user, "자산", "급여통장");
  await choose(user, "날짜 열", "1. 날짜");
  await choose(user, "금액 열", "3. 금액");
  await user.click(screen.getByRole("button", { name: "미리 보기" }));
}

/**
 * 이관 화면(#1268).
 *
 * <p>여기서 확인하는 것은 「파일이 올라가나」가 아니라 <b>사람이 판단할 자리가 남아 있는가</b>다.
 * 중복은 꺼진 채로 보이고, 합치는 버튼은 없으며, 되돌리기는 이 파일로 들어온 줄만 지운다.
 */
describe("가져오기", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  /**
   * 은행 파일을 받은 그대로(#1318).
   *
   * <p>카카오뱅크 거래내역은 앞 10줄이 안내문이고 11행이 머리글이다. 1행을 머리글로 못 박으면
   * 열 이름이 전부 「(이름 없음)」이 되어, 사람이 원본을 따로 열어 <b>열 번호를 세어야</b> 한다.
   */
  it("안내문이 앞에 붙어 있으면 건너뛸 줄 수를 알아서 채운다", async () => {
    const user = userEvent.setup();
    renderAt("/ledger/import", {
      importAnalyze: {
        headers: ["거래일시", "구분", "거래금액", "내용"],
        sample: [["2026.01.10 09:12:00", "출금", "-5,500", "스타벅스 역삼"]],
        totalRows: 3,
        headerRow: 6,
      },
    });

    const file = new File(
      ["안내문\n거래일시,구분,거래금액,내용\n"],
      "거래내역.csv",
      {
        type: "text/csv",
      },
    );
    await user.upload(await screen.findByLabelText("가져올 파일"), file);

    await screen.findByRole("combobox", { name: "자산" });
    // 머리글이 7번째 줄이었으니 그 앞 일곱 줄을 건너뛴다.
    expect(screen.getByLabelText("건너뛸 머리글 줄 수")).toHaveValue("7");
    // 열 이름이 제대로 보여야 사람이 고를 수 있다 — 「(이름 없음)」이면 번호를 세야 한다.
    expect(
      screen.getByRole("columnheader", { name: "1. 거래일시" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("columnheader", { name: "3. 거래금액" }),
    ).toBeInTheDocument();
  });

  it("암호가 걸린 파일은 무엇이 문제인지 말하고, 비밀번호를 받으면 읽는다", async () => {
    const user = userEvent.setup();
    renderAt("/ledger/import", { importFirstAnalyzeFails: "LDG-ERR-035" });

    await user.upload(
      await screen.findByLabelText("가져올 파일"),
      new File(["암호"], "거래내역.csv", { type: "text/csv" }),
    );

    // 「.xlsx만 됩니다」라고 답하면 .xlsx를 든 사람은 고칠 것을 찾지 못한다.
    expect(
      await screen.findByText(/암호가 걸린 파일이에요/),
    ).toBeInTheDocument();

    // 같은 파일을 다시 고르는 것으로는 브라우저가 아무 일도 하지 않는다 —
    // 그래서 고른 파일을 들고 있다가 「다시 읽기」로 연다.
    await user.type(screen.getByLabelText("파일 비밀번호"), "990820");
    await user.click(screen.getByRole("button", { name: "이 파일 다시 읽기" }));

    expect(await screen.findByRole("combobox", { name: "자산" })).toBeVisible();
  });

  /**
   * <b>자동 병합 금지</b>(`LDG-092`). 후보를 보여주고 체크를 꺼 둘 뿐이다 —
   * 합치는 버튼이 있으면 그 순간 사람은 무엇이 합쳐졌는지 모르게 된다.
   */
  it("중복 후보를 경고하고 체크를 꺼 둔다 — 합치는 버튼은 없다", async () => {
    const user = userEvent.setup();
    renderAt("/ledger/import", {
      importPreview: {
        rows: [
          {
            rowNumber: 2,
            occurredOn: "2026-08-10",
            type: "EXPENSE",
            amount: 5500,
            title: "스타벅스 역삼",
            duplicateOf: 42,
          },
          {
            rowNumber: 3,
            occurredOn: "2026-08-11",
            type: "EXPENSE",
            amount: 3200,
            title: "편의점",
          },
        ],
      },
    });

    await pickFileAndMap(user);

    const alert = await screen.findByText(/중복 후보 1건/);
    expect(alert).toBeInTheDocument();
    expect(screen.getByText(/자동으로 병합하지 않습니다/)).toBeInTheDocument();

    // 중복 줄은 꺼져 있고, 나머지는 켜져 있다.
    expect(screen.getByLabelText("2번째 줄 넣기")).not.toBeChecked();
    expect(screen.getByLabelText("3번째 줄 넣기")).toBeChecked();
    // 합치는 문이 화면에도 없다.
    expect(screen.queryByRole("button", { name: /병합/ })).toBeNull();
    expect(
      screen.getByRole("button", { name: "1건 넣기" }),
    ).toBeInTheDocument();
  });

  /**
   * 실행 목록에서 빼는 것이 중복을 다루는 <b>유일한</b> 방법이다 — 그래서 켤 수 있어야 하고,
   * 켜면 넣을 수 있어야 한다.
   *
   * <p>보낸 줄 번호 자체는 여기서 못 본다: jsdom이 multipart 파트의 내용을 비운 채 보내
   * 요청 본문을 읽을 수 없다. 그건 BE 통합 테스트가 지킨다(`executesSelectedRows`).
   * 화면이 책임지는 것은 <b>버튼이 몇 건을 넣겠다고 말하는가</b>이고, 그건 여기서 확인한다.
   */
  it("중복은 꺼진 채로 오지만 켤 수 있다", async () => {
    const user = userEvent.setup();
    renderAt("/ledger/import", {
      importPreview: {
        rows: [
          {
            rowNumber: 2,
            occurredOn: "2026-08-10",
            type: "EXPENSE",
            amount: 5500,
            title: "스타벅스 역삼",
            duplicateOf: 42,
          },
        ],
      },
    });

    await pickFileAndMap(user);

    // 다 꺼져 있으면 넣을 것이 없으니 버튼도 눌리지 않는다.
    const submit = await screen.findByRole("button", { name: "0건 넣기" });
    expect(submit).toBeDisabled();

    await user.click(screen.getByLabelText("2번째 줄 넣기"));
    await user.click(await screen.findByRole("button", { name: "1건 넣기" }));

    // 완료 화면과 토스트가 같은 말을 한다 — 둘 다 세고 넘어간다.
    // 실행 뒤에 목록을 다 새로 받아 와서 기본 1초로는 모자란다.
    expect(
      await screen.findAllByText(/1건을 넣었어요/, {}, { timeout: 3000 }),
    ).not.toHaveLength(0);
  });

  /** 조용히 빠지면 사람은 전부 들어갔다고 믿는다. */
  it("읽지 못한 줄을 사유와 함께 보여주고 넣지 못하게 한다", async () => {
    const user = userEvent.setup();
    renderAt("/ledger/import", {
      importPreview: {
        rows: [
          {
            rowNumber: 2,
            occurredOn: null,
            type: null,
            amount: null,
            title: "깨진 줄",
            error: "날짜를 읽을 수 없습니다",
          },
        ],
      },
    });

    await pickFileAndMap(user);

    expect(await screen.findByText(/읽지 못한 줄 1건/)).toBeInTheDocument();
    expect(screen.getByText("날짜를 읽을 수 없습니다")).toBeInTheDocument();
    expect(screen.getByLabelText("2번째 줄 넣기")).toBeDisabled();
  });

  it("자동 분류 결과를 미리 보여준다", async () => {
    const user = userEvent.setup();
    renderAt("/ledger/import", {
      importPreview: {
        rows: [
          {
            rowNumber: 2,
            occurredOn: "2026-08-10",
            type: "EXPENSE",
            amount: 5500,
            title: "스타벅스 역삼",
            categoryId: 21,
            categoryName: "카페/간식",
          },
        ],
      },
    });

    await pickFileAndMap(user);

    expect(await screen.findByText("카페/간식")).toBeInTheDocument();
  });

  describe("이력", () => {
    it("되돌리면 그 가져오기로 들어온 줄만 사라진다고 알린다", async () => {
      const user = userEvent.setup();
      const sent = renderAt("/ledger/import", {
        importBatches: [{ id: 7, source: "신한카드 8월" }],
      });

      expect(
        await screen.findByText(/손으로 적은 내역은 그대로 남습니다/),
      ).toBeInTheDocument();

      await user.click(await screen.findByRole("button", { name: /되돌리기/ }));
      await waitFor(() => expect(sent.reverts).toContain(7));
    });

    /** 두 번째 되돌리기는 서버가 거부한다 — 그 거부를 만나기 전에 버튼을 없앤다. */
    it("이미 되돌린 배치에는 되돌리기 버튼이 없다", async () => {
      renderAt("/ledger/import", {
        importBatches: [
          {
            id: 7,
            source: "신한카드 8월",
            revertedAt: "2026-08-29T00:00:00Z",
          },
        ],
      });

      expect(await screen.findByText("되돌림")).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /되돌리기/ })).toBeNull();
    });
  });
});

describe("자동 분류 규칙", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  /** 가져오기 화면 안에 두면 「파일로 넣을 때만 걸린다」로 읽힌다. */
  it("설정에 있고, 손으로 적을 때도 걸린다고 적는다", async () => {
    renderAt("/ledger/settings", {
      autoRules: [{ id: 1, keyword: "스타벅스", categoryId: 21 }],
    });

    expect(
      await screen.findByText(/손으로 적을 때도 똑같이 걸립니다/),
    ).toBeInTheDocument();
    expect(await screen.findByText("스타벅스")).toBeInTheDocument();
  });

  it("규칙을 추가하면 서버로 보낸다", async () => {
    const user = userEvent.setup();
    const sent = renderAt("/ledger/settings");

    await user.type(
      await screen.findByLabelText("내용에 이 말이 있으면"),
      "스타벅스",
    );
    await choose(user, "이 카테고리로", "식비");
    await user.click(screen.getByRole("button", { name: "추가" }));

    await waitFor(() =>
      expect(sent.autoRuleWrites).toContainEqual(
        expect.objectContaining({ keyword: "스타벅스", categoryId: 21 }),
      ),
    );
  });
});

describe("포인트", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  /**
   * <b>포인트는 돈이 아니다</b>(`LDG-006`). 총자산과 같은 칸에 놓이면 언젠가 더해진다.
   */
  it("총자산에 포함되지 않는다고 화면이 말한다", async () => {
    renderAt("/ledger/assets", {
      points: [
        {
          id: 1,
          name: "네이버페이",
          balance: 50000,
          expiresOn: "2026-09-10",
          daysLeft: 11,
          expiringSoon: true,
        },
      ],
    });

    expect(
      await screen.findByText(/총자산에 포함되지 않아요/),
    ).toBeInTheDocument();
    expect(await screen.findByText("네이버페이")).toBeInTheDocument();
    // 소멸 D-day는 서버가 센 값을 그대로 쓴다.
    expect(await screen.findByText("D-11")).toBeInTheDocument();
  });

  it("소멸일이 없으면 D-day 배지도 없다", async () => {
    renderAt("/ledger/assets", {
      points: [{ id: 1, name: "OK캐쉬백", balance: 3000 }],
    });

    const row = (await screen.findByText("OK캐쉬백")).closest("li");
    expect(row).not.toBeNull();
    expect(within(row as HTMLElement).queryByText(/^D-/)).toBeNull();
  });
});
