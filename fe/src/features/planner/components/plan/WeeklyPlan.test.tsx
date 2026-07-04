import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

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

  it("블록이 없어도 그리드를 렌더하고 빈 상태 안내 문구는 없다", async () => {
    mockPlan([]);
    renderWithRouter(<WeeklyPlan />);
    expect(
      await screen.findByRole("button", { name: "추가" }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/빈 한 주입니다/)).not.toBeInTheDocument();
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
  });

  it("추가하면 [저장] 버튼 없이 즉시 PUT으로 전량 교체된다(자동 저장)", async () => {
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
    renderWithRouter(<WeeklyPlan />);

    await user.click(await screen.findByRole("button", { name: "추가" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("checkbox", { name: "토" }));
    await user.type(within(dialog).getByLabelText("라벨"), "운동");
    await user.click(within(dialog).getByRole("button", { name: "추가" }));

    // 별도 [저장] 버튼 없이 PUT이 자동 발생
    await waitFor(() => expect(putBody).not.toBeNull());
    expect(putBody!.blocks).toHaveLength(2); // 기존 1 + 신규 1, 전량 교체
    expect(
      screen.queryByRole("button", { name: "저장" }),
    ).not.toBeInTheDocument();
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

  it("선택 모드에서 여러 블록을 골라 한 번에 삭제한다", async () => {
    mockPlan([
      {
        id: 1,
        dayOfWeek: 1,
        startTime: "08:00",
        endTime: "09:00",
        label: "기상",
        color: "violet",
      },
      {
        id: 2,
        dayOfWeek: 2,
        startTime: "10:00",
        endTime: "11:00",
        label: "공부",
        color: "sky",
      },
      {
        id: 3,
        dayOfWeek: 3,
        startTime: "12:00",
        endTime: "13:00",
        label: "점심",
        color: "amber",
      },
    ]);
    let putBody: { blocks: { label: string }[] } | null = null;
    server.use(
      http.put(`${API_BASE}/planner/plan`, async ({ request }) => {
        putBody = (await request.json()) as { blocks: { label: string }[] };
        return HttpResponse.json({
          code: "OK",
          data: {
            blocks: putBody.blocks.map((b, i) => ({ id: i + 10, ...b })),
          },
        });
      }),
    );

    const user = userEvent.setup();
    renderWithRouter(<WeeklyPlan />);

    // 선택 모드 진입
    await user.click(await screen.findByRole("button", { name: "선택" }));

    // 기상·점심 두 블록 체크(공부는 남긴다)
    await user.click(screen.getByRole("button", { name: "기상 08:00~09:00" }));
    await user.click(screen.getByRole("button", { name: "점심 12:00~13:00" }));

    // 체크 상태·개수 반영
    expect(
      screen.getByRole("button", { name: "기상 08:00~09:00" }),
    ).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByText("2개 선택")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "삭제" }));

    // 선택한 두 블록만 제거되고 1회 전량 교체 저장
    await waitFor(() => expect(putBody).not.toBeNull());
    expect(putBody!.blocks).toHaveLength(1);
    expect(putBody!.blocks[0].label).toBe("공부");

    // 남은 블록은 유지, 선택 모드는 해제되어 [선택] 버튼 복귀
    expect(
      screen.getByRole("button", { name: "공부 10:00~11:00" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "기상 08:00~09:00" }),
    ).not.toBeInTheDocument();
    expect(
      await screen.findByRole("button", { name: "선택" }),
    ).toBeInTheDocument();
  });

  it("선택 모드를 취소하면 삭제 없이 편집 모드로 복귀한다", async () => {
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

    await user.click(await screen.findByRole("button", { name: "선택" }));
    await user.click(screen.getByRole("button", { name: "공부 10:00~11:00" }));
    await user.click(screen.getByRole("button", { name: "취소" }));

    // 블록 유지 + 클릭 시 다시 편집 모달이 열린다(선택 모드 해제 확인)
    expect(
      screen.getByRole("button", { name: "공부 10:00~11:00" }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "공부 10:00~11:00" }));
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
  });

  it("블록이 없으면 [선택] 버튼을 노출하지 않는다", async () => {
    mockPlan([]);
    renderWithRouter(<WeeklyPlan />);
    expect(
      await screen.findByRole("button", { name: "추가" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "선택" }),
    ).not.toBeInTheDocument();
  });
});
