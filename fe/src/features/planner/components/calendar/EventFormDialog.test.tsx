import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { renderWithRouter } from "@/test/render";

import type { PlannerEvent } from "../../api/feed";
import { EventFormDialog } from "./EventFormDialog";

const baseProps = {
  open: true,
  onOpenChange: () => {},
  googleConnected: true,
  defaultDate: "2026-06-10",
} as const;

describe("EventFormDialog", () => {
  it("생성: 제목 입력 후 저장하면 기본 시간으로 요청을 만든다", async () => {
    const onSubmit = vi.fn();
    renderWithRouter(
      <EventFormDialog {...baseProps} mode="create" onSubmit={onSubmit} />,
    );

    await userEvent.type(screen.getByLabelText("제목"), "회의");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(onSubmit).toHaveBeenCalledWith({
      title: "회의",
      allDay: false,
      start: "2026-06-10T09:00:00",
      end: "2026-06-10T10:00:00",
      location: null,
      description: null,
    });
  });

  it("종일을 켜면 날짜만으로 요청을 만든다", async () => {
    const onSubmit = vi.fn();
    renderWithRouter(
      <EventFormDialog {...baseProps} mode="create" onSubmit={onSubmit} />,
    );

    await userEvent.click(screen.getByLabelText("종일"));
    await userEvent.type(screen.getByLabelText("제목"), "여행");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        allDay: true,
        start: "2026-06-10",
        end: "2026-06-10",
      }),
    );
  });

  it("편집: 기존 값이 채워지고 삭제 버튼이 있다", () => {
    const event: PlannerEvent = {
      id: "e1",
      title: "치과 예약",
      allDay: false,
      start: "2026-06-10T14:00:00",
      end: "2026-06-10T15:00:00",
      location: "강남",
      description: "정기 검진, 보험증 챙기기",
      recurring: false,
      source: "google",
    };
    renderWithRouter(
      <EventFormDialog
        {...baseProps}
        mode="edit"
        event={event}
        onSubmit={() => {}}
        onDelete={() => {}}
      />,
    );

    expect(screen.getByLabelText("제목")).toHaveValue("치과 예약");
    expect(screen.getByLabelText("시작 시간")).toHaveValue("14:00");
    // 저장돼 있던 메모(description)가 편집창에 그대로 채워져야 한다(회귀 방지: #940).
    expect(
      screen.getByDisplayValue("정기 검진, 보험증 챙기기"),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "삭제" })).toBeInTheDocument();
  });

  it("편집: 기존 메모를 지우지 않고 저장하면 description이 유지된다", async () => {
    const event: PlannerEvent = {
      id: "e1",
      title: "치과 예약",
      allDay: false,
      start: "2026-06-10T14:00:00",
      end: "2026-06-10T15:00:00",
      location: "강남",
      description: "정기 검진",
      recurring: false,
      source: "google",
    };
    const onSubmit = vi.fn();
    renderWithRouter(
      <EventFormDialog
        {...baseProps}
        mode="edit"
        event={event}
        onSubmit={onSubmit}
        onDelete={() => {}}
      />,
    );

    await userEvent.click(screen.getByRole("button", { name: "저장" }));
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ description: "정기 검진" }),
    );
  });

  it("미연동이면 연결 CTA를 보여주고 폼은 없다", () => {
    renderWithRouter(
      <EventFormDialog
        {...baseProps}
        googleConnected={false}
        mode="create"
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
