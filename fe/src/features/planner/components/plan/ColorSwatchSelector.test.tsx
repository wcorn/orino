import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ColorSwatchSelector } from "./ColorSwatchSelector";

describe("ColorSwatchSelector", () => {
  it("선택된 색만 aria-pressed=true로 표시한다", () => {
    render(<ColorSwatchSelector value="sky" onChange={() => {}} />);
    expect(screen.getByRole("button", { name: "하늘" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.getByRole("button", { name: "보라" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
  });

  it("클릭하면 색 키로 onChange를 호출한다", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<ColorSwatchSelector value="violet" onChange={onChange} />);
    await user.click(screen.getByRole("button", { name: "초록" }));
    expect(onChange).toHaveBeenCalledWith("emerald");
  });
});
