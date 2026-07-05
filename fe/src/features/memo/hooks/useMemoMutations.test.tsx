import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";

import { server } from "@/test/mocks/server";

import { useMoveMemo } from "./useMemoMutations";

const API_BASE = "https://api.orino.dev/api";

function wrapper({ children }: { children: ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe("useMoveMemo", () => {
  it("orderedIds를 sortOrder 0..n으로 순차 PATCH하고 드래그 노드는 parentId까지 담는다", async () => {
    const calls: { id: number; body: unknown }[] = [];
    server.use(
      http.patch(`${API_BASE}/memos/:id`, async ({ request, params }) => {
        calls.push({ id: Number(params.id), body: await request.json() });
        return HttpResponse.json({
          code: "OK",
          data: {
            id: Number(params.id),
            parentId: null,
            title: "t",
            sortOrder: 0,
            updatedAt: "2026-07-05T10:00:00",
          },
        });
      }),
    );

    const { result } = renderHook(() => useMoveMemo(), { wrapper });
    // 메모 1을 부모 2의 마지막 자식으로 이동 (기존 자식 21,22 뒤)
    result.current.mutate({
      plan: { parentId: 2, orderedIds: [21, 22, 1] },
      dragId: 1,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(calls).toEqual([
      { id: 21, body: { sortOrder: 0 } },
      { id: 22, body: { sortOrder: 1 } },
      { id: 1, body: { parentId: 2, sortOrder: 2 } },
    ]);
  });
});
