import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { RoutineFormDialog } from "@/features/planner/components/routine/RoutineFormDialog";
import { useToastStore } from "@/shared/lib/toast";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { useCreateRoutine } from "./useRoutineMutations";

const ROUTINES_URL = "https://api.orino.dev/api/planner/routines";

function Harness() {
  const mutation = useCreateRoutine();
  return (
    <RoutineFormDialog
      open
      onOpenChange={() => {}}
      googleConnected
      defaultDate="2026-06-20"
      pending={mutation.isPending}
      onSubmit={(values) => mutation.mutate(values)}
    />
  );
}

describe("useCreateRoutine (RTL + MSW)", () => {
  it("주간 습관 생성 시 POST 본문을 보내고 성공 토스트를 띄운다", async () => {
    let captured: unknown;
    server.use(
      http.post(ROUTINES_URL, async ({ request }) => {
        captured = await request.json();
        return HttpResponse.json({
          code: "OK",
          data: {
            recurringEventId: "r-1",
            type: "habit",
            title: "운동하기",
            allDay: true,
            start: "2026-06-20",
            recurrence: { freq: "WEEKLY", byDay: ["MO", "WE", "FR"] },
            recurrenceText: "매주 월·수·금",
          },
        });
      }),
    );

    renderWithRouter(<Harness />);

    await userEvent.type(screen.getByLabelText("제목"), "운동하기");
    await userEvent.click(screen.getByRole("button", { name: "주간" }));
    await userEvent.click(screen.getByRole("button", { name: "월" }));
    await userEvent.click(screen.getByRole("button", { name: "수" }));
    await userEvent.click(screen.getByRole("button", { name: "금" }));
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(captured).toBeDefined());
    expect(captured).toEqual({
      type: "habit",
      title: "운동하기",
      allDay: true,
      start: "2026-06-20",
      end: "2026-06-20",
      recurrence: { freq: "WEEKLY", byDay: ["MO", "WE", "FR"], until: null },
      memo: null,
      color: null,
    });

    await waitFor(() =>
      expect(
        useToastStore.getState().toasts.some((t) => t.variant === "success"),
      ).toBe(true),
    );
  });
});
