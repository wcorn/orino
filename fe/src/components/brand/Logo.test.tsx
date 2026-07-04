import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { BrandMark, Logo } from "./Logo";

describe("BrandMark", () => {
  it("aria-label=orino인 svg를 렌더한다", () => {
    render(<BrandMark />);
    expect(screen.getByRole("img", { name: "orino" })).toBeInTheDocument();
  });
});

describe("Logo", () => {
  it("기본은 심볼 + 워드마크(orino)", () => {
    render(<Logo />);
    expect(screen.getByRole("img", { name: "orino" })).toBeInTheDocument();
    expect(screen.getByText("orino")).toBeInTheDocument();
  });

  it("showWordmark=false면 워드마크를 렌더하지 않는다", () => {
    render(<Logo showWordmark={false} />);
    expect(screen.queryByText("orino")).not.toBeInTheDocument();
    expect(screen.getByRole("img", { name: "orino" })).toBeInTheDocument();
  });

  it("tone=inverse면 워드마크가 흰색", () => {
    render(<Logo tone="inverse" />);
    expect(screen.getByText("orino")).toHaveClass("text-white");
  });
});
