import { focusManager } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

import { installFocusRevalidation } from "./focusRevalidation";

function setVisibility(state: "visible" | "hidden") {
  Object.defineProperty(document, "visibilityState", {
    configurable: true,
    get: () => state,
  });
}

describe("installFocusRevalidation", () => {
  let unsubscribe: () => void;

  beforeEach(() => {
    installFocusRevalidation();
    // focusManager는 구독자가 있어야 이벤트 setup이 활성화된다(QueryClient mount 대용).
    unsubscribe = focusManager.subscribe(() => {});
  });

  afterEach(() => {
    unsubscribe();
    setVisibility("visible");
    focusManager.setFocused(undefined);
  });

  it("visibilitychange(hidden) 신호로 unfocused가 된다(탭 전환 복귀 경로)", () => {
    setVisibility("hidden");
    window.dispatchEvent(new Event("visibilitychange"));

    expect(focusManager.isFocused()).toBe(false);
  });

  it("window focus 신호로 focused가 된다(다른 창/앱 복귀 경로 — RQ 기본은 미구독)", () => {
    // 먼저 unfocused로 만든 뒤, focus 이벤트만으로 다시 focused가 되는지 확인한다.
    setVisibility("hidden");
    window.dispatchEvent(new Event("visibilitychange"));
    expect(focusManager.isFocused()).toBe(false);

    setVisibility("visible");
    window.dispatchEvent(new Event("focus"));

    expect(focusManager.isFocused()).toBe(true);
  });
});
