import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Tabs, TabsContent, TabsList, TabsTrigger } from "./tabs";

describe("Tabs", () => {
  it("tablist/tab/tabpanel을 렌더하고 활성 탭에 data-selected가 붙는다", () => {
    render(
      <Tabs defaultValue="a">
        <TabsList>
          <TabsTrigger value="a">탭 A</TabsTrigger>
          <TabsTrigger value="b">탭 B</TabsTrigger>
        </TabsList>
        <TabsContent value="a">내용 A</TabsContent>
        <TabsContent value="b">내용 B</TabsContent>
      </Tabs>,
    );

    expect(screen.getByRole("tablist")).toBeInTheDocument();
    expect(screen.getAllByRole("tab")).toHaveLength(2);
    // 기본 활성 탭(a)에 base-ui의 활성 상태 속성(data-active) + aria-selected
    expect(screen.getByText("탭 A")).toHaveAttribute("data-active");
    expect(screen.getByText("탭 A")).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("내용 A")).toBeInTheDocument();
  });
});
