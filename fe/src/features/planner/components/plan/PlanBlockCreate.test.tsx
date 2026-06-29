import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { renderWithRouter } from "@/test/render";

import { PlanBlockCreate } from "./PlanBlockCreate";

describe("PlanBlockCreate", () => {
  it("'자정(24:00)' 체크 시 종료 24:00 블록을 만든다", async () => {
    const onCreate = vi.fn();
    const user = userEvent.setup();
    renderWithRouter(
      <PlanBlockCreate open onCreate={onCreate} onClose={() => {}} />,
    );

    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("checkbox", { name: "월" }));
    await user.type(within(dialog).getByLabelText("라벨"), "마감");
    await user.click(
      within(dialog).getByRole("checkbox", { name: "종료를 자정(24:00)으로" }),
    );
    await user.click(within(dialog).getByRole("button", { name: "추가" }));

    expect(onCreate).toHaveBeenCalledTimes(1);
    const blocks = onCreate.mock.calls[0][0];
    expect(blocks).toHaveLength(1);
    expect(blocks[0]).toMatchObject({
      dayOfWeek: 1,
      startTime: "09:00",
      endTime: "24:00",
      label: "마감",
    });
  });

  it("자정 체크를 해제하면 24:00이 아닌 종료로 되돌아간다", async () => {
    const onCreate = vi.fn();
    const user = userEvent.setup();
    renderWithRouter(
      <PlanBlockCreate open onCreate={onCreate} onClose={() => {}} />,
    );

    const dialog = screen.getByRole("dialog");
    const midnight = within(dialog).getByRole("checkbox", {
      name: "종료를 자정(24:00)으로",
    });
    await user.click(midnight); // on → 24:00
    await user.click(midnight); // off → 24:00 아님

    await user.click(within(dialog).getByRole("checkbox", { name: "월" }));
    await user.type(within(dialog).getByLabelText("라벨"), "공부");
    await user.click(within(dialog).getByRole("button", { name: "추가" }));

    expect(onCreate.mock.calls[0][0][0].endTime).not.toBe("24:00");
  });
});
