import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { Providers } from "@/app/providers";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import type { ExpenseRow } from "../api/expenses";
import { ExpenseQuickSheet } from "./ExpenseQuickSheet";

const API_BASE = "https://api.orino.dev/api";

/** 여행 도구의 환율. 가계부 `/ledger/fx/rate`가 아니다 — 그건 2단계에서 사라진다. */
function mockFx(rate: number | null = 9.4166) {
  server.use(
    http.get(`${API_BASE}/travel/fx`, ({ request }) => {
      if (rate === null) {
        // 고시표를 못 받은 상태. 화면은 환산을 지어내지 않고 저장은 그대로 된다.
        return HttpResponse.json(
          { code: "TRAVEL-ERR-010", message: "환율을 가져올 수 없습니다." },
          { status: 503 },
        );
      }
      const url = new URL(request.url);
      return HttpResponse.json({
        code: "OK",
        data: {
          base: url.searchParams.get("base"),
          quote: url.searchParams.get("quote"),
          rate,
          source: "ECB",
          referenceDate: "2026-10-27",
          fetchedAt: "2026-10-27T00:00:00Z",
        },
      });
    }),
  );
}

function row(partial: Partial<ExpenseRow> = {}): ExpenseRow {
  return {
    expenseId: 4301,
    title: "이자카야",
    amount: 32000,
    fx: null,
    status: "CONFIRMED",
    category: "FOOD",
    paymentMethod: "국민 체크",
    uncategorized: false,
    overdue: false,
    occurredOn: "2026-10-27",
    ...partial,
  };
}

function renderSheet(
  props: Partial<Parameters<typeof ExpenseQuickSheet>[0]> = {},
) {
  const onCreate = vi.fn();
  const onUpdate = vi.fn();
  const onDelete = vi.fn();
  const view = renderWithRouter(
    <Providers>
      <ExpenseQuickSheet
        open
        onOpenChange={() => {}}
        tripId={12}
        cityName="오사카"
        cityCurrency="JPY"
        occurredOn="2026-10-27"
        editing={null}
        paymentMethods={["국민 체크", "현금"]}
        onCreate={onCreate}
        onUpdate={onUpdate}
        onDelete={onDelete}
        pending={false}
        {...props}
      />
    </Providers>,
  );
  return { ...view, onCreate, onUpdate, onDelete };
}

/**
 * 지출 시트(경비 독립 §6.1 · §7). 지키는 것이 둘이다.
 *
 * <p>하나는 <b>30초 안에 끝난다</b> — 여기 있는 테스트 대부분이 「무엇을 안 골라도
 * 저장되나」와 「기본값이 맞나」인 이유다. 분류를 고르느라 기록을 포기하는 것이 이 기능의
 * 유일한 실패 방식이다.
 *
 * <p>다른 하나는 <b>입력과 편집이 한 벌</b>이라는 것이다. 같은 컴포넌트가 두 일을 하므로
 * 「적을 때는 되는데 고칠 때는 안 되는 것」이 생기지 않는다.
 */
