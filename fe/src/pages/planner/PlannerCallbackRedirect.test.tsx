import { screen } from "@testing-library/react";
import { Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import { Toaster } from "@/components/Toaster";
import { useToastStore } from "@/shared/lib/toast";
import { renderWithRouter } from "@/test/render";

import { PlannerCallbackRedirect } from "./PlannerCallbackRedirect";

function renderAt(entry: string) {
  return renderWithRouter(
    <>
      <Toaster />
      <Routes>
        <Route path="/planner" element={<PlannerCallbackRedirect />} />
        <Route path="/planner/calendar" element={<div>복습 캘린더</div>} />
      </Routes>
    </>,
    { initialEntries: [entry] },
  );
}

describe("PlannerCallbackRedirect", () => {
  beforeEach(() => {
    useToastStore.setState({ toasts: [] });
  });

  it("google=connected면 성공 토스트 후 캘린더로 이동한다", async () => {
    renderAt("/planner?google=connected");

    expect(await screen.findByText("복습 캘린더")).toBeInTheDocument();
    expect(
      screen.getByText("Google 캘린더가 연결되었습니다."),
    ).toBeInTheDocument();
  });

  it("google=error면 에러 토스트 후 캘린더로 이동한다", async () => {
    renderAt("/planner?google=error");

    expect(await screen.findByText("복습 캘린더")).toBeInTheDocument();
    expect(
      screen.getByText("Google 연결에 실패했습니다. 다시 시도해 주세요."),
    ).toBeInTheDocument();
  });

  it("파라미터가 없으면 토스트 없이 캘린더로 이동한다", async () => {
    renderAt("/planner");

    expect(await screen.findByText("복습 캘린더")).toBeInTheDocument();
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });
});
