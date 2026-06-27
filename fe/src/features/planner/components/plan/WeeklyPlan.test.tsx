import { fireEvent, screen, waitFor, within } from "@testing-library/react";
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

/** 모바일(좁은 폭) matchMedia로 위장. 기본(데스크탑)은 jsdom에 matchMedia가 없어 자동. */
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

  it("시간 칸을 눌러 블록을 만들고 편집 후 그리드에 반영한다", async () => {
    mockPlan([]);
    const user = userEvent.setup();
    renderWithRouter(<WeeklyPlan />);

    // 수요일(dayOfWeek=3) 9시 칸 → 09:00~10:00 블록 생성 + 편집 모달
    await user.click(await screen.findByLabelText("수요일 9시 추가"));

    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("라벨"), "회의");
    await user.click(within(dialog).getByRole("button", { name: "적용" }));

    expect(
      await screen.findByRole("button", { name: "회의 09:00~10:00" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("저장되지 않은 변경이 있습니다"),
    ).toBeInTheDocument();
  });

  it("세로 드래그로 구간 블록을 만든다(스냅 단위)", async () => {
    mockPlan([]);
    const user = userEvent.setup();
    // 컬럼 높이를 1440px로 고정 → 1px = 1분
    const originalRect = Element.prototype.getBoundingClientRect;
    Element.prototype.getBoundingClientRect = () =>
      ({
        top: 0,
        left: 0,
        right: 100,
        bottom: 1440,
        width: 100,
        height: 1440,
        x: 0,
        y: 0,
        toJSON: () => {},
      }) as DOMRect;
    try {
      renderWithRouter(<WeeklyPlan />);

      // 수요일 컬럼 = "수요일 0시 추가" 칸의 부모
      const column = (await screen.findByLabelText("수요일 0시 추가"))
        .parentElement as HTMLElement;
      // 09:00(540) → 11:30(690) 드래그
      fireEvent.pointerDown(column, { clientY: 540, pointerId: 1 });
      fireEvent.pointerMove(column, { clientY: 690, pointerId: 1 });
      fireEvent.pointerUp(column, { clientY: 690, pointerId: 1 });

      const dialog = await screen.findByRole("dialog");
      await user.type(within(dialog).getByLabelText("라벨"), "공부");
      await user.click(within(dialog).getByRole("button", { name: "적용" }));

      expect(
        await screen.findByRole("button", { name: "공부 09:00~11:30" }),
      ).toBeInTheDocument();
    } finally {
      Element.prototype.getBoundingClientRect = originalRect;
    }
  });

  it("편집을 취소하면 블록이 추가되지 않고 누적되지 않는다", async () => {
    mockPlan([]);
    const user = userEvent.setup();
    renderWithRouter(<WeeklyPlan />);

    // 두 번 생성 시도 후 모두 취소 → 블록 누적 0, 미저장 변경 없음
    for (let i = 0; i < 2; i++) {
      await user.click(await screen.findByLabelText("수요일 9시 추가"));
      const dialog = await screen.findByRole("dialog");
      await user.click(within(dialog).getByRole("button", { name: "취소" }));
      await waitFor(() =>
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
      );
    }

    expect(
      screen.queryByRole("button", { name: /\(라벨 없음\)/ }),
    ).not.toBeInTheDocument();
    expect(screen.getByText(/빈 한 주입니다/)).toBeInTheDocument();
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

    // 블록 추가로 dirty 만들기
    await user.click(await screen.findByLabelText("토요일 10시 추가"));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("라벨"), "운동");
    await user.click(within(dialog).getByRole("button", { name: "적용" }));

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
    // 종료를 시작보다 이르게
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
    // 화요일 탭 → 공부 블록 보임
    await user.click(within(tablist).getByRole("tab", { name: "화" }));
    expect(
      await screen.findByRole("button", { name: "공부 10:00~11:00" }),
    ).toBeInTheDocument();

    // 수요일 탭으로 전환하면 화요일 블록은 사라진다(1일 뷰)
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