describe("ExpenseQuickSheet", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
    localStorage.clear();
  });

  it("금액만 적어도 저장된다 — 제목도 분류도 나중에 채운다", async () => {
    mockFx();
    const user = userEvent.setup();
    const { onCreate } = renderSheet({ cityCurrency: "KRW" });

    await user.type(await screen.findByLabelText("금액"), "4500");
    await user.click(screen.getByRole("button", { name: "저장" }));

    expect(onCreate).toHaveBeenCalledWith({
      occurredOn: "2026-10-27",
      amount: 4500,
    });
  });

  it("통화 기본값이 오늘 도시를 따른다 — 오사카면 엔이다", async () => {
    mockFx();
    const user = userEvent.setup();
    const { onCreate } = renderSheet({ cityCurrency: "JPY" });

    expect(
      await screen.findByRole("button", { name: "JPY ¥" }),
    ).toHaveAttribute("aria-pressed", "true");

    await user.type(screen.getByLabelText("금액"), "1200");
    // 환산 줄은 「굳는다」고 말한다 — 다녀온 여행의 총액이 매일 바뀌면 안 된다.
    expect(
      await screen.findByText("11,300원 · 오늘 환율로 굳습니다"),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "저장" }));

    // 환율은 비워 보낸다. 서버가 오늘 고시로 채우고 그 지출에 고정한다(§4.3).
    expect(onCreate).toHaveBeenCalledWith({
      occurredOn: "2026-10-27",
      fx: { currency: "JPY", amount: 1200, rate: null },
    });
  });

  it("분류 칩은 여섯 개 고정이다 — 서버에서 목록을 받지 않는다", async () => {
    mockFx();
    const sheet = within((renderSheet(), await screen.findByRole("dialog")));

    for (const label of ["식비", "교통", "숙소", "관광", "쇼핑", "기타"]) {
      expect(sheet.getByRole("button", { name: label })).toBeVisible();
    }
  });

  it("고른 분류를 다시 누르면 해제된다 — 선택 없이도 저장이 되니까", async () => {
    mockFx();
    const user = userEvent.setup();
    const { onCreate } = renderSheet({ cityCurrency: "KRW" });

    const chip = await screen.findByRole("button", { name: "식비" });
    await user.click(chip);
    expect(chip).toHaveAttribute("aria-pressed", "true");
    await user.click(chip);
    expect(chip).toHaveAttribute("aria-pressed", "false");

    await user.type(screen.getByLabelText("금액"), "1200");
    await user.click(screen.getByRole("button", { name: "저장" }));

    expect(onCreate).toHaveBeenCalledWith({
      occurredOn: "2026-10-27",
      amount: 1200,
    });
  });

  it("결제수단 후보는 넘겨받은 것뿐이다 — 그 여행에서 이미 쓴 값", async () => {
    mockFx();
    renderSheet();

    const sheet = await screen.findByRole("dialog");
    const options = [...sheet.querySelectorAll("datalist option")].map((o) =>
      o.getAttribute("value"),
    );
    expect(options).toEqual(["국민 체크", "현금"]);
  });

  it("결제수단은 직전에 적은 것으로 시작한다 — 여행 중엔 같은 카드를 계속 쓴다", async () => {
    mockFx();
    const user = userEvent.setup();
    const { unmount } = renderSheet({ cityCurrency: "KRW" });

    await user.type(await screen.findByLabelText("결제수단"), "현금");
    await user.type(screen.getByLabelText("금액"), "1200");
    await user.click(screen.getByRole("button", { name: "저장" }));
    unmount();

    renderSheet({ cityCurrency: "KRW" });
    await waitFor(() =>
      expect(screen.getByLabelText("결제수단")).toHaveValue("현금"),
    );
  });

  it("금액이 비었으면 저장할 수 없다", async () => {
    mockFx();
    renderSheet();

    expect(await screen.findByRole("button", { name: "저장" })).toBeDisabled();
  });

  it("환율을 못 받으면 환산을 지어내지 않는다 — 저장은 그대로 된다", async () => {
    mockFx(null);
    const user = userEvent.setup();
    const { onCreate } = renderSheet({ cityCurrency: "JPY" });

    await user.type(await screen.findByLabelText("금액"), "1200");

    expect(
      screen.getByText("금액을 적으면 원화가 여기 나와요"),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "저장" }));

    // 환율 때문에 기록을 막지 않는다(§6).
    expect(onCreate).toHaveBeenCalledWith({
      occurredOn: "2026-10-27",
      fx: { currency: "JPY", amount: 1200, rate: null },
    });
  });

  it("고칠 줄을 주면 그 값으로 열린다 — 시트는 한 벌이다", async () => {
    mockFx();
    renderSheet({ editing: row(), cityCurrency: "KRW" });

    const sheet = within(await screen.findByRole("dialog"));
    expect(sheet.getByText("지출 고치기")).toBeVisible();
    expect(sheet.getByLabelText("금액")).toHaveValue("32000");
    expect(sheet.getByLabelText("무엇에")).toHaveValue("이자카야");
    expect(sheet.getByLabelText("결제수단")).toHaveValue("국민 체크");
    expect(sheet.getByRole("button", { name: "식비" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("외화 건을 고칠 때는 외화 금액이 뜬다 — 원화를 띄우면 두 배가 된다", async () => {
    mockFx();
    renderSheet({
      editing: row({
        amount: 11300,
        fx: { currency: "JPY", amount: 1200, rate: 9.4166 },
      }),
    });

    expect(await screen.findByLabelText("금액")).toHaveValue("1200");
    expect(screen.getByRole("button", { name: "JPY ¥" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("비운 칸은 clear로 간다 — null은 「안 보냈다」와 구분되지 않는다", async () => {
    mockFx();
    const user = userEvent.setup();
    const { onUpdate } = renderSheet({ editing: row(), cityCurrency: "KRW" });

    await user.clear(await screen.findByLabelText("무엇에"));
    await user.clear(screen.getByLabelText("결제수단"));
    await user.click(screen.getByRole("button", { name: "식비" }));
    await user.click(screen.getByRole("button", { name: "저장" }));

    expect(onUpdate).toHaveBeenCalledWith(4301, {
      amount: 32000,
      clearTitle: true,
      clearCategory: true,
      clearPaymentMethod: true,
      clearFx: true,
    });
  });

  it("고칠 때만 지우기가 보인다 — 새로 적는 시트에는 지울 것이 없다", async () => {
    mockFx();
    const user = userEvent.setup();
    const { onDelete, unmount } = renderSheet();

    expect(await screen.findByRole("button", { name: "저장" })).toBeVisible();
    expect(screen.queryByRole("button", { name: "지우기" })).toBeNull();
    unmount();

    const editing = row();
    const second = renderSheet({ editing });
    await user.click(await screen.findByRole("button", { name: "지우기" }));

    expect(second.onDelete).toHaveBeenCalledWith(editing);
    expect(onDelete).not.toHaveBeenCalled();
  });

  /**
   * 통화 기본값은 <b>시트를 연 뒤에 도착한다</b> — 보드 응답에서 오기 때문이다. 그 값이
   * 상태를 다시 세우면 이미 찍어 둔 금액이 지워지는데, 현지에서 망이 느릴수록 잘 맞는
   * 타이밍이라 정확히 이 화면에서 가장 아픈 자리다(브라우저에서 잡았다).
   */
  it("도시 통화가 늦게 와도 이미 찍은 금액을 지우지 않는다", async () => {
    mockFx();
    const user = userEvent.setup();
    // 보드를 아직 못 받은 상태로 연다 — 통화를 모르니 원화로 시작한다.
    const { rerender, onCreate } = renderSheet({ cityCurrency: null });

    await user.type(await screen.findByLabelText("금액"), "1200");

    // 보드가 도착해 「오사카 = 엔」이 들어온다.
    rerender(
      <Providers>
        <ExpenseQuickSheet
          open
          onOpenChange={() => {}}
          tripId={12}
          cityName="오사카"
          cityCurrency="JPY"
          occurredOn="2026-10-27"
          editing={null}
          paymentMethods={[]}
          onCreate={onCreate}
          onUpdate={() => {}}
          onDelete={() => {}}
          pending={false}
        />
      </Providers>,
    );

    // 찍어 둔 금액은 그대로고, 칩만 엔으로 따라 움직인다.
    expect(screen.getByLabelText("금액")).toHaveValue("1200");
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "JPY ¥" })).toHaveAttribute(
        "aria-pressed",
        "true",
      ),
    );
  });

  it("어디에서 언제 쓰는 돈인지 말해 준다", async () => {
    mockFx();
    renderSheet();

    const sheet = within(await screen.findByRole("dialog"));
    expect(
      sheet.getByText("오사카 · 10.27 (화) · 30초 안에 끝나게"),
    ).toBeVisible();
  });
});
