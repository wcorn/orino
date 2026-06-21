import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";

import { server } from "@/test/mocks/server";

import { useHolidays } from "./useHolidays";

const HOLIDAYS_URL = "https://api.orino.dev/api/planner/holidays";

function wrapper({ children }: { children: ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe("useHolidays", () => {
  it("구간 공휴일을 조회한다", async () => {
    server.use(
      http.get(HOLIDAYS_URL, ({ request }) => {
        const url = new URL(request.url);
        expect(url.searchParams.get("from")).toBe("2026-06-01");
        expect(url.searchParams.get("to")).toBe("2026-06-30");
        return HttpResponse.json({
          code: "OK",
          data: [
            { date: "2026-06-03", name: "지방선거일" },
            { date: "2026-06-06", name: "현충일" },
          ],
        });
      }),
    );

    const { result } = renderHook(
      () => useHolidays("2026-06-01", "2026-06-30"),
      { wrapper },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([
      { date: "2026-06-03", name: "지방선거일" },
      { date: "2026-06-06", name: "현충일" },
    ]);
  });
});
