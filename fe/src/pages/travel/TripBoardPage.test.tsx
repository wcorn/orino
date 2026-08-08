import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { act } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import { usePendingActions } from "@/features/travel/board/pendingActions";
import { useToastStore } from "@/shared/lib/toast";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

const API_BASE = "https://api.orino.dev/api";

const DAYS = [
  {
    dayIndex: 1,
    date: "2026-10-24",
    weekday: "토",
    activityCount: 2,
    weather: null,
  },
  {
    dayIndex: 2,
    date: "2026-10-25",
    weekday: "일",
    activityCount: 0,
    weather: null,
  },
  {
    dayIndex: 3,
    date: "2026-10-26",
    weekday: "월",
    activityCount: 1,
    weather: null,
  },
];

const TRIP = {
  id: 3,
  title: "도쿄 3박 4일",
  timezone: "Asia/Tokyo",
  currency: "JPY",
  startDate: "2026-10-24",
  endDate: "2026-10-26",
  status: "UPCOMING",
  recordMode: false,
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
    hasLog: false,
    ...overrides,
  };
}

/**
 * 날짜별 보드를 흉내낸다. 서버처럼 `date`·`archive` 파라미터로 갈라 응답한다.
 * `selectedDate`는 요청한 날짜(생략 시 1일차)를 그대로 돌려준다.
 */
function mockBoard(options: {
  byDate?: Record<string, unknown[]>;
  archive?: unknown[];
  trip?: Record<string, unknown>;
}) {
  const byDate = options.byDate ?? {};
  const archive = options.archive ?? [];
  server.use(
    http.get(`${API_BASE}/travel/trips/:tripId/board`, ({ request }) => {
      const url = new URL(request.url);
      const isArchive = url.searchParams.get("archive") === "true";
      const date = url.searchParams.get("date") ?? DAYS[0].date;
      return HttpResponse.json({
        code: "OK",
        data: {
          trip: { ...TRIP, ...options.trip },
          days: DAYS,
          selectedDate: isArchive ? null : date,
          archiveCount: archive.length,
          activities: isArchive ? archive : (byDate[date] ?? []),
          legs: [],
        },
      });
    }),
  );
}

function renderBoard(path = "/travel/trips/3/board") {
  return renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: [path] },
  );
}

