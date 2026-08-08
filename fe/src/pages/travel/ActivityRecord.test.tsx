import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

const API_BASE = "https://api.orino.dev/api";

/** 디바운스(1초)보다 넉넉히 기다린다. */
const SAVE_TIMEOUT = 4000;

function trip(status = "ONGOING") {
  return {
    id: 3,
    title: "도쿄 3박 4일",
    destinationName: "도쿄",
    destinationPlaceId: null,
    startDate: "2026-08-06",
    endDate: "2026-08-10",
    timezone: "Asia/Tokyo",
    currency: "JPY",
    lat: null,
    lng: null,
    defaultNotifyMinutes: 15,
    morningSummaryEnabled: false,
    status,
    dDay: 0,
    totalDays: 5,
    activityCount: 1,
  };
}

function activity(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    tripId: 3,
    title: "센소지",
    activityDate: "2026-08-08",
    startTime: "09:00",
    place: null,
    memo: null,
    url: null,
    notifyEnabled: false,
    notifyMinutes: null,
    departureNotifyEnabled: false,
    sortOrder: 0,
    log: null,
    hasLog: false,
    ...overrides,
  };
}

function mock({
  status = "ONGOING",
  ...activityOverrides
}: Record<string, unknown> = {}) {
  server.use(
    http.get(`${API_BASE}/travel/activities/:id`, () =>
      HttpResponse.json({ code: "OK", data: activity(activityOverrides) }),
    ),
    http.get(`${API_BASE}/travel/trips/:tripId`, () =>
      HttpResponse.json({ code: "OK", data: trip(status as string) }),
    ),
  );
}

/** 기록 저장 요청 본문을 잡아 둔다. */
function captureLogSave() {
  const seen: Record<string, unknown>[] = [];
  server.use(
    http.put(`${API_BASE}/travel/activities/:id/log`, async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>;
      seen.push(body);
      const empty = body.rating === null && !body.memo;
      return HttpResponse.json({
        code: "OK",
        data: empty ? null : { ...body, updatedAt: "2026-08-08T10:00:00Z" },
      });
    }),
  );
  return seen;
}

function renderDetail() {
  return renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: ["/travel/activities/1"] },
  );
}

describe("일정 기록 영역", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
    mock();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("노출 조건", () => {
    it("여행이 시작된 뒤에만 보인다 — 겪지 않은 일에 평점을 매길 수 없다", async () => {
      mock({ status: "UPCOMING" });
      renderDetail();

      await screen.findByRole("heading", { name: "센소지" });

      expect(screen.queryByRole("group", { name: "평점" })).toBeNull();
    });

    it("여행이 끝난 뒤에도 보인다 — 기록은 돌아와서 적는 게 보통이다", async () => {
      mock({ status: "COMPLETED" });
      renderDetail();

      expect(
        await screen.findByRole("group", { name: "평점" }),
      ).toBeInTheDocument();
    });
  });

  describe("평점", () => {
    it("별을 누르면 그 점수로 저장한다", async () => {
      const saved = captureLogSave();
      const user = userEvent.setup();
      renderDetail();

      await user.click(await screen.findByRole("button", { name: "4점" }));

      await waitFor(
        () => expect(saved).toContainEqual({ rating: 4, memo: null }),
        { timeout: SAVE_TIMEOUT },
      );
    });

    it("같은 별을 다시 누르면 해제된다 — 잘못 누른 별을 되돌릴 수 있어야 한다", async () => {
      const saved = captureLogSave();
      const user = userEvent.setup();
      mock({
        log: { rating: 3, memo: null, updatedAt: "2026-08-08T09:00:00Z" },
      });
      renderDetail();

      const third = await screen.findByRole("button", { name: "3점" });
      expect(third).toHaveAttribute("aria-pressed", "true");

      await user.click(third);

      expect(third).toHaveAttribute("aria-pressed", "false");
      await waitFor(
        () => expect(saved).toContainEqual({ rating: null, memo: null }),
        { timeout: SAVE_TIMEOUT },
      );
    });

    it("기존 기록을 그대로 보여준다", async () => {
      mock({
        log: {
          rating: 5,
          memo: "야경이 좋았다",
          updatedAt: "2026-08-08T09:00:00Z",
        },
        hasLog: true,
      });
      renderDetail();

      expect(await screen.findByLabelText("기록 메모")).toHaveValue(
        "야경이 좋았다",
      );
      expect(screen.getByRole("button", { name: "5점" })).toHaveAttribute(
        "aria-pressed",
        "true",
      );
    });
  });

  describe("메모", () => {
    it("타이핑이 멎으면 한 번만 저장한다 — 글자마다 요청하지 않는다", async () => {
      const saved = captureLogSave();
      const user = userEvent.setup();
      renderDetail();

      await user.type(await screen.findByLabelText("기록 메모"), "좋았다");

      await waitFor(
        () => expect(saved).toContainEqual({ rating: null, memo: "좋았다" }),
        { timeout: SAVE_TIMEOUT },
      );
      expect(saved).toHaveLength(1);
    });

    it("계획 저장과 별개다 — 계획을 저장하지 않아도 기록만 남는다", async () => {
      const planSaves: unknown[] = [];
      server.use(
        http.put(`${API_BASE}/travel/activities/:id`, async ({ request }) => {
          planSaves.push(await request.json());
          return HttpResponse.json({ code: "OK", data: activity() });
        }),
      );
      const saved = captureLogSave();
      const user = userEvent.setup();
      renderDetail();

      await user.click(await screen.findByRole("button", { name: "2점" }));

      await waitFor(() => expect(saved).toHaveLength(1), {
        timeout: SAVE_TIMEOUT,
      });
      expect(planSaves).toHaveLength(0);
    });
  });

  describe("오프라인", () => {
    it("입력을 막는다 — 저장할 수 없는 칸을 열어두지 않는다", async () => {
      vi.spyOn(navigator, "onLine", "get").mockReturnValue(false);
      renderDetail();

      expect(await screen.findByLabelText("기록 메모")).toBeDisabled();
      expect(screen.getByRole("button", { name: "3점" })).toBeDisabled();
    });
  });
});
