import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { ErrorBoundary } from "./ErrorBoundary";

function Boom(): never {
  throw new Error("boom");
}

describe("ErrorBoundary", () => {
  it("자식 컴포넌트에서 에러가 발생하면 기본 폴백을 렌더링한다", () => {
    const error = vi.spyOn(console, "error").mockImplementation(() => {});

    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    );

    expect(screen.getByText("문제가 발생했습니다.")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "새로고침" }),
    ).toBeInTheDocument();

    error.mockRestore();
  });

  it("커스텀 fallback이 제공되면 그것을 렌더링한다", () => {
    const error = vi.spyOn(console, "error").mockImplementation(() => {});

    render(
      <ErrorBoundary fallback={<div>custom fallback</div>}>
        <Boom />
      </ErrorBoundary>,
    );

    expect(screen.getByText("custom fallback")).toBeInTheDocument();

    error.mockRestore();
  });

  it("에러가 없으면 children을 그대로 렌더링한다", () => {
    render(
      <ErrorBoundary>
        <div>정상</div>
      </ErrorBoundary>,
    );

    expect(screen.getByText("정상")).toBeInTheDocument();
  });
});
