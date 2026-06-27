import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

import { Toaster } from "@/components/Toaster";
import { useToastStore } from "@/shared/lib/toast";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { WeeklyPlan } from "./WeeklyPlan";

const API_BASE = "https://api.orino.dev/api";

function mockPlan(blocks: unknown[]) {
  server.use(
    http.get(`${API_BASE}/planner/plan`, () =>
      HttpResponse.json({ code: "OK", data: { blocks } }),
    ),
  );
}

function setNarrowViewport() {
  window.matchMedia = ((query: string) => ({
    matches: query.includes("767"),
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia;
}

describe("WeeklyPlan", () => {
  const originalMatchMedia = window.matchMedia;

  beforeEach(() => {
    useToastStore.setState({ toasts: [] });
  });

  afterEach(() => {
    window.matchMedia = originalMatchMedia;
  });

  it("기존 블록을 로드해 그리드에 렌더한다", async () => {
    mockPlan([
      {
        id: 1,
        dayOfWeek: 1,
        startTime: "08:00",
        endTime: "09:00",
        label: "기상",
        color: "violet",
      },
    ]);

    renderWithRouter(<WeeklyPlan />);

    expect(
      await screen.findByRole("button", { name: "기상 08:00~09:00" }),
    ).toBeInTheDocument();
  });

  it("블록이 없으면 빈 상태 안내를 보여준다", async () => {
    mockPlan([]);
    renderWithRouter(<WeeklyPlan />);
    expect(await screen.findByText(/빈 한 주입니다/)).toBeInTheDocument();
  });

  it("[+ 추가]로 여러 요일에 블록을 한 번에 만든다", async () => {
    mockPlan([]);
    const user = userEvent.setup();
    renderWithRouter(<WeeklyPlan />);

    await user.click(await screen.findByRole("button", { name: "추가" }));
    const dialog = await screen.findByRole("dialog");

    // 요일 미선택 → 추가 비활성
    expect(within(dialog).getByRole("button", { name: "추가" })).toBeDisabled();

    await user.click(within(dialog).getByRole("checkbox", { name: "월" }));
    await user.click(within(dialog).getByRole("checkbox", { name: "수" }));
    await user.type(within(dialog).getByLabelText("라벨"), "공부");
    await user.click(within(dialog).getByRole("button", { name: "추가" }));

    // 월·수 두 블록 생성
    expect(
      await screen.findAllByRole("button", { name: "공부 09:00~10:00" }),
    ).toHaveLength(2);
    expect(
      screen.getByText("저장되지 않은 변경이 있습니다"),
    ).toBeInTheDocument();
  });

  it("추가를 취소하면 블록이 생성되지 않는다", async () => {
    mockPlan([]);
    const user = userEvent.setup();
    renderWithRouter(<WeeklyPlan />);

    await user.click(await screen.findByRole("button", { name: "추가" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("checkbox", { name: "월" }));
    await user.click(within(dialog).getByRole("button", { name: "취소" }));

    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
    expect(
      screen.queryByRole("button", { name: /09:00~10:00/ }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText("저장되지 않은 변경이 있습니다"),
    ).not.toBeInTheDocument();
  });

  it("저장은 PUT으로 전량 교체하고 성공 토스트를 띄운다", async () => {
    mockPlan([
      {
        id: 1,
        dayOfWeek: 1,
        startTime: "08:00",
        endTime: "09:00",
        label: "기상",
        color: "violet",
      },
    ]);
    let putBody: { blocks: unknown[] } | null = null;
    server.use(
      http.put(`${API_BASE}/planner/plan`, async ({ request }) => {
        putBody = (await request.json()) as { blocks: unknown[] };
        return HttpResponse.json({
          code: "OK",
          data: {
            blocks: putBody.blocks.map((b, i) => ({
              id: i + 10,
              ...(b as object),
            })),
          },
        });
      }),
    );

    const user = userEvent.setup();
    renderWithRouter(
      <>
        <WeeklyPlan />
        <Toaster />
      </>,
    );

    // 토요일에 블록 추가로 dirty
    await user.click(await screen.findByRole("button", { name: "추가" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("checkbox", { name: "토" }));
    await user.type(within(dialog).getByLabelText("라벨"), "운동");
    await user.click(within(dialog).getByRole("button", { name: "추가" }));

    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() =>
      expect(
        screen.getByText("주간 계획표를 저장했습니다."),
      ).toBeInTheDocument(),
    );
    expect(putBody).not.toBeNull();
    expect(putBody!.blocks).toHaveLength(2); // 기존 1 + 신규 1, 전량 교체
  });

  it("블록 편집에서 종료가 시작보다 빠르면 적용을 막는다", async () => {
    mockPlan([
      {
        id: 1,
        dayOfWeek: 2,
        startTime: "10:00",
        endTime: "11:00",
        label: "공부",
        color: "sky",
      },
    ]);
    const user = userEvent.setup();
    renderWithRouter(<WeeklyPlan />);

    await user.click(
      await screen.findByRole("button", { name: "공부 10:00~11:00" }),
    );
    const dialog = await screen.findByRole("dialog");
    const end = within(dialog).getByLabelText("종료");
    await user.clear(end);
    await user.type(end, "09:00");

    expect(
      within(dialog).getByText("종료가 시작보다 늦어야 합니다."),
    ).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "적용" })).toBeDisabled();
  });

  it("모바일에서는 요일 탭으로 1일 뷰를 전환한다", async () => {
    setNarrowViewport();
    mockPlan([
      {
        id: 1,
        dayOfWeek: 2,
        startTime: "10:00",
        endTime: "11:00",
        label: "공부",
        color: "sky",
      },
    ]);
    const user = userEvent.setup();
    renderWithRouter(<WeeklyPlan />);

    const tablist = await screen.findByRole("tablist", { name: "요일 선택" });
    await user.click(within(tablist).getByRole("tab", { name: "화" }));
    expect(
      await screen.findByRole("button", { name: "공부 10:00~11:00" }),
    ).toBeInTheDocument();

    await user.click(within(tablist).getByRole("tab", { name: "수" }));
    expect(
      screen.queryByRole("button", { name: "공부 10:00~11:00" }),
    ).not.toBeInTheDocument();
  });

  it("블록을 삭제한다", async () => {
    mockPlan([
      {
        id: 1,
        dayOfWeek: 2,
        startTime: "10:00",
        endTime: "11:00",
        label: "공부",
        color: "sky",
      },
    ]);
    const user = userEvent.setup();
    renderWithRouter(<WeeklyPlan />);

    await user.click(
      await screen.findByRole("button", { name: "공부 10:00~11:00" }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "삭제" }));

    await waitFor(() =>
      expect(
        screen.queryByRole("button", { name: "공부 10:00~11:00" }),
      ).not.toBeInTheDocument(),
    );
  });
});
