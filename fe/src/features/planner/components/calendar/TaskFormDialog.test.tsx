import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { renderWithRouter } from "@/test/render";

import { TaskFormDialog } from "./TaskFormDialog";

const baseProps = {
  open: true,
  onOpenChange: () => {},
  defaultDue: "2026-06-12",
} as const;

describe("TaskFormDialog", () => {
  it("제목을 입력하고 저장하면 기본 마감일과 함께 요청을 만든다", async () => {
    const onSubmit = vi.fn();
    renderWithRouter(
      <TaskFormDialog {...baseProps} googleConnected onSubmit={onSubmit} />,
    );

    await userEvent.type(screen.getByLabelText("제목"), "리포트 제출");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(onSubmit).toHaveBeenCalledWith({
      title: "리포트 제출",
      due: "2026-06-12",
    });
  });

  it("미연동이면 연결 CTA를 보여주고 폼은 없다", () => {
    renderWithRouter(
      <TaskFormDialog
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
