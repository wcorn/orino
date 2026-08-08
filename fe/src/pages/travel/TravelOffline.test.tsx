import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import { useToastStore } from "@/shared/lib/toast";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

const API_BASE = "https://api.orino.dev/api";

/**
 * jsdom에는 `caches`도 `navigator.storage.estimate`도 없다 — 브라우저가 주는 것이지
 * 우리가 만든 것이 아니라 여기서 심어 준다. `defineProperty`로 심은 값은
 * `vi.restoreAllMocks()`로 안 돌아오므로 직접 되돌린다.
 */
const ORIGINAL_STORAGE = Object.getOwnPropertyDescriptor(navigator, "storage");

function stubStorage(estimate: (() => Promise<StorageEstimate>) | null) {
  Object.defineProperty(navigator, "storage", {
    value: estimate ? { estimate } : {},
    configurable: true,
  });
}

/** 아주 작은 Cache Storage 대역. 건수와 삭제만 쓰므로 그만큼만 흉내 낸다. */
function stubCaches(entries: Record<string, string[]>) {
  const store = new Map(Object.entries(entries));
  vi.stubGlobal("caches", {
    has: (name: string) => Promise.resolve(store.has(name)),
    open: (name: string) =>
      Promise.resolve({
        keys: () => Promise.resolve(store.get(name) ?? []),
      }),
    delete: (name: string) => Promise.resolve(store.delete(name)),
  });
  return store;
}

function restoreStorage() {
  if (ORIGINAL_STORAGE) {
    Object.defineProperty(navigator, "storage", ORIGINAL_STORAGE);
  } else {
    Reflect.deleteProperty(navigator, "storage");
  }
}

function renderSettings() {
  return renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: ["/travel/settings"] },
  );
}

describe("설정 오프라인 섹션", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
    useToastStore.setState({ toasts: [] });
    server.use(
      http.get(`${API_BASE}/travel/summary`, () =>
        HttpResponse.json({
          code: "OK",
          data: { ongoing: null, next: null, recentCount: 0 },
        }),
      ),
      http.get(`${API_BASE}/travel/push/public-key`, () =>
        HttpResponse.json({ code: "OK", data: { publicKey: null } }),
      ),
    );
    stubStorage(() =>
      Promise.resolve({ usage: 5 * 1024 * 1024, quota: 1024 * 1024 * 1024 }),
    );
    stubCaches({ "travel-api": ["/api/travel/summary", "/api/travel/trips"] });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    restoreStorage();
  });

  it("저장된 건수와 용량을 함께 보여준다 — 바이트만으로는 감이 안 온다", async () => {
    renderSettings();

    expect(await screen.findByText("일정 2건 · 5.0 MB")).toBeInTheDocument();
  });

  it("무엇이 남는지 밝힌다 — 비운 뒤 앱이 안 열릴까 걱정하게 두지 않는다", async () => {
    renderSettings();

    expect(
      await screen.findByText(
        /앱 자체는 남아 있어 오프라인에서도 계속 열립니다/,
      ),
    ).toBeInTheDocument();
  });

  it("비우면 여행 캐시만 지운다 — 앱 셸을 지우면 오프라인에서 안 열린다", async () => {
    const store = stubCaches({
      "travel-api": ["/api/travel/summary"],
      "workbox-precache-v2": ["/index.html"],
    });
    const user = userEvent.setup();
    renderSettings();

    await user.click(await screen.findByRole("button", { name: "비우기" }));

    await waitFor(() => expect(store.has("travel-api")).toBe(false));
    expect(store.has("workbox-precache-v2")).toBe(true);
    expect(
      await screen.findByText("오프라인 데이터를 비웠어요."),
    ).toBeInTheDocument();
  });

  it("비운 뒤 표시가 따라 바뀐다", async () => {
    const user = userEvent.setup();
    renderSettings();

    await user.click(await screen.findByRole("button", { name: "비우기" }));

    expect(await screen.findByText(/저장된 일정 없음/)).toBeInTheDocument();
  });

  it("비울 게 없으면 버튼을 잠근다", async () => {
    stubCaches({});
    renderSettings();

    await screen.findByText(/저장된 일정 없음/);
    expect(screen.getByRole("button", { name: "비우기" })).toBeDisabled();
  });

  it("용량을 모르는 브라우저에서는 0으로 꾸미지 않는다", async () => {
    stubStorage(null);
    renderSettings();

    // 건수는 알 수 있으니 그것만 말한다.
    expect(await screen.findByText("일정 2건")).toBeInTheDocument();
  });

  it("캐시 자체가 없는 브라우저에서는 모른다고 말한다", async () => {
    stubStorage(null);
    vi.stubGlobal("caches", undefined);
    renderSettings();

    expect(
      await screen.findByText("이 브라우저에서는 알 수 없어요"),
    ).toBeInTheDocument();
  });
});
