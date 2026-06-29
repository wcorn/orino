import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Tooltip, TooltipContent, TooltipTrigger } from "./tooltip";

describe("Tooltip", () => {
  it("열리면 popover 토큰으로 content를 렌더한다", () => {
    render(
      <Tooltip defaultOpen>
        <TooltipTrigger>도움말</TooltipTrigger>
        <TooltipContent>마감 기준 시각입니다</TooltipContent>
      </Tooltip>,
    );

    expect(screen.getByText("도움말")).toBeInTheDocument();
    const content = screen.getByText("마감 기준 시각입니다");
    expect(content).toHaveClass("bg-popover", "text-popover-foreground");
  });
});
