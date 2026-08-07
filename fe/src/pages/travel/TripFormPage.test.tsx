import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

const API_BASE = "https://api.orino.dev/api";

const TOKYO = {
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
  activityCount: 13,
};

function renderApp(path: string) {
  return renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: [path] },
  );
}

function mockTripDetail(trip = TOKYO) {
  server.use(
    http.get(`${API_BASE}/travel/trips/:tripId`, () =>
      HttpResponse.json({ code: "OK", data: trip }),
    ),
  );
}

/** 생성·수정 요청 본문을 잡아 두는 핸들러. */
function captureWrite(method: "post" | "put") {
  const seen: Record<string, unknown>[] = [];
  const path =
    method === "post"
      ? `${API_BASE}/travel/trips`
      : `${API_BASE}/travel/trips/:tripId`;
  server.use(
    http[method](path, async ({ request }) => {
      seen.push((await request.json()) as Record<string, unknown>);
      return HttpResponse.json({ code: "OK", data: TOKYO });
    }),
  );
  return seen;
}

async function fillNewTripForm() {
  await userEvent.type(screen.getByLabelText("목적지 도시"), "도쿄");
  await userEvent.type(screen.getByLabelText("시작일"), "2026-10-24");
  await userEvent.type(screen.getByLabelText("종료일"), "2026-10-27");
}

