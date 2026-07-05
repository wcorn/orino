import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { MonthlyGoalInline } from "./MonthlyGoalInline";

const API_BASE = "https://api.orino.dev/api";

interface StoredGoal {
  year: number;
  month: number;
  content: string;
  updatedAt: string;
}

/** GET/PUT/DELETE를 하나의 가변 저장소로 묶어 실제 upsert/삭제 흐름을 흉내낸다. */
function mockGoalStore(initial: StoredGoal | null) {
  let stored = initial;
  const puts: string[] = [];
  let deleted = false;
  server.use(
    http.get(`${API_BASE}/planner/monthly-goals/:year/:month`, () =>
      HttpResponse.json({ code: "OK", data: stored }),
    ),
    http.put(
      `${API_BASE}/planner/monthly-goals/:year/:month`,
      async ({ request, params }) => {
        const body = (await request.json()) as { content: string };
        puts.push(body.content);
        stored = {
          year: Number(params.year),
          month: Number(params.month),
          content: body.content,
          updatedAt: "2026-07-05T10:00:00",
        };
        return HttpResponse.json({ code: "OK", data: stored });
      },
    ),
    http.delete(`${API_BASE}/planner/monthly-goals/:year/:month`, () => {
      deleted = true;
      stored = null;
      return HttpResponse.json({ code: "OK", data: null });
    }),
  );
  return {
    puts,
    wasDeleted: () => deleted,
  };
}

describe("MonthlyGoalInline", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
  });

  it("목표가 없으면 플레이스홀더를 보여준다", async () => {
    mockGoalStore(null);
    renderWithRouter(<MonthlyGoalInline year={2026} month={7} />);
    expect(
      await screen.findByRole("button", { name: "이번 달 목표 추가" }),
    ).toBeInTheDocument();
  });

  it("플레이스홀더에서 목표를 입력·저장하면 PUT 후 목표가 표시된다", async () => {
    const store = mockGoalStore(null);
    const user = userEvent.setup();
    renderWithRouter(<MonthlyGoalInline year={2026} month={7} />);

    await user.click(
      await screen.findByRole("button", { name: "이번 달 목표 추가" }),
    );
    await user.type(
      await screen.findByLabelText("이번 달 목표 내용"),
      "책 2권 읽기",
    );
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(store.puts).toContain("책 2권 읽기"));
    expect(
      await screen.findByRole("button", { name: /이번 달 목표: 책 2권 읽기/ }),
    ).toBeInTheDocument();
  });

  it("기존 목표를 [삭제]하면 DELETE 후 플레이스홀더로 돌아간다", async () => {
    const store = mockGoalStore({
      year: 2026,
      month: 7,
      content: "운동 꾸준히",
      updatedAt: "2026-07-01T00:00:00",
    });
    const user = userEvent.setup();
    renderWithRouter(<MonthlyGoalInline year={2026} month={7} />);

    await user.click(
      await screen.findByRole("button", { name: /이번 달 목표: 운동 꾸준히/ }),
    );
    await user.click(await screen.findByRole("button", { name: "삭제" }));

    await waitFor(() => expect(store.wasDeleted()).toBe(true));
    expect(
      await screen.findByRole("button", { name: "이번 달 목표 추가" }),
    ).toBeInTheDocument();
  });

  it("내용을 비우고 저장하면 DELETE를 호출한다", async () => {
    const store = mockGoalStore({
      year: 2026,
      month: 7,
      content: "지울 목표",
      updatedAt: "2026-07-01T00:00:00",
    });
    const user = userEvent.setup();
    renderWithRouter(<MonthlyGoalInline year={2026} month={7} />);

    await user.click(
      await screen.findByRole("button", { name: /이번 달 목표: 지울 목표/ }),
    );
    await user.clear(await screen.findByLabelText("이번 달 목표 내용"));
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(store.wasDeleted()).toBe(true));
    expect(store.puts).toHaveLength(0);
  });
});
