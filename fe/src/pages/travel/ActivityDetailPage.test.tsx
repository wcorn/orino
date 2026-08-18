import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import { usePendingActions } from "@/features/travel/board/pendingActions";
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
  endDate: "2026-10-26",
  timezone: "Asia/Tokyo",
  currency: "JPY",
  lat: null,
  lng: null,
  defaultNotifyMinutes: 15,
  morningSummaryEnabled: false,
  status: "UPCOMING",
  dDay: 78,
  totalDays: 3,
  activityCount: 1,
};

function activity(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    tripId: 3,
    title: "센소지",
    activityDate: "2026-10-24",
    startTime: "09:00",
    place: null,
    memo: null,
    url: null,
    notifyEnabled: false,
    notifyMinutes: null,
    departureNotifyEnabled: false,
    sortOrder: 0,
    log: null,
    hasLog: false,
    outOfBaseCity: false,
    // 서버가 판정해 내려주는 값(#1142). 화면은 읽기만 한다.
    canDepartureNotify: true,
    ...overrides,
  };
}

function mockDetail(overrides: Record<string, unknown> = {}) {
  server.use(
    http.get(`${API_BASE}/travel/activities/:id`, () =>
      HttpResponse.json({ code: "OK", data: activity(overrides) }),
    ),
    http.get(`${API_BASE}/travel/trips/:tripId`, () =>
      HttpResponse.json({ code: "OK", data: TRIP }),
    ),
  );
}

/** 저장 요청 본문을 잡아 둔다. */
function captureSave() {
  const seen: Record<string, unknown>[] = [];
  server.use(
    http.put(`${API_BASE}/travel/activities/:id`, async ({ request }) => {
      seen.push((await request.json()) as Record<string, unknown>);
      return HttpResponse.json({ code: "OK", data: activity() });
    }),
  );
  return seen;
}

function renderDetail() {
  return renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: ["/travel/activities/1"] },
  );
}

