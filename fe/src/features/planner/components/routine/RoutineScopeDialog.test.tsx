import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { renderWithRouter } from "@/test/render";

import { RoutineScopeDialog } from "./RoutineScopeDialog";

const base = {
  open: true,
  onOpenChange: () => {},
  instanceDate: "2026-06-20",
} as const;

describe("RoutineScopeDialog", () => {
  it("기본 scope가 선택되고 인스턴스 날짜를 라벨에 표시한다", () => {
    renderWithRouter(
      <RoutineScopeDialog
        {...base}
        mode="edit"
        defaultScope="all"
        onConfirm={() => {}}
      />,
    );

    expect(screen.getByRole("radio", { name: "전체 루틴" })).toBeChecked();
    expect(
      screen.getByRole("radio", { name: "이 날짜만 (6/20)" }),
    ).toBeInTheDocument();
  });

  it("각 scope를 선택해 확인하면 그 값으로 onConfirm을 부른다", async () => {
    const onConfirm = vi.fn();
    renderWithRouter(
      <RoutineScopeDialog
        {...base}
        mode="delete"
        defaultScope="instance"
        onConfirm={onConfirm}
      />,
    );

    // 기본: 이 날짜만 → instance
    await userEvent.click(screen.getByRole("button", { name: "확인" }));
    expect(onConfirm).toHaveBeenLastCalledWith("instance");

    await userEvent.click(
      screen.getByRole("radio", { name: "이 날짜 이후 모두" }),
    );
    await userEvent.click(screen.getByRole("button", { name: "확인" }));
    expect(onConfirm).toHaveBeenLastCalledWith("following");

    await userEvent.click(screen.getByRole("radio", { name: "전체 루틴" }));
    await userEvent.click(screen.getByRole("button", { name: "확인" }));
    expect(onConfirm).toHaveBeenLastCalledWith("all");
  });

  it("instanceDate가 없으면 날짜 기반 옵션은 비활성화된다", () => {
    renderWithRouter(
      <RoutineScopeDialog
        open
        onOpenChange={() => {}}
        mode="edit"
        defaultScope="all"
        onConfirm={() => {}}
      />,
    );

    expect(screen.getByRole("radio", { name: "이 날짜만" })).toBeDisabled();
    expect(
      screen.getByRole("radio", { name: "이 날짜 이후 모두" }),
    ).toBeDisabled();
    expect(screen.getByRole("radio", { name: "전체 루틴" })).toBeEnabled();
  });
});
