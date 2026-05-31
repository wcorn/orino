import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

import { server } from "@/test/mocks/server";

import { useCreateFlashcard } from "./useCreateFlashcard";

const API_BASE = "https://api.orino.dev/api";

function makeWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const spy = vi.spyOn(queryClient, "invalidateQueries");
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return { wrapper, spy };
}

describe("useCreateFlashcard", () => {
  it("카드 생성 성공 시 복습(today·캘린더 포함) 쿼리를 무효화한다", async () => {
    server.use(
      http.post(`${API_BASE}/planner/materials/1/flashcards`, () =>
        HttpResponse.json(
          {
            code: "OK",
            data: {
              flashcard: {
                id: 99,
                materialId: 1,
                front: "test",
                back: "test",
                nextReview: null,
                createdAt: "2026-05-31T10:00:00",
              },
              firstReview: {
                id: 1,
                flashcardId: 99,
                sequence: 1,
                scheduledDate: "2026-06-01",
                intervalDays: 1,
                easeFactor: 2.5,
                status: "PENDING",
              },
            },
          },
          { status: 201 },
        ),
      ),
    );

    const { wrapper, spy } = makeWrapper();
    const { result } = renderHook(() => useCreateFlashcard(1), { wrapper });

    result.current.mutate({ front: "test", back: "test" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    // 캘린더 쿼리(["reviews","calendar",...])까지 포함하도록 ["reviews"] 프리픽스를 무효화해야 한다.
    const invalidatedKeys = spy.mock.calls.map((call) => call[0]?.queryKey);
    expect(invalidatedKeys).toContainEqual(["reviews"]);
  });
});
