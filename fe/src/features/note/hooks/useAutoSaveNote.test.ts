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

  it("schedule 호출 후 2초 debounce되어 PUT을 호출하고 saved 상태로 전환된다", async () => {
    const calls: Array<{ content: unknown }> = [];
    server.use(
      http.put(
        `${API_BASE}/planner/materials/:id/note`,
        async ({ request }) => {
          const body = (await request.json()) as { content: unknown };
          calls.push(body);
          return HttpResponse.json({
            code: "OK",
            data: {
              id: 1,
              materialId: 1,
              updatedAt: "2026-05-18T10:30:00",
            },
          });
        },
      ),
    );

    const { result } = renderHook(() => useAutoSaveNote(1));
    expect(result.current.status).toBe("idle");

    act(() => {
      result.current.schedule({ type: "doc", content: [] });
    });
    // 디바운스 전: idle 유지
    expect(result.current.status).toBe("idle");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });

    expect(calls).toHaveLength(1);
    expect(calls[0]).toEqual({ content: { type: "doc", content: [] } });
    expect(result.current.status).toBe("saved");
    expect(result.current.savedAt).not.toBeNull();
  });

  it("연속 schedule은 마지막 값만 1회 PUT으로 합쳐진다 (debounce)", async () => {
    let putCount = 0;
    server.use(
      http.put(
        `${API_BASE}/planner/materials/:id/note`,
        async ({ request }) => {
          putCount++;
          const body = (await request.json()) as { content: { tag?: string } };
          return HttpResponse.json({
            code: "OK",
            data: {
              id: 1,
              materialId: 1,
              updatedAt: "2026-05-18T10:30:00",
              _echo: body.content.tag,
            },
          });
        },
      ),
    );

    const { result } = renderHook(() => useAutoSaveNote(1));
    act(() => {
      result.current.schedule({ type: "doc", tag: "a" });
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1500);
    });
    act(() => {
      result.current.schedule({ type: "doc", tag: "b" });
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });

    expect(putCount).toBe(1);
  });

  it("실패 시 error 상태가 되고 retry로 재전송된다", async () => {
    let firstFailed = false;
    server.use(
      http.put(`${API_BASE}/planner/materials/:id/note`, async () => {
        if (!firstFailed) {
          firstFailed = true;
          return HttpResponse.json(
            { code: "ERR", message: "fail" },
            { status: 500 },
          );
        }
        return HttpResponse.json({
          code: "OK",
          data: { id: 1, materialId: 1, updatedAt: "2026-05-18T10:30:00" },
        });
      }),
    );

    const { result } = renderHook(() => useAutoSaveNote(1));
    act(() => {
      result.current.schedule({ type: "doc", content: [] });
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
