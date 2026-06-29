import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { SectionHeader } from "./section-header";

describe("SectionHeader", () => {
  it("기본은 h2(text-heading font-semibold)", () => {
    render(<SectionHeader>오늘의 루틴</SectionHeader>);
    const heading = screen.getByRole("heading", {
      name: "오늘의 루틴",
      level: 2,
    });
    expect(heading).toHaveClass("text-heading", "font-semibold");
  });

  it("size=sm·level=3은 h3(text-caption font-medium muted)", () => {
    render(
      <SectionHeader size="sm" level={3}>
        일정 (3)
      </SectionHeader>,
    );
    const heading = screen.getByRole("heading", { name: "일정 (3)", level: 3 });
    expect(heading).toHaveClass(
      "text-caption",
      "font-medium",
      "text-muted-foreground",
    );
  });
});
