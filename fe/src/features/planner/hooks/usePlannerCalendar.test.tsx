import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { installFocusRevalidation } from "@/app/focusRevalidation";
import { server } from "@/test/mocks/server";

import { usePlannerCalendar } from "./usePlannerCalendar";

const CALENDAR_URL = "https://api.orino.dev/api/planner/calendar";
const FROM = "2026-06-01";
const TO = "2026-06-30";

function setVisibility(state: "visible" | "hidden") {
  Object.defineProperty(document, "visibilityState", {
    configurable: true,
    get: () => state,
  });
}

/** 실제 복귀처럼 unfocus→focus 전이를 만들어 RQ의 window-focus refetch 판정을 트리거한다. */
function refocus() {
  setVisibility("hidden");
  window.dispatchEvent(new Event("visibilitychange"));
  setVisibility("visible");
  window.dispatchEvent(new Event("focus"));
}

/** events 개수만 다른 최소 피드 응답(envelope). */
function feedResponse(eventCount: number) {
  return HttpResponse.json({
    data: {
      from: FROM,
      to: TO,
      googleConnected: true,
      partial: false,
      errors: [],
      events: Array.from({ length: eventCount }, (_, i) => ({
        id: `e${i}`,
        title: "일정",
        allDay: false,
        start: "2026-06-10T09:00:00",
        end: "2026-06-10T10:00:00",
        location: null,
        recurring: false,
        source: "google",
      })),
      tasks: [],
      reviews: [],
    },
  });
}

/** GET 호출마다 events 개수를 1씩 늘려 응답한다(재호출 여부를 데이터로 식별). */
function installCountingHandler(): () => number {
  let calls = 0;
  server.use(
    http.get(CALENDAR_URL, () => {
      calls += 1;
      return feedResponse(calls);
    }),
  );
  return () => calls;
}

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  };
}

describe("usePlannerCalendar 복귀 재검증", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    server.resetHandlers();
    setVisibility("visible");
  });

  it("dedupe 창(8초) 안의 재포커스는 refetch하지 않는다", async () => {
    installFocusRevalidation();
    const callCount = installCountingHandler();

    const { result } = renderHook(() => usePlannerCalendar(FROM, TO), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.data?.events).toHaveLength(1));
    expect(callCount()).toBe(1);

    // 초기 로드 직후(8초 이내) 복귀 → stale 아님 → refetch 스킵
    refocus();
    await new Promise((r) => setTimeout(r, 50));

    expect(callCount()).toBe(1);
    expect(result.current.data?.events).toHaveLength(1);
  });

  it("dedupe 창을 지난 뒤 복귀하면 SWR로 재검증한다(옛 값 유지 후 교체)", async () => {
    installFocusRevalidation();
    const callCount = installCountingHandler();
    const baseNow = Date.now();

    const { result } = renderHook(() => usePlannerCalendar(FROM, TO), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.data?.events).toHaveLength(1));
    expect(callCount()).toBe(1);

    // 9초 경과를 시뮬레이션(staleTime 8초 초과) → 복귀 시 stale로 판정되어 refetch
    vi.spyOn(Date, "now").mockReturnValue(baseNow + 9000);
    refocus();

    // SWR: refetch 중에도 이전 데이터는 유지되다가 완료 후 교체된다.
    await waitFor(() => expect(callCount()).toBe(2));
    await waitFor(() => expect(result.current.data?.events).toHaveLength(2));
  });
});
