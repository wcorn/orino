import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PageHeader } from "./PageHeader";

describe("PageHeader", () => {
  it("제목을 text-xl h1로 렌더한다", () => {
    render(<PageHeader title="학습 자료" />);
    const heading = screen.getByRole("heading", { name: "학습 자료" });
    expect(heading.tagName).toBe("H1");
    expect(heading).toHaveClass("text-xl", "font-semibold");
  });

  it("설명과 우측 액션 슬롯을 함께 렌더한다", () => {
    render(
      <PageHeader
        title="주간 계획표"
        description="변경 시 자동 저장됩니다"
        actions={<button>추가</button>}
      />,
    );
    expect(screen.getByText("변경 시 자동 저장됩니다")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "추가" })).toBeInTheDocument();
  });

  it("설명/액션이 없으면 해당 요소를 렌더하지 않는다", () => {
    render(<PageHeader title="연동 설정" />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});
