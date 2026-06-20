import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { useToastStore } from "@/shared/lib/toast";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { TodayRoutines } from "./TodayRoutines";

const API_BASE = "https://api.orino.dev/api";

function todayIso(): string {
  const d = new Date();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${m}-${day}`;
}

function feed(done: boolean) {
  return {
    code: "OK",
    data: {
      from: todayIso(),
      to: todayIso(),
      googleConnected: true,
      partial: false,
      errors: [],
      events: [
        {
          id: "r-habit-1_today",
          title: "운동하기",
          allDay: true,
          start: todayIso(),
          end: null,
          location: null,
          recurring: true,
          source: "google",
          routine: { type: "habit", recurringEventId: "r-habit-1", done },
        },
      ],
      tasks: [],
      reviews: [],
    },
  };
}

/** GET은 mutable checked 상태를 반영하고, POST가 그 상태를 갱신한다(낙관적→재검증 일관성). */
function installStatefulHandlers(checkStatus = 200) {
  let checked = false;
  server.use(
    http.get(`${API_BASE}/planner/calendar`, () =>
      HttpResponse.json(feed(checked)),
    ),
    http.post(
      `${API_BASE}/planner/routines/r-habit-1/check`,
      async ({ request }) => {
        if (checkStatus !== 200) {
          return HttpResponse.json({ code: "ERR" }, { status: checkStatus });
        }
        const body = (await request.json()) as { done: boolean };
        checked = body.done;
        return HttpResponse.json({
          code: "OK",
          data: {
            recurringEventId: "r-habit-1",
            date: todayIso(),
            done: checked,
          },
        });
      },
    ),
  );
}

describe("TodayRoutines", () => {
  it("체크 토글을 낙관적으로 반영한다 (0/1 → 1/1)", async () => {
    installStatefulHandlers();
    renderWithRouter(<TodayRoutines />);

    const circle = await screen.findByRole("checkbox", {
      name: "운동하기 완료 토글",
    });
    expect(circle).toHaveAttribute("aria-checked", "false");
    expect(screen.getByText("0/1")).toBeInTheDocument();

    await userEvent.click(circle);

    await waitFor(() =>
      expect(
        screen.getByRole("checkbox", { name: "운동하기 완료 토글" }),
      ).toHaveAttribute("aria-checked", "true"),
    );
    expect(screen.getByText("1/1")).toBeInTheDocument();
  });

  it("실패하면 롤백하고 에러 토스트를 띄운다", async () => {
    installStatefulHandlers(500);
    renderWithRouter(<TodayRoutines />);

    const circle = await screen.findByRole("checkbox", {
      name: "운동하기 완료 토글",
    });
    await userEvent.click(circle);

    await waitFor(() =>
      expect(
        screen.getByRole("checkbox", { name: "운동하기 완료 토글" }),
      ).toHaveAttribute("aria-checked", "false"),
    );
    await waitFor(() =>
      expect(
        useToastStore.getState().toasts.some((t) => t.variant === "error"),
      ).toBe(true),
    );
  });
});
