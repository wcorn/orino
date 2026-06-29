import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "./table";

describe("Table", () => {
  it("헤더·바디를 렌더하고 중립 토큰을 쓴다", () => {
    render(
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>이름</TableHead>
            <TableHead>상태</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow>
            <TableCell>홍길동</TableCell>
            <TableCell>완료</TableCell>
          </TableRow>
        </TableBody>
      </Table>,
    );

    expect(screen.getByRole("table")).toBeInTheDocument();
    expect(screen.getByText("이름")).toHaveClass("text-muted-foreground");
    expect(screen.getByText("홍길동")).toBeInTheDocument();
    // 본문 행은 hover 강조 토큰
    const rows = screen.getAllByRole("row");
    expect(rows[rows.length - 1]).toHaveClass("hover:bg-muted/50");
  });
});
