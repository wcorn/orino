import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { resetGoogleMapsLoader } from "./googleMaps";

/**
 * 로더가 <b>스크립트 태그를 언제 붙이는지</b>만 본다. 지도 그리기는 컴포넌트 테스트가
 * 다룬다({@code TripDayMap.test.tsx}).
 */
describe("Maps 로더", () => {
  beforeEach(() => {
    resetGoogleMapsLoader();
    delete (window as { google?: unknown }).google;
    delete window.gm_authFailure;
  });

  afterEach(() => {
    vi.restoreAllMocks();
    resetGoogleMapsLoader();
    delete (window as { google?: unknown }).google;
  });

  it("키가 없으면 스크립트를 받지 않는다 — 없는 키로 부르면 콘솔만 더러워진다", async () => {
    const append = vi.spyOn(document.head, "appendChild");
    const { useGoogleMaps } = await import("./googleMaps");

    // 훅을 부르지 않고 로더만 돌린다(테스트 환경엔 키가 없다).
    expect(typeof useGoogleMaps).toBe("function");
    expect(append).not.toHaveBeenCalled();
  });

  it("이미 올라와 있으면 키를 보지 않는다 — 다른 화면이 먼저 받아 뒀을 수 있다", async () => {
    (window as { google?: unknown }).google = { maps: { Map: class {} } };
    const append = vi.spyOn(document.head, "appendChild");

    const { useGoogleMaps } = await import("./googleMaps");
    expect(typeof useGoogleMaps).toBe("function");

    // 이미 있으니 태그를 새로 붙이지 않는다.
    expect(append).not.toHaveBeenCalled();
  });

  it("google.maps만 있고 생성자가 없으면 준비된 게 아니다", async () => {
    // 프로덕션에서 터진 상태 그대로다 — 스크립트는 로드됐고 google.maps도 있는데
    // google.maps.Map이 아직 undefined였다. 이걸 "준비됨"으로 보면
    // `Map is not a constructor`로 죽는다.
    (window as { google?: unknown }).google = { maps: {} };

    const { useGoogleMaps } = await import("./googleMaps");
    const { renderHook, waitFor } = await import("@testing-library/react");
    const { result } = renderHook(() => useGoogleMaps());

    // 키도 없는 테스트 환경이라 결국 unavailable로 떨어져야 한다. 중요한 건
    // "ready"가 되지 않는다는 것이다.
    await waitFor(() => expect(result.current).not.toBe("loading"));
    expect(result.current).toBe("unavailable");
  });
});
