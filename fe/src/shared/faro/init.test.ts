import { afterEach, describe, expect, it, vi } from "vitest";

import { initFaro } from "./init";

describe("initFaro", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
  });

  it("VITE_FARO_URL이 없으면 초기화하지 않는다", async () => {
    vi.stubEnv("VITE_FARO_URL", "");
    vi.stubEnv("VITE_FARO_KEY", "");

    const sdk = await import("@grafana/faro-react");
    const spy = vi.spyOn(sdk, "initializeFaro");

    initFaro();

    expect(spy).not.toHaveBeenCalled();
  });

  it("초기화 실패해도 throw하지 않는다 (앱 동작에 영향 없음)", async () => {
    vi.stubEnv("VITE_FARO_URL", "https://invalid.test/collect");
    vi.stubEnv("VITE_FARO_KEY", "dummy-key");
    vi.spyOn(console, "warn").mockImplementation(() => {});

    expect(() => initFaro()).not.toThrow();
  });
});