describe("TripBoardPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
    // 스낵바는 모듈 스토어라 테스트 사이에 남는다. 이전 실행취소 스낵바가 섞이면
    // 다음 테스트가 엉뚱한 버튼을 누른다.
    useToastStore.setState({ toasts: [] });
    // 보류함도 모듈 스토어라 테스트 사이에 남는다.
    usePendingActions.setState({ pendingIds: [], commits: new Map() });
  });

  describe("날짜 탭", () => {
    it("기간의 모든 날짜와 맨 뒤에 보관함 칩을 보여준다", async () => {
      mockBoard({
        byDate: { "2026-10-24": [activity()] },
        archive: [activity({ id: 9 })],
      });

      renderBoard();

      const tabs = await screen.findAllByRole("tab");
      expect(tabs).toHaveLength(4);
      expect(tabs[0]).toHaveTextContent("1일차");
      expect(tabs[0]).toHaveTextContent("10.24 토");
      // 보관함은 항상 마지막이고 2행에 건수를 쓴다.
      expect(tabs[3]).toHaveTextContent("보관함");
      expect(tabs[3]).toHaveTextContent("1개");
    });

    it("서버가 고른 날짜가 기본 선택된다", async () => {
      mockBoard({ byDate: { "2026-10-24": [activity()] } });

      renderBoard();

      const tabs = await screen.findAllByRole("tab");
      await waitFor(() => {
        expect(tabs[0]).toHaveAttribute("aria-selected", "true");
      });
    });

    it("탭을 누르면 URL에 ?day= 가 남고 그 날짜를 조회한다", async () => {
      mockBoard({
        byDate: {
          "2026-10-24": [activity()],
          "2026-10-26": [
            activity({ id: 5, title: "디즈니씨", activityDate: "2026-10-26" }),
          ],
        },
      });

      renderBoard();
      await screen.findByText("센소지");

      await userEvent.click(screen.getByRole("tab", { name: /3일차/ }));

      expect(await screen.findByText("디즈니씨")).toBeInTheDocument();
      expect(screen.queryByText("센소지")).toBeNull();
    });

    it("?day= 로 직접 들어오면 그 날짜가 열린다", async () => {
      mockBoard({
        byDate: {
          "2026-10-24": [activity()],
          "2026-10-26": [
            activity({ id: 5, title: "디즈니씨", activityDate: "2026-10-26" }),
          ],
        },
      });

      renderBoard("/travel/trips/3/board?day=2");

      expect(await screen.findByText("디즈니씨")).toBeInTheDocument();
      const tabs = await screen.findAllByRole("tab");
      await waitFor(() => {
        expect(tabs[2]).toHaveAttribute("aria-selected", "true");
      });
    });

    it("?day=archive 로 들어오면 보관함이 열린다", async () => {
      mockBoard({
        byDate: { "2026-10-24": [activity()] },
        archive: [
          activity({ id: 9, title: "가고 싶은 라멘집", activityDate: null }),
        ],
      });

      renderBoard("/travel/trips/3/board?day=archive");

      expect(await screen.findByText("가고 싶은 라멘집")).toBeInTheDocument();
      const tabs = await screen.findAllByRole("tab");
      expect(tabs[3]).toHaveAttribute("aria-selected", "true");
    });
  });

  describe("일정 행", () => {
    it("시각을 tabular-nums로 보여주고, 없으면 ── 로 자리를 지킨다", async () => {
      mockBoard({
        byDate: {
          "2026-10-24": [
            activity(),
            activity({
              id: 2,
              title: "동네 산책",
              startTime: null,
              sortOrder: 1,
            }),
          ],
        },
      });

      renderBoard();

      expect(await screen.findByText("09:00")).toBeInTheDocument();
      expect(screen.getByText("──")).toBeInTheDocument();
    });

    it("장소가 있으면 아래 줄에 장소명을 보여준다", async () => {
      mockBoard({
        byDate: {
          "2026-10-24": [
            activity({
              place: {
                id: 1,
                name: "시부야 스카이",
                address: null,
                lat: null,
                lng: null,
              },
            }),
          ],
        },
      });

      renderBoard();

      expect(await screen.findByText("시부야 스카이")).toBeInTheDocument();
    });

    it("알림이 켜진 일정에 종 아이콘을 붙인다", async () => {
      mockBoard({
        byDate: { "2026-10-24": [activity({ notifyEnabled: true })] },
      });

      renderBoard();

      expect(await screen.findByLabelText("알림 켜짐")).toBeInTheDocument();
    });

    it("일정을 누르면 상세로 간다", async () => {
      mockBoard({ byDate: { "2026-10-24": [activity()] } });

      renderBoard();

      expect(
        await screen.findByRole("link", { name: /센소지/ }),
      ).toHaveAttribute("href", "/travel/activities/1");
    });

    it("드래그 모드가 아닐 때 행이 비활성 버튼으로 읽히지 않는다", async () => {
      mockBoard({ byDate: { "2026-10-24": [activity()] } });

      renderBoard();
      await screen.findByText("센소지");

      // dnd의 disabled sortable은 role="button" aria-disabled를 남긴다.
      // 그러면 행 전체가 비활성으로 읽혀 안의 링크를 누를 수 없다.
      const row = screen.getByRole("link", { name: /센소지/ }).closest("li");
      expect(row).not.toHaveAttribute("aria-disabled");
      expect(row).not.toHaveAttribute("role", "button");
    });

    it("보관함에서는 '보관함으로' 액션을 감춘다", async () => {
      mockBoard({
        byDate: { "2026-10-24": [] },
        archive: [activity({ id: 9, title: "후보", activityDate: null })],
      });

      renderBoard("/travel/trips/3/board?day=archive");
      await screen.findByText("후보");

      expect(screen.queryByLabelText("후보 보관함으로")).toBeNull();
      expect(screen.getByLabelText("후보 삭제")).toBeInTheDocument();
    });
  });

  describe("빈 상태", () => {
    it("일정 없는 날짜와 빈 보관함의 문구가 다르다", async () => {
      mockBoard({ byDate: { "2026-10-24": [] }, archive: [] });

      renderBoard();
      expect(await screen.findByText("일정이 없어요")).toBeInTheDocument();

      await userEvent.click(screen.getByRole("tab", { name: /보관함/ }));
      expect(
        await screen.findByText("가고 싶은 곳을 미리 담아두세요"),
      ).toBeInTheDocument();
    });
  });

  describe("일정 추가", () => {
    it("직접 입력으로 지금 보는 날짜에 일정을 만든다", async () => {
      mockBoard({ byDate: { "2026-10-24": [] } });
      const seen: Record<string, unknown>[] = [];
      server.use(
        http.post(
          `${API_BASE}/travel/trips/:tripId/activities`,
          async ({ request }) => {
            seen.push((await request.json()) as Record<string, unknown>);
            return HttpResponse.json({ code: "OK", data: activity() });
          },
        ),
      );

      renderBoard();
      await screen.findByText("일정이 없어요");

      await userEvent.click(screen.getByRole("button", { name: "일정 추가" }));
      await userEvent.click(
        await screen.findByRole("button", { name: /직접 입력/ }),
      );
      await userEvent.type(screen.getByLabelText("일정 제목"), "센소지");
      await userEvent.type(screen.getByLabelText("시각 (선택)"), "09:00");
      await userEvent.click(screen.getByRole("button", { name: "추가" }));

      await waitFor(() => expect(seen).toHaveLength(1));
      expect(seen[0]).toMatchObject({
        title: "센소지",
        activityDate: "2026-10-24",
        startTime: "09:00",
      });
    });

    it("장소 검색은 시트 안이 아니라 검색 화면으로 나간다", async () => {
      mockBoard({ byDate: { "2026-10-24": [] } });

      renderBoard();
      await screen.findByText("일정이 없어요");
      await userEvent.click(screen.getByRole("button", { name: "일정 추가" }));

      const sheet = await screen.findByRole("dialog");
      await userEvent.click(
        within(sheet).getByRole("button", { name: /장소 검색/ }),
      );

      // 결과 20개와 날짜 선택 시트가 시트 위에 쌓이지 않게 화면을 바꾼다.
      expect(await screen.findByLabelText("장소 검색")).toBeInTheDocument();
    });

    it("보관함 일정을 지금 보는 날짜로 가져온다", async () => {
      mockBoard({
        byDate: { "2026-10-24": [] },
        archive: [
          activity({ id: 9, title: "가고 싶은 라멘집", activityDate: null }),
        ],
      });
      const seen: Record<string, unknown>[] = [];
      server.use(
        http.put(`${API_BASE}/travel/activities/:id`, async ({ request }) => {
          seen.push((await request.json()) as Record<string, unknown>);
          return HttpResponse.json({ code: "OK", data: activity() });
        }),
      );

      renderBoard();
      await screen.findByText("일정이 없어요");
      await userEvent.click(screen.getByRole("button", { name: "일정 추가" }));

      const sheet = await screen.findByRole("dialog");
      await userEvent.click(
        await within(sheet).findByRole("button", { name: "가고 싶은 라멘집" }),
      );

      await waitFor(() => expect(seen).toHaveLength(1));
      expect(seen[0]).toMatchObject({ activityDate: "2026-10-24" });
    });

    it("보관함을 보고 있으면 '가져오기'를 띄우지 않는다", async () => {
      mockBoard({ byDate: { "2026-10-24": [] }, archive: [] });

      renderBoard("/travel/trips/3/board?day=archive");
      await screen.findByText("가고 싶은 곳을 미리 담아두세요");
      await userEvent.click(screen.getByRole("button", { name: "일정 추가" }));

      const sheet = await screen.findByRole("dialog");
      expect(within(sheet).queryByText("보관함에서 가져오기")).toBeNull();
      expect(sheet).toHaveTextContent("미배정 보관함에 담습니다");
    });
  });

  describe("일정 액션 · 실행취소", () => {
    /** 보류 중인 요청을 잡아 두고, 실제로 나갔는지 세는 핸들러. */
    function captureWrites() {
      const puts: Record<string, unknown>[] = [];
      const deletes: string[] = [];
      server.use(
        http.put(`${API_BASE}/travel/activities/:id`, async ({ request }) => {
          puts.push((await request.json()) as Record<string, unknown>);
          return HttpResponse.json({ code: "OK", data: activity() });
        }),
        http.delete(`${API_BASE}/travel/activities/:id`, ({ params }) => {
          deletes.push(String(params.id));
          return HttpResponse.json({ code: "OK", data: null });
        }),
      );
      return { puts, deletes };
    }

    it("보관함으로 옮기면 즉시 사라지지만 요청은 아직 나가지 않는다", async () => {
      mockBoard({ byDate: { "2026-10-24": [activity()] } });
      const { puts } = captureWrites();

      renderBoard();
      await screen.findByText("센소지");

      await userEvent.click(screen.getByLabelText("센소지 보관함으로"));

      // 낙관적으로 목록에서 빠지고, 되돌릴 수 있는 스낵바가 뜬다.
      await waitFor(() => {
        expect(screen.queryByLabelText("센소지 보관함으로")).toBeNull();
      });
      expect(
        await screen.findByRole("button", { name: /실행취소/ }),
      ).toBeInTheDocument();
      expect(puts).toHaveLength(0);
    });

    it("실행취소를 누르면 요청이 아예 나가지 않고 일정이 돌아온다", async () => {
      mockBoard({ byDate: { "2026-10-24": [activity()] } });
      const { puts, deletes } = captureWrites();

      renderBoard();
      await screen.findByText("센소지");
      await userEvent.click(screen.getByLabelText("센소지 삭제"));
      await waitFor(() => {
        expect(screen.queryByLabelText("센소지 삭제")).toBeNull();
      });

      await userEvent.click(
        await screen.findByRole("button", { name: /실행취소/ }),
      );

      expect(await screen.findByText("센소지")).toBeInTheDocument();
      expect(deletes).toHaveLength(0);
      expect(puts).toHaveLength(0);
    });

    it("5초가 지나면 삭제 요청이 실제로 나간다", async () => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      mockBoard({ byDate: { "2026-10-24": [activity()] } });
      const { deletes } = captureWrites();

      renderBoard();
      await screen.findByText("센소지");
      await userEvent.click(screen.getByLabelText("센소지 삭제"));
      await screen.findByRole("button", { name: /실행취소/ });

      await act(async () => {
        vi.advanceTimersByTime(5000);
      });

      await waitFor(() => expect(deletes).toEqual(["1"]));
      vi.useRealTimers();
    });

    it("보류가 풀린 뒤 요청이 실패하면 알려 주고 목록을 되돌린다", async () => {
      mockBoard({ byDate: { "2026-10-24": [activity()] } });
      server.use(
        http.delete(`${API_BASE}/travel/activities/:id`, () =>
          HttpResponse.error(),
        ),
      );

      // 스낵바 타이머가 등록되기 전에 가짜 시계를 켜야 앞당길 수 있다.
      vi.useFakeTimers({ shouldAdvanceTime: true });
      renderBoard();
      await screen.findByText("센소지");
      await userEvent.click(screen.getByLabelText("센소지 삭제"));
      await screen.findByRole("button", { name: /실행취소/ });

      await act(async () => {
        vi.advanceTimersByTime(5000);
      });

      // 화면에서는 이미 지운 뒤라, 실패를 알리지 않으면 서버와 어긋난 채로 남는다.
      expect(
        await screen.findByText("변경을 저장하지 못했어요."),
      ).toBeInTheDocument();
      vi.useRealTimers();
    });

    it("보드를 떠나도 보류가 유지된다 — 되돌릴 기회가 사라지면 안 된다", async () => {
      mockBoard({ byDate: { "2026-10-24": [activity()] } });
      const { deletes } = captureWrites();

      const { unmount } = renderBoard();
      await screen.findByText("센소지");
      await userEvent.click(screen.getByLabelText("센소지 삭제"));
      await screen.findByRole("button", { name: /실행취소/ });

      // 일정 상세 등 다른 화면으로 옮겨 가는 상황. 타이머는 스낵바가 들고 있다.
      unmount();

      expect(deletes).toHaveLength(0);
      expect(usePendingActions.getState().pendingIds).toEqual([1]);
    });

    it("탭을 닫으면 보류 중인 요청을 즉시 보낸다", async () => {
      mockBoard({ byDate: { "2026-10-24": [activity()] } });
      const { deletes } = captureWrites();

      renderBoard();
      await screen.findByText("센소지");
      await userEvent.click(screen.getByLabelText("센소지 삭제"));
      await screen.findByRole("button", { name: /실행취소/ });
      expect(deletes).toHaveLength(0);

      // 그냥 사라지면 사용자는 지웠다고 믿는데 서버에는 남는다.
      window.dispatchEvent(new Event("pagehide"));

      await waitFor(() => expect(deletes).toEqual(["1"]));
    });
  });

  describe("헤더", () => {
    it("메뉴에서 여행 수정으로 간다", async () => {
      mockBoard({ byDate: { "2026-10-24": [] } });
      server.use(
        http.get(`${API_BASE}/travel/trips/:tripId`, () =>
          HttpResponse.json({
            code: "OK",
            data: {
              ...TRIP,
              destinationName: "도쿄",
              destinationPlaceId: null,
              lat: null,
              lng: null,
              defaultNotifyMinutes: 15,
              morningSummaryEnabled: false,
              dDay: 78,
              totalDays: 3,
              activityCount: 0,
            },
          }),
        ),
      );

      renderBoard();
      await screen.findByText("일정이 없어요");

      await userEvent.click(screen.getByRole("button", { name: "여행 메뉴" }));
      await userEvent.click(
        await screen.findByRole("menuitem", { name: "여행 수정" }),
      );

      await waitFor(() => {
        expect(
          screen.getByRole("heading", { name: "여행 수정" }),
        ).toBeInTheDocument();
      });
    });

    it("지도·도구는 후속 단계라 비활성이다", async () => {
      mockBoard({ byDate: { "2026-10-24": [] } });

      renderBoard();
      await screen.findByText("일정이 없어요");

      expect(screen.getByRole("button", { name: "지도" })).toBeDisabled();
      expect(screen.getByRole("button", { name: "도구" })).toBeDisabled();
    });
  });
});
