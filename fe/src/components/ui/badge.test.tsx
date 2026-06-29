import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Badge } from "./badge";

describe("Badge", () => {
  it("기본 variant는 primary 토큰을 쓴다", () => {
    render(<Badge>완료</Badge>);
    expect(screen.getByText("완료")).toHaveClass(
      "bg-primary",
      "text-primary-foreground",
    );
  });

  it("상태 variant는 해당 시맨틱 토큰을 쓴다", () => {
    render(<Badge variant="success">성공</Badge>);
    expect(screen.getByText("성공")).toHaveClass(
      "bg-success",
      "text-success-foreground",
    );
  });

  it("outline variant는 테두리만", () => {
    render(<Badge variant="outline">기본</Badge>);
    const badge = screen.getByText("기본");
    expect(badge).toHaveClass("border-border", "text-foreground");
    expect(badge).not.toHaveClass("bg-primary");
  });
});
