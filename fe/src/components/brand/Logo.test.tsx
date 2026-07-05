import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { BrandMark, Logo, Wordmark } from "./Logo";

describe("BrandMark", () => {
  it("aria-label=orino인 svg를 렌더한다", () => {
    render(<BrandMark />);
    expect(screen.getByRole("img", { name: "orino" })).toBeInTheDocument();
  });

  it("animated여도 정상 렌더한다", () => {
    render(<BrandMark animated />);
    expect(screen.getByRole("img", { name: "orino" })).toBeInTheDocument();
  });
});

describe("Wordmark", () => {
  it("aria-label=orino + dotless ı를 포함한 소문자 표기", () => {
    render(<Wordmark />);
    const wm = screen.getByRole("img", { name: "orino" });
    expect(wm).toBeInTheDocument();
    // 가시 텍스트는 점 없는 ı(U+0131) — "orıno"
    expect(wm).toHaveTextContent("orıno");
  });

  it("tone=inverse면 흰색", () => {
    render(<Wordmark tone="inverse" />);
    expect(screen.getByRole("img", { name: "orino" })).toHaveClass(
      "text-white",
    );
  });
});

describe("Logo", () => {
  it("기본은 심볼 + 워드마크(둘 다 orino)", () => {
    render(<Logo />);
    expect(screen.getAllByRole("img", { name: "orino" })).toHaveLength(2);
  });

  it("showWordmark=false면 심볼만", () => {
    render(<Logo showWordmark={false} />);
    expect(screen.getAllByRole("img", { name: "orino" })).toHaveLength(1);
  });
});