describe("ActivityDetailPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
    useToastStore.setState({ toasts: [] });
    usePendingActions.setState({ pendingIds: [], commits: new Map() });
  });

  it("계획과 알림 영역을 보여준다 — 기록은 4단계다", async () => {
    mockDetail();
    renderDetail();

    await waitFor(() => {
      expect(screen.getByLabelText("제목")).toHaveValue("센소지");
    });
    expect(screen.getByText("계획")).toBeInTheDocument();
    expect(screen.getByText("알림")).toBeInTheDocument();
    // 기록은 빈 껍데기를 두지 않고 아예 렌더하지 않는다.
    expect(screen.queryByText("기록")).toBeNull();
  });

  it("기존 값으로 폼을 채운다", async () => {
    mockDetail({ memo: "나카미세부터", url: "https://example.com" });
    renderDetail();

    await waitFor(() => {
      expect(screen.getByLabelText("제목")).toHaveValue("센소지");
    });
    expect(screen.getByLabelText("시작 시각")).toHaveValue("09:00");
    expect(screen.getByLabelText("메모")).toHaveValue("나카미세부터");
    expect(screen.getByLabelText("링크")).toHaveValue("https://example.com");
  });

  it("날짜 선택지에 1일차~N일차와 보관함이 있다", async () => {
    mockDetail();
    renderDetail();

    await waitFor(() => {
      expect(screen.getByLabelText("제목")).toHaveValue("센소지");
    });
    await userEvent.click(screen.getByRole("combobox", { name: "날짜" }));

    expect(
      await screen.findByRole("option", { name: /1일차/ }),
    ).toBeInTheDocument();
    expect(screen.getByRole("option", { name: /3일차/ })).toBeInTheDocument();
    expect(
      screen.getByRole("option", { name: "보관함 (미배정)" }),
    ).toBeInTheDocument();
  });

  it("날짜 선택지에 도시명이 붙는다 — `4일차`만으로는 어디로 옮기는지 모른다", async () => {
    mockDetail();
    renderDetail();
    await waitFor(() => {
      expect(screen.getByLabelText("제목")).toHaveValue("센소지");
    });

    await userEvent.click(screen.getByRole("combobox", { name: "날짜" }));

    // 기본 보드 목이 1일차를 도쿄로 준다.
    expect(
      await screen.findByRole("option", { name: "1일차 · 도쿄 (10.24)" }),
    ).toBeInTheDocument();
  });

  it("헤더 부제가 며칠째의 어느 도시인지 말한다", async () => {
    mockDetail();
    renderDetail();

    expect(await screen.findByText("1일차 · 도쿄 · 10.24")).toBeInTheDocument();
  });

  it("보관함 일정은 날짜가 없다고 말한다", async () => {
    mockDetail({ activityDate: null });
    renderDetail();

    expect(await screen.findByText("보관함 · 날짜 미정")).toBeInTheDocument();
  });

  it("보관함을 고르면 activityDate를 null로 저장한다", async () => {
    mockDetail();
    const seen = captureSave();
    renderDetail();

    await waitFor(() => {
      expect(screen.getByLabelText("제목")).toHaveValue("센소지");
    });
    await userEvent.click(screen.getByRole("combobox", { name: "날짜" }));
    await userEvent.click(
      await screen.findByRole("option", { name: "보관함 (미배정)" }),
    );
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(seen).toHaveLength(1));
    expect(seen[0].activityDate).toBeNull();
  });

  it("시각을 지우고 저장할 수 있다(시각 없는 일정은 정상이다)", async () => {
    mockDetail();
    const seen = captureSave();
    renderDetail();

    await waitFor(() => {
      expect(screen.getByLabelText("시작 시각")).toHaveValue("09:00");
    });
    await userEvent.clear(screen.getByLabelText("시작 시각"));
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(seen).toHaveLength(1));
    expect(seen[0].startTime).toBeNull();
  });

  it("시각을 넣고 저장해도 장소가 살아남는다 — 수정은 전체 교체다(#1197)", async () => {
    // 이 화면의 장소 블록은 읽기 전용이라 사용자가 장소를 지울 방법이 없다.
    // `placeId`를 빠뜨리면 서버가 place_id를 NULL로 덮어 이름·주소·좌표가 다 사라진다.
    mockDetail({
      startTime: null,
      place: {
        id: 10,
        name: "센소지",
        address: "다이토구",
        lat: 35.7147651,
        lng: 139.7966553,
        cityName: "도쿄",
        cityPlaceRef: "ChIJ_tokyo",
      },
    });
    const seen = captureSave();
    renderDetail();

    await waitFor(() => {
      expect(screen.getByLabelText("시작 시각")).toHaveValue("");
    });
    await userEvent.type(screen.getByLabelText("시작 시각"), "09:00");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(seen).toHaveLength(1));
    expect(seen[0].startTime).toBe("09:00");
    expect(seen[0].placeId).toBe(10);
  });

  it("시각은 문자열 그대로 보낸다 — 기기 타임존으로 변환하지 않는다", async () => {
    mockDetail();
    const seen = captureSave();
    renderDetail();

    await waitFor(() => {
      expect(screen.getByLabelText("시작 시각")).toHaveValue("09:00");
    });
    await userEvent.clear(screen.getByLabelText("시작 시각"));
    await userEvent.type(screen.getByLabelText("시작 시각"), "07:30");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(seen).toHaveLength(1));
    expect(seen[0].startTime).toBe("07:30");
  });

  it("빈 메모·링크는 null로 보낸다", async () => {
    mockDetail({ memo: "지울 메모" });
    const seen = captureSave();
    renderDetail();

    await waitFor(() => {
      expect(screen.getByLabelText("메모")).toHaveValue("지울 메모");
    });
    await userEvent.clear(screen.getByLabelText("메모"));
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(seen).toHaveLength(1));
    expect(seen[0].memo).toBeNull();
    expect(seen[0].url).toBeNull();
  });

  it("알림 설정은 건드리지 않고 그대로 유지한다(3단계 화면의 몫)", async () => {
    mockDetail({ notifyEnabled: true, notifyMinutes: 30 });
    const seen = captureSave();
    renderDetail();

    await waitFor(() => {
      expect(screen.getByLabelText("제목")).toHaveValue("센소지");
    });
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(seen).toHaveLength(1));
    expect(seen[0].notifyEnabled).toBe(true);
    expect(seen[0].notifyMinutes).toBe(30);
  });

  it("저장하면 그 일정이 속한 탭으로 돌아간다", async () => {
    mockDetail();
    captureSave();
    renderDetail();

    await waitFor(() => {
      expect(screen.getByLabelText("제목")).toHaveValue("센소지");
    });
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(screen.getByRole("tab", { name: /10\.24/ })).toHaveAttribute(
        "aria-selected",
        "true",
      );
    });
  });

  it("보관함으로 옮겨 저장하면 보관함 탭으로 돌아간다", async () => {
    mockDetail();
    captureSave();
    server.use(
      http.get(`${API_BASE}/travel/trips/:tripId/board`, ({ request }) => {
        const archive =
          new URL(request.url).searchParams.get("archive") === "true";
        return HttpResponse.json({
          code: "OK",
          data: {
            trip: {
              id: 3,
              title: "도쿄 3박 4일",
              timezone: "Asia/Tokyo",
              currency: "JPY",
              startDate: "2026-10-24",
              endDate: "2026-10-26",
              status: "UPCOMING",
              recordMode: false,
            },
            days: [
              {
                dayIndex: 1,
                date: "2026-10-24",
                weekday: "토",
                activityCount: 0,
                weather: null,
              },
            ],
            selectedDate: archive ? null : "2026-10-24",
            archiveCount: 1,
            activities: [],
            moves: [],
          },
        });
      }),
    );
    renderDetail();

    await waitFor(() => {
      expect(screen.getByLabelText("제목")).toHaveValue("센소지");
    });
    await userEvent.click(screen.getByRole("combobox", { name: "날짜" }));
    await userEvent.click(
      await screen.findByRole("option", { name: "보관함 (미배정)" }),
    );
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    // 기본 탭으로 돌려보내면 방금 옮긴 일정이 어디 갔는지 확인할 수 없다.
    await waitFor(() => {
      expect(screen.getByRole("tab", { name: /보관함/ })).toHaveAttribute(
        "aria-selected",
        "true",
      );
    });
  });

  describe("삭제", () => {
    it("보드로 돌아간 뒤에도 실행취소할 수 있다", async () => {
      mockDetail();
      let deleted = false;
      server.use(
        http.delete(`${API_BASE}/travel/activities/:id`, () => {
          deleted = true;
          return HttpResponse.json({ code: "OK", data: null });
        }),
      );
      renderDetail();
      await waitFor(() => {
        expect(screen.getByLabelText("제목")).toHaveValue("센소지");
      });

      await userEvent.click(screen.getByLabelText("일정 삭제"));

      // 보드로 이동했는데도 되돌릴 기회가 남아 있다 — 보류함이 화면 밖에 있기 때문이다.
      await waitFor(() => {
        expect(screen.getByRole("tab", { name: /10\.24/ })).toBeInTheDocument();
      });
      expect(
        await screen.findByRole("button", { name: /실행취소/ }),
      ).toBeInTheDocument();
      expect(deleted).toBe(false);

      await userEvent.click(screen.getByRole("button", { name: /실행취소/ }));
      expect(deleted).toBe(false);
      expect(usePendingActions.getState().pendingIds).toEqual([]);
    });

    it("되돌리지 않으면 보류함에 남아 있다가 나간다", async () => {
      mockDetail();
      renderDetail();
      await waitFor(() => {
        expect(screen.getByLabelText("제목")).toHaveValue("센소지");
      });

      await userEvent.click(screen.getByLabelText("일정 삭제"));

      await waitFor(() => {
        expect(usePendingActions.getState().pendingIds).toEqual([1]);
      });
    });
  });

  describe("알림 영역 (S-07)", () => {
    const PLACE = {
      id: 10,
      name: "센소지",
      address: "다이토구",
      lat: 35.7147651,
      lng: 139.7966553,
      cityName: "도쿄",
      cityPlaceRef: "ChIJ_tokyo",
    };

    const KYOTO_PLACE = {
      ...PLACE,
      id: 11,
      name: "기요미즈데라",
      cityName: "교토",
      cityPlaceRef: "ChIJ_kyoto",
    };

    /**
     * 그날 보드 — 상세 화면이 <b>날짜가 갖는 것들</b>을 여기서 읽는다.
     *
     * <p>들어오는 이동은 서버가 내려준 `moves`다(#1208). 소요 시간을 적었는지가 출발 알림이
     * 실제로 서는지를 가르므로, 목이 그 값을 쥐고 있어야 한다.
     */
    function mockDayBoard({
      durationMinutes = null,
    }: { durationMinutes?: number | null } = {}) {
      server.use(
        http.get(`${API_BASE}/travel/trips/:tripId/board`, () =>
          HttpResponse.json({
            code: "OK",
            data: {
              trip: { ...TRIP, recordMode: false, singleCity: false },
              days: [
                {
                  dayId: 1,
                  dayIndex: 1,
                  date: "2026-10-24",
                  weekday: "토",
                  activityCount: 2,
                  baseCity: {
                    placeId: 21,
                    name: "교토",
                    timezone: "Asia/Tokyo",
                    currency: "JPY",
                    countryCode: "JP",
                    cityPlaceRef: "ChIJ_kyoto",
                    lat: null,
                    lng: null,
                  },
                  cityChanged: false,
                  legIndex: 1,
                  cityMemo: null,
                  weather: null,
                  stayTonight: null,
                  stayCheckout: null,
                },
              ],
              selectedDate: "2026-10-24",
              archiveCount: 0,
              activities: [
                activity({
                  id: 9,
                  title: "구로몬 시장",
                  place: { ...PLACE, id: 12, cityName: "오사카" },
                }),
                activity({ id: 1, place: KYOTO_PLACE }),
              ],
              moves: [
                {
                  fromActivityId: 9,
                  toActivityId: 1,
                  toStayId: null,
                  mode: durationMinutes === null ? null : "TRAIN",
                  name: null,
                  durationMinutes,
                  url: null,
                  memo: null,
                },
              ],
              stayMove: null,
            },
          }),
        ),
      );
    }

    it("시각이 없으면 통째로 비활성이다 — 언제 보낼지 정할 수 없다", async () => {
      mockDetail({ startTime: null });

      renderDetail();
      await screen.findByLabelText("제목");

      expect(
        screen.getByText("시각을 입력하면 알림을 설정할 수 있어요."),
      ).toBeInTheDocument();
      expect(screen.getByRole("switch", { name: "일정 알림" })).toBeDisabled();
      expect(screen.getByRole("switch", { name: "출발 알림" })).toBeDisabled();
    });

    it("시각이 있으면 일정 알림을 켤 수 있다", async () => {
      mockDetail();

      renderDetail();
      await screen.findByLabelText("제목");

      expect(screen.getByRole("switch", { name: "일정 알림" })).toBeEnabled();
    });

    it("장소가 없으면 출발 알림만 비활성이다 — 어디서 출발하는지 모른다", async () => {
      mockDetail({ place: null });

      renderDetail();
      await screen.findByLabelText("제목");

      expect(screen.getByRole("switch", { name: "일정 알림" })).toBeEnabled();
      expect(screen.getByRole("switch", { name: "출발 알림" })).toBeDisabled();
      expect(
        screen.getByText("시각과 이전 장소가 필요해요"),
      ).toBeInTheDocument();
    });

    it("장소가 있으면 출발 알림을 켤 수 있다", async () => {
      mockDetail({ place: PLACE });

      renderDetail();
      await screen.findByLabelText("제목");

      expect(screen.getByRole("switch", { name: "출발 알림" })).toBeEnabled();
    });

    it("켜고 저장하면 서버로 보낸다 — 서버가 예약을 다시 짠다", async () => {
      mockDetail({ place: PLACE });
      const bodies: Record<string, unknown>[] = [];
      server.use(
        http.put(`${API_BASE}/travel/activities/:id`, async ({ request }) => {
          bodies.push((await request.json()) as Record<string, unknown>);
          return HttpResponse.json({ code: "OK", data: activity() });
        }),
      );

      const user = userEvent.setup();
      renderDetail();
      await screen.findByLabelText("제목");

      await user.click(screen.getByRole("switch", { name: "일정 알림" }));
      await user.click(screen.getByRole("switch", { name: "출발 알림" }));
      await user.click(screen.getByRole("button", { name: "저장" }));

      await waitFor(() => expect(bodies).toHaveLength(1));
      expect(bodies[0]).toMatchObject({
        notifyEnabled: true,
        departureNotifyEnabled: true,
      });
    });

    it("이동 시간을 안 적었으면 켤 수는 있어도 왜 안 오는지 말해 준다", async () => {
      // 스위치는 켰는데 알림이 안 오면, 이유를 모르는 사용자에게는 그냥 고장이다.
      mockDetail({ place: KYOTO_PLACE });
      mockDayBoard();

      renderDetail();
      await screen.findByLabelText("제목");

      expect(
        screen.getByRole("switch", { name: "출발 알림" }),
      ).not.toBeDisabled();
      expect(
        screen.getByText("보드에서 이동 시간을 적으면 알림이 잡혀요"),
      ).toBeInTheDocument();
    });

    it("도시를 넘어 들어와도 시간만 적혀 있으면 그대로 선다 (#1208)", async () => {
      // 자동 계산 시절에는 도시를 넘으면 아예 막혔다. 신칸센 구간이야말로
      // "언제 나서야 하는가"가 가장 중요한 이동이다.
      mockDetail({ place: KYOTO_PLACE });
      mockDayBoard({ durationMinutes: 75 });

      renderDetail();
      await screen.findByLabelText("제목");

      expect(
        screen.getByRole("switch", { name: "출발 알림" }),
      ).not.toBeDisabled();
      expect(
        screen.getByText("시작시각 − 이동 시간 − 5분"),
      ).toBeInTheDocument();
    });

    it("직전에 장소 있는 일정이 없으면 켤 수 없다", async () => {
      mockDetail({ place: PLACE, canDepartureNotify: false });
      mockDayBoard({ durationMinutes: 12 });

      renderDetail();
      await screen.findByLabelText("제목");

      expect(screen.getByRole("switch", { name: "출발 알림" })).toBeDisabled();
      expect(
        screen.getByText("시각과 이전 장소가 필요해요"),
      ).toBeInTheDocument();
    });

    it("알림 시점을 비우면 여행 기본값을 따른다", async () => {
      mockDetail({ notifyEnabled: true, notifyMinutes: 30 });
      const bodies: Record<string, unknown>[] = [];
      server.use(
        http.put(`${API_BASE}/travel/activities/:id`, async ({ request }) => {
          bodies.push((await request.json()) as Record<string, unknown>);
          return HttpResponse.json({ code: "OK", data: activity() });
        }),
      );

      const user = userEvent.setup();
      renderDetail();
      await screen.findByLabelText("제목");
      // 타임존을 함께 말한다 — 09:00이 어느 도시의 09:00인지가 매번 달라진다.
      expect(screen.getByText("시작 30분 전 · Asia/Tokyo")).toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: "저장" }));

      await waitFor(() => expect(bodies).toHaveLength(1));
      // 값이 있으면 그대로 보낸다. null이면 서버가 여행 기본값으로 떨어뜨린다.
      expect(bodies[0].notifyMinutes).toBe(30);
    });
  });
});
