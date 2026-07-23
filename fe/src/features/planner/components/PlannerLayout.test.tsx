import { screen } from "@testing-library/react";
import { Route, Routes } from "react-router-dom";
import { describe, expect, it } from "vitest";

import { renderWithRouter } from "@/test/render";

import { PlannerLayout } from "./PlannerLayout";

function renderLayout(path: string) {
  return renderWithRouter(
    <Routes>
      <Route element={<PlannerLayout />}>
        <Route path="/planner/calendar" element={<div>캘린더 본문</div>} />
        <Route path="/planner/plan" element={<div>주간 계획표 본문</div>} />
        <Route path="/planner/routines" element={<div>루틴 본문</div>} />
      </Route>
    </Routes>,
    { initialEntries: [path] },
  );
}

describe("PlannerLayout", () => {
  it("하위 탭 3개(캘린더·주간 계획표·루틴)를 노출한다", () => {
    renderLayout("/planner/calendar");
    expect(screen.getByRole("link", { name: "캘린더" })).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "주간 계획표" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "루틴" })).toBeInTheDocument();
  });

  it("현재 경로의 하위 탭이 활성(aria-current=page)이고 해당 본문을 렌더한다", () => {
    renderLayout("/planner/plan");
    expect(screen.getByText("주간 계획표 본문")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "주간 계획표" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("link", { name: "캘린더" })).not.toHaveAttribute(
      "aria-current",
    );
  });

  it("딥링크(/planner/routines)로 진입하면 루틴 본문과 활성 탭이 보인다", () => {
    renderLayout("/planner/routines");
    expect(screen.getByText("루틴 본문")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "루틴" })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });
});
