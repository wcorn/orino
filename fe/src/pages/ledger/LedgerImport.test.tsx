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

function csvFile(name: string) {
  return new File(["날짜,내용,금액\n2026-08-10,스타벅스,-5500\n"], name, {
    type: "text/csv",
  });
}

/**
 * 파일을 고르고 열 맞추기 단계까지 간다.
 *
 * <p>파일을 고르는 것만으로 넘어가지 않는다 — 여러 장을 고르는 중일 수 있어서, 다 골랐다는
 * 것은 사람이 말한다.
 */
async function pickFiles(
  user: ReturnType<typeof userEvent.setup>,
  files: File[],
) {
  await user.upload(await screen.findByLabelText("가져올 파일"), files);
  const next = await screen.findByRole("button", { name: "열 맞추기" });
  await waitFor(() => expect(next).toBeEnabled());
  await user.click(next);
  // 스테퍼와 매핑 제목에 같은 말이 있다 — 폼이 떴는지는 자산 셀렉트로 본다.
  await screen.findByRole("combobox", { name: "자산" });
}

/** 지금 보고 있는 파일의 열을 맞춘다. */
async function mapActiveFile(user: ReturnType<typeof userEvent.setup>) {
  await choose(user, "자산", "급여통장");
  await choose(user, "날짜 열", "1. 날짜");
  await choose(user, "금액 열", "3. 금액");
}

