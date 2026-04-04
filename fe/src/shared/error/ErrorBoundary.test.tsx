import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ErrorBoundary } from "./ErrorBoundary";

function ThrowingComponent({ shouldThrow }: { shouldThrow: boolean }) {
  if (shouldThrow) {
    throw new Error("test error");
  }
  return <div>정상 컨텐츠</div>;
}

describe("ErrorBoundary", () => {
  beforeEach(() => {
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  it("에러가 없으면 children을 렌더링한다", () => {
    render(
      <ErrorBoundary>
        <ThrowingComponent shouldThrow={false} />
      </ErrorBoundary>,
    );

    expect(screen.getByText("정상 컨텐츠")).toBeInTheDocument();
  });

  it("에러 발생 시 기본 폴백 UI를 렌더링한다", () => {
    render(
      <ErrorBoundary>
        <ThrowingComponent shouldThrow={true} />
      </ErrorBoundary>,
    );

    expect(screen.getByText("문제가 발생했습니다.")).toBeInTheDocument();
    expect(screen.getByText("새로고침")).toBeInTheDocument();
  });

  it("커스텀 fallback을 전달하면 해당 UI를 렌더링한다", () => {
    render(
      <ErrorBoundary fallback={<div>커스텀 에러 UI</div>}>
        <ThrowingComponent shouldThrow={true} />
      </ErrorBoundary>,
    );

    expect(screen.getByText("커스텀 에러 UI")).toBeInTheDocument();
  });

  it("새로고침 버튼 클릭 시 window.location.reload를 호출한다", async () => {
    const reloadMock = vi.fn();
    Object.defineProperty(window, "location", {
      value: { ...window.location, reload: reloadMock },
      writable: true,
    });

    const user = userEvent.setup();

    render(
      <ErrorBoundary>
        <ThrowingComponent shouldThrow={true} />
      </ErrorBoundary>,
    );

    await user.click(screen.getByText("새로고침"));
    expect(reloadMock).toHaveBeenCalled();
  });
});
