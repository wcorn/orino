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

const TRIP = {
  id: 3,
  title: "도쿄 3박 4일",
  destinationName: "도쿄",
  destinationPlaceId: null,
  startDate: "2026-10-24",
  endDate: "2026-10-27",
  timezone: "Asia/Tokyo",
  currency: "JPY",
  lat: null,
  lng: null,
  defaultNotifyMinutes: 15,
  morningSummaryEnabled: false,
  status: "UPCOMING",
  dDay: 78,
  totalDays: 4,
  activityCount: 3,
};

function mockSummary(hasTrip = true) {
  server.use(
    http.get(`${API_BASE}/travel/summary`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          ongoing: null,
          next: hasTrip
            ? {
                id: 3,
                title: TRIP.title,
                destinationName: "도쿄",
                startDate: TRIP.startDate,
                endDate: TRIP.endDate,
                dDay: 78,
                activityCount: 3,
              }
            : null,
          recentCompleted: null,
        },
      }),
    ),
    http.get(`${API_BASE}/travel/trips/:tripId`, () =>
      HttpResponse.json({ code: "OK", data: TRIP }),
    ),
  );
}

function mockPublicKey(key: string | null) {
  server.use(
    http.get(`${API_BASE}/travel/push/public-key`, () =>
      HttpResponse.json({ code: "OK", data: { publicKey: key } }),
    ),
  );
}

/**
 * `navigator.serviceWorker`는 `vi.restoreAllMocks()`로 안 돌아온다 —
 * `Object.defineProperty`로 심은 값이라 <b>직접 되돌려야</b> 다음 테스트로 새지 않는다.
 * (CI에서 뒤 테스트들이 `getSubscription is not a function`으로 무더기 실패했다.)
 */
const ORIGINAL_SW = Object.getOwnPropertyDescriptor(navigator, "serviceWorker");

function stubServiceWorker(value: unknown) {
  Object.defineProperty(navigator, "serviceWorker", {
    value,
    configurable: true,
  });
}

function restoreServiceWorker() {
  if (ORIGINAL_SW) {
    Object.defineProperty(navigator, "serviceWorker", ORIGINAL_SW);
  } else {
    // 원래 없던 속성이면 지워야 `"serviceWorker" in navigator`가 다시 false가 된다.
    Reflect.deleteProperty(navigator, "serviceWorker");
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

describe("TravelSettingsPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
    useToastStore.setState({ toasts: [] });
    mockSummary();
    // jsdom에는 PushManager가 없다 — 기본 상태는 "지원 안 함"이다.
    mockPublicKey("BExamplePublicKey");
  });

  afterEach(() => {
    vi.restoreAllMocks();
    restoreServiceWorker();
  });

  describe("알림 권한", () => {
    it("웹푸시를 못 하는 브라우저에는 지원 안 함으로 알린다", async () => {
      renderSettings();

      // jsdom은 PushManager가 없다. 상태를 숨기지 않고 그대로 말한다.
      expect(await screen.findByText("지원 안 함")).toBeInTheDocument();
    });

    it("SW가 없으면 지원 안 함이다 — ready를 기다리면 화면이 멈춘다", async () => {
      vi.stubGlobal("PushManager", class {});
      vi.stubGlobal("Notification", { permission: "default" });
      stubServiceWorker({ getRegistration: () => Promise.resolve(undefined) });

      renderSettings();

      expect(await screen.findByText("지원 안 함")).toBeInTheDocument();
    });

    it("서버에 키가 없으면 준비 중으로 둔다 — 오류가 아니다", async () => {
      // 지원되고 SW도 등록돼 있는데 서버가 아직 키를 안 준 상태.
      vi.stubGlobal("PushManager", class {});
      vi.stubGlobal("Notification", { permission: "default" });
      // ready가 아니라 getRegistration을 본다 — ready는 SW가 없으면 영원히 안 풀린다.
      stubServiceWorker({
        getRegistration: () =>
          Promise.resolve({
            // 구독 조회까지 가므로 형태를 맞춰 둔다.
            pushManager: { getSubscription: () => Promise.resolve(null) },
          }),
      });
      mockPublicKey(null);

      renderSettings();

      expect(await screen.findByText("준비 중")).toBeInTheDocument();
    });
  });

  describe("여행 알림 설정", () => {
    it("기본 알림 시점과 아침 요약을 보여준다", async () => {
      renderSettings();

      expect(await screen.findByText("아침 요약 알림")).toBeInTheDocument();
      expect(
        screen.getByText(
          "그날 기준 도시의 현지 08:00 · 도시가 바뀌는 날은 전날 도시 08:00",
        ),
      ).toBeInTheDocument();
      // 어느 여행에 적용되는지 밝힌다 — 설정이 여행 단위라서다.
      expect(screen.getByText(/도쿄 3박 4일에 적용됩니다/)).toBeInTheDocument();
    });

    it("아침 요약을 켜면 여행 설정으로 저장한다", async () => {
      const bodies: Record<string, unknown>[] = [];
      server.use(
        http.put(`${API_BASE}/travel/trips/:tripId`, async ({ request }) => {
          bodies.push((await request.json()) as Record<string, unknown>);
          return HttpResponse.json({
            code: "OK",
            data: { ...TRIP, morningSummaryEnabled: true },
          });
        }),
      );

      const user = userEvent.setup();
      renderSettings();
      await user.click(
        await screen.findByRole("switch", { name: "아침 요약 알림" }),
      );

      await waitFor(() => expect(bodies).toHaveLength(1));
      expect(bodies[0]).toMatchObject({
        morningSummaryEnabled: true,
        // 제목·기간은 덮어쓰지 않도록 그대로 실어 보낸다(전체 수정 API).
        title: "도쿄 3박 4일",
        startDate: "2026-10-24",
      });
      // 구간은 보내지 않는다 — 알림 스위치 하나가 날짜별 기준 도시를 되감으면 안 된다.
      expect(bodies[0]).not.toHaveProperty("legs");
    });

    it("켜져 있으면 어떤 요약이 오는지 미리 보여준다", async () => {
      mockSummary();
      mockPublicKey("key");
      // 미리보기는 켜져 있을 때만 나온다 — 끈 사람에게 보여줄 이유가 없다.
      server.use(
        http.get(`${API_BASE}/travel/trips/:tripId`, () =>
          HttpResponse.json({
            code: "OK",
            data: { ...TRIP, morningSummaryEnabled: true },
          }),
        ),
      );

      renderSettings();
      await screen.findByText("아침 요약 알림");

      // 문구만으로는 두 형태가 그려지지 않는다.
      expect(
        screen.getByText("교토 · 오늘 일정 4개 · 첫 일정 09:00"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("오사카 → 교토 · 오늘 일정 3개"),
      ).toBeInTheDocument();
    });

    it("여행이 없으면 설정할 것이 없다고 알린다", async () => {
      mockSummary(false);

      renderSettings();

      expect(
        await screen.findByText("여행을 만들면 알림 설정을 할 수 있어요."),
      ).toBeInTheDocument();
    });
  });

  describe("정보", () => {
    it("데이터 출처를 밝힌다 — OSM은 표기가 필수다", async () => {
      renderSettings();

      expect(await screen.findByText(/© OpenStreetMap/)).toBeInTheDocument();
    });
  });
});