/** 파일 한 장을 골라 열을 맞추고 미리보기까지 가는 공통 동선. */
async function pickFileAndMap(user: ReturnType<typeof userEvent.setup>) {
  await pickFiles(user, [csvFile("card.csv")]);
  await mapActiveFile(user);
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

    await pickFiles(user, [
      new File(["안내문\n거래일시,구분,거래금액,내용\n"], "거래내역.csv", {
        type: "text/csv",
      }),
    ]);

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

  /**
   * 못 읽은 파일을 그냥 지나치면 사람은 전부 들어갔다고 믿는다 — 그래서 목록에 남기고,
   * 다음으로 넘어가는 문을 잠근다.
   */
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
    expect(screen.getByText(/읽지 못한 파일이 1장 있어요/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "열 맞추기" })).toBeDisabled();

    // 같은 파일을 다시 고르는 것으로는 브라우저가 아무 일도 하지 않는다 —
    // 그래서 고른 파일을 들고 있다가 「다시 읽기」로 연다.
    await user.type(screen.getByLabelText("이 파일의 비밀번호"), "990820");
    await user.click(screen.getByRole("button", { name: "다시 읽기" }));

    const next = await screen.findByRole("button", { name: "열 맞추기" });
    await waitFor(() => expect(next).toBeEnabled());
    await user.click(next);
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

    // 파일별 구획도 제 몫을 세므로, 합계를 말하는 것은 제목 전체로 가린다.
    expect(
      await screen.findByText(/중복 후보 1건 — 자동으로 병합하지 않습니다/),
    ).toBeInTheDocument();

    // 중복 줄은 꺼져 있고, 나머지는 켜져 있다.
    expect(screen.getByLabelText(/2번째 줄 넣기/)).not.toBeChecked();
    expect(screen.getByLabelText(/3번째 줄 넣기/)).toBeChecked();
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

    await user.click(screen.getByLabelText(/2번째 줄 넣기/));
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
    expect(screen.getByLabelText(/2번째 줄 넣기/)).toBeDisabled();
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

  /**
   * 파일 여러 장(#1320).
   *
   * <p>은행이 내려주는 거래내역은 한 장이 아니다. 여기서 확인하는 것은 <b>아홉 번 반복하지
   * 않아도 되는가</b>와, 파일끼리 겹치는 줄이 <b>넣기 전에</b> 드러나는가다.
   */
  describe("파일 여러 장", () => {
    it("두 장을 한 번에 골라, 설정을 퍼뜨려 한 번에 넣는다", async () => {
      const user = userEvent.setup();
      renderAt("/ledger/import", {
        importPreview: {
          files: [
            {
              fileName: "2026-01.csv",
              rows: [
                {
                  rowNumber: 2,
                  occurredOn: "2026-01-10",
                  type: "EXPENSE",
                  amount: 5500,
                  title: "스타벅스",
                },
              ],
            },
            {
              fileName: "2026-02.csv",
              rows: [
                {
                  rowNumber: 2,
                  occurredOn: "2026-02-10",
                  type: "EXPENSE",
                  amount: 3200,
                  title: "편의점",
                },
              ],
            },
          ],
        },
        importExecute: {
          inserted: 2,
          batches: [
            {
              batchId: 1,
              fileName: "2026-01.csv",
              inserted: 1,
              skipped: 0,
            },
            {
              batchId: 2,
              fileName: "2026-02.csv",
              inserted: 1,
              skipped: 0,
            },
          ],
        },
      });

      await user.upload(await screen.findByLabelText("가져올 파일"), [
        csvFile("2026-01.csv"),
        csvFile("2026-02.csv"),
      ]);
      expect(await screen.findByText("고른 파일 2장")).toBeInTheDocument();

      const next = screen.getByRole("button", { name: "열 맞추기" });
      await waitFor(() => expect(next).toBeEnabled());
      await user.click(next);

      // 같은 곳에서 받은 아홉 장에 같은 매핑을 아홉 번 적는 것은 고문이다.
      await mapActiveFile(user);
      await user.click(
        screen.getByRole("button", { name: /이 설정을 나머지 1장에도/ }),
      );
      expect(await screen.findByText("1장에 적용했어요")).toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: "미리 보기" }));

      // 파일 경계가 확인 화면에 남는다 — 줄 번호는 파일 안에서 세기 때문이다.
      expect(
        await screen.findByRole("heading", { name: "2026-01.csv" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "2026-02.csv" }),
      ).toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: "2건 넣기" }));

      // 배치는 파일마다 하나다 — 완료 화면도 파일마다 한 줄로 알린다.
      expect(
        await screen.findByText(
          /파일 2장에서 2건을 넣었어요/,
          {},
          { timeout: 3000 },
        ),
      ).toBeInTheDocument();
      expect(screen.getByText(/2026-01.csv — 1건/)).toBeInTheDocument();
    });

    /**
     * 기간이 겹치게 내려받은 파일을 함께 올리면 겹치는 구간이 두 번 들어간다. 파일마다 따로
     * 미리 보면 <b>둘째 파일을 볼 때 첫 파일은 아직 원장에 없어서</b> 「중복 없음」으로
     * 지나간다 — 그래서 한 번에 보고, 어느 파일 몇 번째 줄인지까지 말한다.
     */
    it("앞 파일과 겹치는 줄을 어느 파일 몇 번째 줄인지까지 알리고 꺼 둔다", async () => {
      const user = userEvent.setup();
      renderAt("/ledger/import", {
        importPreview: {
          files: [
            {
              fileName: "3분기.csv",
              rows: [
                {
                  rowNumber: 3,
                  occurredOn: "2026-01-11",
                  type: "EXPENSE",
                  amount: 3200,
                  title: "편의점",
                },
              ],
            },
            {
              fileName: "1월.csv",
              rows: [
                {
                  rowNumber: 2,
                  occurredOn: "2026-01-11",
                  type: "EXPENSE",
                  amount: 3200,
                  title: "편의점",
                  duplicateOfRow: { fileIndex: 0, rowNumber: 3 },
                },
              ],
            },
          ],
        },
      });

      await pickFiles(user, [csvFile("3분기.csv"), csvFile("1월.csv")]);
      await mapActiveFile(user);
      await user.click(
        screen.getByRole("button", { name: /이 설정을 나머지 1장에도/ }),
      );
      await user.click(screen.getByRole("button", { name: "미리 보기" }));

      expect(
        await screen.findByText(/중복 후보 1건 — 자동으로 병합하지 않습니다/),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/「3분기.csv」의 3번째 줄과 같아 보여요/),
      ).toBeInTheDocument();
      // 앞 파일의 줄도 기존 거래와 똑같이 꺼진 채로 온다.
      expect(screen.getByLabelText(/1월.csv 2번째 줄 넣기/)).not.toBeChecked();
      expect(
        screen.getByRole("button", { name: "1건 넣기" }),
      ).toBeInTheDocument();
    });
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
