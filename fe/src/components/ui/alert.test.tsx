import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Alert, AlertDescription, AlertTitle } from "./alert";

describe("Alert", () => {
  it("role=alert로 제목·설명을 렌더한다", () => {
    render(
      <Alert>
        <AlertTitle>안내</AlertTitle>
        <AlertDescription>변경 사항이 저장되었습니다.</AlertDescription>
      </Alert>,
    );
    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByText("안내")).toBeInTheDocument();
    expect(screen.getByText("변경 사항이 저장되었습니다.")).toBeInTheDocument();
  });

  it("상태 variant는 해당 status 토큰을 쓴다", () => {
    render(
      <Alert variant="warning">
        <AlertTitle>주의</AlertTitle>
      </Alert>,
    );
    expect(screen.getByRole("alert")).toHaveClass(
      "border-warning/30",
      "bg-warning/10",
    );
  });
});