describe("TripFormPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
  });

  describe("생성", () => {
    it("명세 순서대로 필드를 보여준다", async () => {
      renderApp("/travel/trips/new");

      await waitFor(() => {
        expect(
          screen.getByRole("heading", { name: "여행 만들기" }),
        ).toBeInTheDocument();
      });
      expect(screen.getByLabelText("목적지 도시")).toBeInTheDocument();
      expect(screen.getByLabelText("여행 제목")).toBeInTheDocument();
      expect(screen.getByLabelText("시작일")).toBeInTheDocument();
      expect(screen.getByLabelText("종료일")).toBeInTheDocument();
      expect(
        screen.getByText(/여행 중에는 이 타임존을 씁니다/),
      ).toBeInTheDocument();
      expect(screen.getByText("기본 알림 시점")).toBeInTheDocument();
    });

    it("제목 placeholder가 입력한 목적지 이름을 따라간다", async () => {
      renderApp("/travel/trips/new");

      const destination = await screen.findByLabelText("목적지 도시");
      await userEvent.type(destination, "오사카");

      expect(screen.getByLabelText("여행 제목")).toHaveAttribute(
        "placeholder",
        "오사카",
      );
    });

    it("제목을 비우면 요청에서 빼서 서버가 목적지명으로 채우게 한다", async () => {
      const seen = captureWrite("post");
      renderApp("/travel/trips/new");
      await screen.findByLabelText("목적지 도시");

      await fillNewTripForm();
      await userEvent.click(screen.getByRole("button", { name: "만들기" }));

      await waitFor(() => expect(seen).toHaveLength(1));
      expect(seen[0].title).toBeUndefined();
      expect(seen[0]).toMatchObject({
        destinationName: "도쿄",
        startDate: "2026-10-24",
        endDate: "2026-10-27",
        timezone: "Asia/Tokyo",
        currency: "JPY",
        defaultNotifyMinutes: 15,
      });
    });

    it("만들면 그 여행의 보드로 간다", async () => {
      captureWrite("post");
      renderApp("/travel/trips/new");
      await screen.findByLabelText("목적지 도시");

      await fillNewTripForm();
      await userEvent.click(screen.getByRole("button", { name: "만들기" }));

      // 만든 여행의 보드로 이동한다.
      await waitFor(() => {
        expect(screen.getByRole("tab", { name: /1일차/ })).toBeInTheDocument();
      });
    });

    it("종료일이 시작일보다 빠르면 저장하지 않고 알려준다", async () => {
      const seen = captureWrite("post");
      renderApp("/travel/trips/new");
      await screen.findByLabelText("목적지 도시");

      await userEvent.type(screen.getByLabelText("목적지 도시"), "도쿄");
      await userEvent.type(screen.getByLabelText("시작일"), "2026-10-27");
      await userEvent.type(screen.getByLabelText("종료일"), "2026-10-24");
      await userEvent.click(screen.getByRole("button", { name: "만들기" }));

      expect(
        await screen.findByText("종료일은 시작일보다 빠를 수 없습니다."),
      ).toBeInTheDocument();
      expect(seen).toHaveLength(0);
    });

    it("목적지를 비우면 저장하지 않는다", async () => {
      const seen = captureWrite("post");
      renderApp("/travel/trips/new");
      await screen.findByLabelText("목적지 도시");

      await userEvent.click(screen.getByRole("button", { name: "만들기" }));

      expect(
        await screen.findByText("목적지를 입력해 주세요."),
      ).toBeInTheDocument();
      expect(seen).toHaveLength(0);
    });
  });

  describe("수정", () => {
    it("기존 값으로 폼을 채운다", async () => {
      mockTripDetail();
      renderApp("/travel/trips/3/edit");

      await waitFor(() => {
        expect(screen.getByLabelText("목적지 도시")).toHaveValue("도쿄");
      });
      expect(screen.getByLabelText("여행 제목")).toHaveValue("도쿄 3박 4일");
      expect(screen.getByLabelText("시작일")).toHaveValue("2026-10-24");
      expect(screen.getByLabelText("종료일")).toHaveValue("2026-10-27");
      expect(
        screen.getByRole("heading", { name: "여행 수정" }),
      ).toBeInTheDocument();
    });

    it("제목이 목적지명과 같으면(자동 저장된 값) 폼을 비워 둔다", async () => {
      mockTripDetail({ ...TOKYO, title: "도쿄" });
      renderApp("/travel/trips/3/edit");

      await waitFor(() => {
        expect(screen.getByLabelText("목적지 도시")).toHaveValue("도쿄");
      });
      expect(screen.getByLabelText("여행 제목")).toHaveValue("");
    });

    it("기간을 줄이지 않으면 확인 없이 저장한다", async () => {
      mockTripDetail();
      const seen = captureWrite("put");
      renderApp("/travel/trips/3/edit");
      await waitFor(() => {
        expect(screen.getByLabelText("종료일")).toHaveValue("2026-10-27");
      });

      // 늘리는 방향.
      await userEvent.clear(screen.getByLabelText("종료일"));
      await userEvent.type(screen.getByLabelText("종료일"), "2026-10-30");
      await userEvent.click(screen.getByRole("button", { name: "저장" }));

      await waitFor(() => expect(seen).toHaveLength(1));
      expect(seen[0].confirmArchive).toBeUndefined();
      expect(screen.queryByRole("dialog")).toBeNull();
    });
  });

  describe("기간 단축 확인", () => {
    function mockShrinkPreview(movedActivityCount: number) {
      server.use(
        http.get(`${API_BASE}/travel/trips/:tripId/shrink-preview`, () =>
          HttpResponse.json({ code: "OK", data: { movedActivityCount } }),
        ),
      );
    }

    it("잘리는 일정이 있으면 개수를 보여주고 확인을 받는다", async () => {
      mockTripDetail();
      mockShrinkPreview(4);
      const seen = captureWrite("put");
      renderApp("/travel/trips/3/edit");
      await waitFor(() => {
        expect(screen.getByLabelText("종료일")).toHaveValue("2026-10-27");
      });

      await userEvent.clear(screen.getByLabelText("종료일"));
      await userEvent.type(screen.getByLabelText("종료일"), "2026-10-25");
      await userEvent.click(screen.getByRole("button", { name: "저장" }));

      const dialog = await screen.findByRole("dialog");
      expect(dialog).toHaveTextContent("기간을 줄이면 일정이 이동합니다");
      expect(dialog).toHaveTextContent(
        "잘리는 날짜의 일정 4개가 미배정 보관함으로 이동합니다.",
      );
      // 확인 전에는 저장 요청이 나가지 않는다.
      expect(seen).toHaveLength(0);
    });

    it("확인하면 confirmArchive를 실어 저장한다", async () => {
      mockTripDetail();
      mockShrinkPreview(4);
      const seen = captureWrite("put");
      renderApp("/travel/trips/3/edit");
      await waitFor(() => {
        expect(screen.getByLabelText("종료일")).toHaveValue("2026-10-27");
      });

      await userEvent.clear(screen.getByLabelText("종료일"));
      await userEvent.type(screen.getByLabelText("종료일"), "2026-10-25");
      await userEvent.click(screen.getByRole("button", { name: "저장" }));
      await screen.findByRole("dialog");
      await userEvent.click(
        screen.getByRole("button", { name: "이동하고 저장" }),
      );

      await waitFor(() => expect(seen).toHaveLength(1));
      expect(seen[0].confirmArchive).toBe(true);
      expect(seen[0].endDate).toBe("2026-10-25");
    });

    it("시작일을 늦춰 앞쪽이 잘려도 확인을 받는다", async () => {
      mockTripDetail();
      mockShrinkPreview(2);
      captureWrite("put");
      renderApp("/travel/trips/3/edit");
      await waitFor(() => {
        expect(screen.getByLabelText("시작일")).toHaveValue("2026-10-24");
      });

      await userEvent.clear(screen.getByLabelText("시작일"));
      await userEvent.type(screen.getByLabelText("시작일"), "2026-10-26");
      await userEvent.click(screen.getByRole("button", { name: "저장" }));

      expect(await screen.findByRole("dialog")).toHaveTextContent(
        "일정 2개가 미배정 보관함으로 이동합니다.",
      );
    });

    it("잘리는 일정이 0개면 확인 없이 바로 저장한다", async () => {
      mockTripDetail();
      mockShrinkPreview(0);
      const seen = captureWrite("put");
      renderApp("/travel/trips/3/edit");
      await waitFor(() => {
        expect(screen.getByLabelText("종료일")).toHaveValue("2026-10-27");
      });

      await userEvent.clear(screen.getByLabelText("종료일"));
      await userEvent.type(screen.getByLabelText("종료일"), "2026-10-25");
      await userEvent.click(screen.getByRole("button", { name: "저장" }));

      await waitFor(() => expect(seen).toHaveLength(1));
      expect(screen.queryByRole("dialog")).toBeNull();
    });

    it("미리보기가 실패해도 저장을 막지 않는다(서버 409가 최종 안전장치)", async () => {
      mockTripDetail();
      server.use(
        http.get(`${API_BASE}/travel/trips/:tripId/shrink-preview`, () =>
          HttpResponse.json(null, { status: 500 }),
        ),
      );
      const seen = captureWrite("put");
      // 콘솔 에러 노이즈를 줄인다(요청 실패는 의도된 경로).
      vi.spyOn(console, "error").mockImplementation(() => {});
      renderApp("/travel/trips/3/edit");
      await waitFor(() => {
        expect(screen.getByLabelText("종료일")).toHaveValue("2026-10-27");
      });

      await userEvent.clear(screen.getByLabelText("종료일"));
      await userEvent.type(screen.getByLabelText("종료일"), "2026-10-25");
      await userEvent.click(screen.getByRole("button", { name: "저장" }));

      await waitFor(() => expect(seen).toHaveLength(1));
      vi.restoreAllMocks();
    });
  });
});
