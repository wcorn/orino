import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { renderWithRouter } from "@/test/render";

import { HomePage } from "./HomePage";

describe("HomePage", () => {
  it("환영 메시지와 v2 안내 텍스트를 표시한다", () => {
    renderWithRouter(<HomePage />);

    expect(screen.getByText("안녕하세요 👋")).toBeInTheDocument();
    expect(screen.getByText(/Study Planner v2 준비 중/)).toBeInTheDocument();
  });
});
