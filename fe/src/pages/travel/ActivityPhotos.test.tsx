import { screen, waitFor, within } from "@testing-library/react";
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
 * 업로드 경로(canvas 재인코딩)는 jsdom에서 돌지 않는다 — `createImageBitmap`도
 * `canvas.toBlob`도 없다. 크기 계산은 `processPhoto.test.ts`가 단위로 고정하고,
 * 여기서는 <b>이미 올라간 사진</b>의 그리드·뷰어·삭제를 본다.
 */
const TRIP = {
  id: 3,
  title: "도쿄 3박 4일",
  destinationName: "도쿄",
  destinationPlaceId: null,
  startDate: "2026-08-06",
  endDate: "2026-08-10",
  timezone: "Asia/Tokyo",
  currency: "JPY",
  lat: null,
  lng: null,
  defaultNotifyMinutes: 15,
  morningSummaryEnabled: false,
  status: "ONGOING",
  dDay: 0,
  totalDays: 5,
  activityCount: 1,
};

function photo(id: number) {
  return {
    id,
    url: `https://img.orino.dev/note-images/travel/activities/1/${id}.jpg`,
    // 썸네일만 실패할 수 있어 null이 정상값이다.
    thumbUrl: `https://img.orino.dev/note-images/travel/thumbs/1/${id}.jpg` as
      | string
      | null,
    width: 2560,
    height: 1920,
  };
}

function mock(photos: ReturnType<typeof photo>[]) {
  server.use(
    http.get(`${API_BASE}/travel/activities/:id`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          id: 1,
          tripId: 3,
          title: "센소지",
          activityDate: "2026-08-08",
          startTime: "09:00",
          place: null,
          memo: null,
          url: null,
          notifyEnabled: false,
          notifyMinutes: null,
          departureNotifyEnabled: false,
          sortOrder: 0,
          log: {
            rating: 4,
            memo: "좋았다",
            photos,
            updatedAt: "2026-08-08T09:00:00Z",
          },
          hasLog: true,
        },
      }),
    ),
    http.get(`${API_BASE}/travel/trips/:tripId`, () =>
      HttpResponse.json({ code: "OK", data: TRIP }),
    ),
  );
}

function renderDetail() {
  return renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: ["/travel/activities/1"] },
  );
}

describe("기록 사진", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
    useToastStore.setState({ toasts: [] });
    mock([photo(1), photo(2)]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("그리드", () => {
    it("썸네일을 보여준다 — 원본을 그리드에 깔지 않는다", async () => {
      renderDetail();

      const first = await screen.findByRole("button", {
        name: "사진 1 크게 보기",
      });
      // 그리드 썸네일은 장식이라 alt가 비어 있다(버튼이 이름을 갖는다).
      expect(first.querySelector("img")).toHaveAttribute(
        "src",
        expect.stringContaining("/travel/thumbs/"),
      );
    });

    it("썸네일이 없으면 원본을 줄여 쓴다 — 썸네일만 실패할 수 있다", async () => {
      mock([{ ...photo(1), thumbUrl: null }]);
      renderDetail();

      const first = await screen.findByRole("button", {
        name: "사진 1 크게 보기",
      });
      expect(first.querySelector("img")).toHaveAttribute(
        "src",
        expect.stringContaining("/travel/activities/"),
      );
    });

    it("10장이 차면 추가 버튼이 사라지고 이유를 밝힌다", async () => {
      mock(Array.from({ length: 10 }, (_, i) => photo(i + 1)));
      renderDetail();

      expect(
        await screen.findByText("사진은 10장까지 올릴 수 있어요."),
      ).toBeInTheDocument();
      expect(screen.queryByLabelText("사진 추가")).toBeNull();
    });

    it("오프라인이면 삭제 버튼을 감춘다 — 눌러도 실패할 버튼을 두지 않는다", async () => {
      vi.spyOn(navigator, "onLine", "get").mockReturnValue(false);
      renderDetail();

      await screen.findByRole("button", { name: "사진 1 크게 보기" });
      expect(screen.queryByLabelText("사진 1 삭제")).toBeNull();
    });
  });

  describe("삭제", () => {
    it("지우면 그리드에서 바로 빠진다", async () => {
      const deleted: string[] = [];
      server.use(
        http.delete(`${API_BASE}/travel/photos/:id`, ({ params }) => {
          deleted.push(String(params.id));
          return HttpResponse.json({ code: "OK", data: null });
        }),
      );
      const user = userEvent.setup();
      renderDetail();

      await user.click(await screen.findByLabelText("사진 1 삭제"));

      await waitFor(() => expect(deleted).toEqual(["1"]));
      // 남은 한 장은 이제 "사진 1"이다 — 번호는 자리 기준이다.
      expect(screen.getByLabelText("사진 1 삭제")).toBeInTheDocument();
      expect(screen.queryByLabelText("사진 2 삭제")).toBeNull();
    });

    it("실패하면 되돌리고 알린다 — 지워진 줄 알고 넘어가면 안 된다", async () => {
      server.use(
        http.delete(`${API_BASE}/travel/photos/:id`, () =>
          HttpResponse.json({ code: "ERR", message: "boom" }, { status: 500 }),
        ),
      );
      const user = userEvent.setup();
      renderDetail();

      await user.click(await screen.findByLabelText("사진 1 삭제"));

      expect(
        await screen.findByText("사진을 지우지 못했어요."),
      ).toBeInTheDocument();
      // 되돌아와 두 장 그대로다.
      expect(screen.getByLabelText("사진 2 삭제")).toBeInTheDocument();
    });
  });

  describe("전체화면 뷰어", () => {
    it("사진을 누르면 원본을 크게 띄운다", async () => {
      const user = userEvent.setup();
      renderDetail();

      await user.click(
        await screen.findByRole("button", { name: "사진 1 크게 보기" }),
      );

      const dialog = screen.getByRole("dialog", { name: "사진 보기" });
      expect(
        within(dialog).getByRole("img", { name: "사진 1" }),
      ).toHaveAttribute("src", expect.stringContaining("/travel/activities/"));
      expect(within(dialog).getByText("1 / 2")).toBeInTheDocument();
    });

    it("좌우로 넘긴다", async () => {
      const user = userEvent.setup();
      renderDetail();

      await user.click(
        await screen.findByRole("button", { name: "사진 1 크게 보기" }),
      );
      const dialog = screen.getByRole("dialog", { name: "사진 보기" });

      // 첫 장에서는 이전으로 갈 수 없다.
      expect(within(dialog).getByLabelText("이전 사진")).toBeDisabled();

      await user.click(within(dialog).getByLabelText("다음 사진"));

      expect(within(dialog).getByText("2 / 2")).toBeInTheDocument();
      expect(within(dialog).getByLabelText("다음 사진")).toBeDisabled();
    });

    it("Esc로 닫는다", async () => {
      const user = userEvent.setup();
      renderDetail();

      await user.click(
        await screen.findByRole("button", { name: "사진 1 크게 보기" }),
      );
      await user.keyboard("{Escape}");

      expect(screen.queryByRole("dialog", { name: "사진 보기" })).toBeNull();
    });
  });
});
