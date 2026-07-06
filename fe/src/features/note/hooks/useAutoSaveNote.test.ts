import { act, renderHook } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { server } from "@/test/mocks/server";

import { useAutoSaveNote } from "./useAutoSaveNote";

const API_BASE = "https://api.orino.dev/api";

describe("useAutoSaveNote", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("schedule 후 2초 debounce되어 PATCH를 호출하고 saved로 전환된다", async () => {
    const calls: unknown[] = [];
    server.use(
      http.patch(`${API_BASE}/notes/:id`, async ({ request }) => {
        calls.push(await request.json());
        return HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            materialId: 1,
            parentId: null,
            title: "t",
            sortOrder: 0,
            updatedAt: "2026-05-31T10:30:00",
          },
        });
      }),
    );

    const { result } = renderHook(() => useAutoSaveNote(1));
    expect(result.current.status).toBe("idle");

    act(() => {
      result.current.schedule({ content: { type: "doc", content: [] } });
    });
    expect(result.current.status).toBe("idle");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });

    expect(calls).toHaveLength(1);
    expect(calls[0]).toEqual({ content: { type: "doc", content: [] } });
    expect(result.current.status).toBe("saved");
  });

  it("같은 창의 title/content schedule은 병합되어 1회 PATCH", async () => {
    let count = 0;
    let lastBody: { title?: string; content?: unknown } | null = null;
    server.use(
      http.patch(`${API_BASE}/notes/:id`, async ({ request }) => {
        count++;
        lastBody = (await request.json()) as typeof lastBody;
        return HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            materialId: 1,
            parentId: null,
            title: "t",
            sortOrder: 0,
            updatedAt: "2026-05-31T10:30:00",
          },
        });
      }),
    );

    const { result } = renderHook(() => useAutoSaveNote(1));
    act(() => {
      result.current.schedule({ title: "새 제목" });
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
    act(() => {
      result.current.schedule({ content: { type: "doc", content: [] } });
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });

    expect(count).toBe(1);
    expect(lastBody).toEqual({
      title: "새 제목",
      content: { type: "doc", content: [] },
    });
  });

  it("실패하면 error → retry로 재전송하여 saved", async () => {
    let failed = false;
    server.use(
      http.patch(`${API_BASE}/notes/:id`, async () => {
        if (!failed) {
          failed = true;
          return HttpResponse.json({ code: "ERR" }, { status: 500 });
        }
        return HttpResponse.json({
          code: "OK",
          data: {
            id: 1,
            materialId: 1,
            parentId: null,
            title: "t",
            sortOrder: 0,
            updatedAt: "2026-05-31T10:30:00",
          },
        });
      }),
    );

    const { result } = renderHook(() => useAutoSaveNote(1));
    act(() => {
      result.current.schedule({ title: "x" });
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    expect(result.current.status).toBe("error");

    await act(async () => {
      result.current.retry();
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(result.current.status).toBe("saved");
  });
});
