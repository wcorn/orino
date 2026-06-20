import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { renderWithRouter } from "@/test/render";

import { RoutineFormDialog } from "./RoutineFormDialog";

const baseProps = {
  open: true,
  onOpenChange: () => {},
  googleConnected: true,
  defaultDate: "2026-06-20",
} as const;

describe("RoutineFormDialog", () => {
  it("습관: 종일 매일 루틴 생성 payload", async () => {
    const onSubmit = vi.fn();
    renderWithRouter(<RoutineFormDialog {...baseProps} onSubmit={onSubmit} />);

    await userEvent.type(screen.getByLabelText("제목"), "운동하기");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(onSubmit).toHaveBeenCalledWith({
      type: "habit",
      title: "운동하기",
      allDay: true,
      start: "2026-06-20",
      end: "2026-06-20",
      recurrence: { freq: "DAILY", until: null },
      memo: null,
      color: null,
    });
  });

  it("일정: 고정 일정으로 바꾸면 시간 입력이 나오고 datetime payload", async () => {
    const onSubmit = vi.fn();
    renderWithRouter(<RoutineFormDialog {...baseProps} onSubmit={onSubmit} />);

    await userEvent.click(screen.getByRole("button", { name: "고정 일정" }));
    await userEvent.type(screen.getByLabelText("제목"), "스탠드업");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        type: "schedule",
        allDay: false,
        start: "2026-06-20T10:00:00",
        end: "2026-06-20T11:00:00",
      }),
    );
  });

  it("주간: 선택 요일이 byDay로 매핑된다", async () => {
    const onSubmit = vi.fn();
    renderWithRouter(<RoutineFormDialog {...baseProps} onSubmit={onSubmit} />);

    await userEvent.type(screen.getByLabelText("제목"), "운동하기");
    await userEvent.click(screen.getByRole("button", { name: "주간" }));
    await userEvent.click(screen.getByRole("button", { name: "월" }));
    await userEvent.click(screen.getByRole("button", { name: "수" }));
    await userEvent.click(screen.getByRole("button", { name: "금" }));
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        recurrence: { freq: "WEEKLY", byDay: ["MO", "WE", "FR"], until: null },
      }),
    );
  });

  it("매월: 추가한 일자가 byMonthDay로 매핑된다", async () => {
    const onSubmit = vi.fn();
    renderWithRouter(<RoutineFormDialog {...baseProps} onSubmit={onSubmit} />);

    await userEvent.type(screen.getByLabelText("제목"), "정산");
    await userEvent.click(screen.getByRole("button", { name: "매월" }));
    await userEvent.type(screen.getByLabelText("일자 입력"), "15");
    await userEvent.click(screen.getByRole("button", { name: "추가" }));
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        recurrence: { freq: "MONTHLY", byMonthDay: [15], until: null },
      }),
    );
  });

  it("N일 간격: interval로 매핑된다", async () => {
    const onSubmit = vi.fn();
    renderWithRouter(<RoutineFormDialog {...baseProps} onSubmit={onSubmit} />);

    await userEvent.type(screen.getByLabelText("제목"), "물주기");
    await userEvent.click(screen.getByRole("button", { name: "N일 간격" }));
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        recurrence: { freq: "DAILY", interval: 3, until: null },
      }),
    );
  });

  it("주간인데 요일이 없으면 저장이 비활성화된다", async () => {
    renderWithRouter(<RoutineFormDialog {...baseProps} onSubmit={() => {}} />);

    await userEvent.type(screen.getByLabelText("제목"), "운동하기");
    await userEvent.click(screen.getByRole("button", { name: "주간" }));

    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  });

  it("실시간 미리보기 라인이 갱신된다", async () => {
    renderWithRouter(<RoutineFormDialog {...baseProps} onSubmit={() => {}} />);

    expect(screen.getByTestId("routine-preview")).toHaveTextContent(
      "매일 · 2026-06-20부터",
    );

    await userEvent.click(screen.getByRole("button", { name: "주간" }));
    await userEvent.click(screen.getByRole("button", { name: "월" }));
    await userEvent.click(screen.getByRole("button", { name: "금" }));

    expect(screen.getByTestId("routine-preview")).toHaveTextContent(
      "매주 월·금 · 2026-06-20부터",
    );
  });

  it("미연동이면 연결 CTA를 보여주고 폼은 없다", () => {
    renderWithRouter(
      <RoutineFormDialog
        {...baseProps}
        googleConnected={false}
        onSubmit={() => {}}
      />,
    );

    expect(screen.getByText("Google 연결이 필요합니다.")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Google 연결" }),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("제목")).not.toBeInTheDocument();
  });
});
