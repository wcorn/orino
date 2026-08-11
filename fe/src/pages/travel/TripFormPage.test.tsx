import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

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
  destinationPlaceId: 21,
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

const TOKYO_CITY = {
  googlePlaceId: "ChIJ_tokyo",
  name: "도쿄",
  address: "일본 도쿄도",
  lat: 35.6764225,
  lng: 139.650027,
  timezone: "Asia/Tokyo",
  currency: "JPY",
};

const KYOTO_CITY = {
  googlePlaceId: "ChIJ_kyoto",
  name: "교토",
  address: "일본 교토부",
  lat: 35.0116,
  lng: 135.7681,
  timezone: "Asia/Tokyo",
  currency: "JPY",
};

const HONOLULU_CITY = {
  googlePlaceId: "ChIJ_honolulu",
  name: "호놀룰루",
  address: "미국 하와이",
  lat: 21.3069,
  lng: -157.8583,
  timezone: "Pacific/Honolulu",
  currency: "USD",
};

function renderApp(path: string) {
  return renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: [path] },
  );
}

/** 도시 검색 응답. 호출된 검색어를 모아 둔다. */
function mockCities(cities: unknown[] = [TOKYO_CITY]) {
  const queries: string[] = [];
  server.use(
    http.get(`${API_BASE}/travel/places/cities`, ({ request }) => {
      queries.push(new URL(request.url).searchParams.get("q") ?? "");
      return HttpResponse.json({ code: "OK", data: cities });
    }),
  );
  return queries;
}

/** 생성·수정 요청 본문을 잡아 둔다. */
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

/** 직접 입력한 도시 저장 요청. 검색이 막혔을 때만 지나는 길이다. */
function captureManualPlaces() {
  const seen: Record<string, unknown>[] = [];
  server.use(
    http.post(`${API_BASE}/travel/places`, async ({ request }) => {
      seen.push((await request.json()) as Record<string, unknown>);
      return HttpResponse.json({ code: "OK", data: { id: 77, name: "도쿄" } });
    }),
  );
  return seen;
}

function mockTripDetail(trip = TOKYO) {
  server.use(
    http.get(`${API_BASE}/travel/trips/:tripId`, () =>
      HttpResponse.json({ code: "OK", data: trip }),
    ),
  );
}

/** 수정 화면 초기값 — 서버가 날짜에서 파생한 구간. */
function mockCityLegs(legs: unknown[]) {
  server.use(
    http.get(`${API_BASE}/travel/trips/:tripId/city-legs`, () =>
      HttpResponse.json({ code: "OK", data: legs }),
    ),
  );
}

function mockShrinkPreview(preview: {
  movedActivityCount: number;
  shrunkStayCount?: number;
  removedStayCount?: number;
}) {
  server.use(
    http.get(`${API_BASE}/travel/trips/:tripId/shrink-preview`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          shrunkStayCount: 0,
          removedStayCount: 0,
          ...preview,
        },
      }),
    ),
  );
}

/** 구간 추가 → 시트에서 검색 → 첫 결과 선택. 이제 도시를 정하는 유일한 경로다. */
async function addLeg(cityName: string) {
  await userEvent.click(screen.getByRole("button", { name: "구간 추가" }));
  await userEvent.type(await screen.findByLabelText("도시 검색"), cityName);
  await userEvent.click(screen.getByRole("button", { name: "검색" }));
  await userEvent.click(
    await screen.findByRole("button", { name: RegExp(cityName) }),
  );
}

async function fillPeriod(start: string, end: string) {
  await userEvent.type(screen.getByLabelText("시작일"), start);
  await userEvent.type(screen.getByLabelText("종료일"), end);
}

describe("TripFormPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
    mockCities();
  });

  describe("생성", () => {
    it("명세 순서대로 필드를 보여준다 — 제목 · 기간 · 구간 · 타임존 안내 · 알림", async () => {
      renderApp("/travel/trips/new");

      await waitFor(() => {
        expect(
          screen.getByRole("heading", { name: "여행 만들기" }),
        ).toBeInTheDocument();
      });
      expect(screen.getByLabelText("여행 제목")).toBeInTheDocument();
      expect(screen.getByLabelText("시작일")).toBeInTheDocument();
      expect(screen.getByLabelText("종료일")).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: "구간 추가" }),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/타임존과 통화는 날짜마다 정해져요/),
      ).toBeInTheDocument();
      expect(screen.getByText("기본 알림 시점")).toBeInTheDocument();
    });

    it("제목은 필수다 — 목적지가 여행에 없으니 채울 이름도 없다", async () => {
      const seen = captureWrite("post");
      renderApp("/travel/trips/new");
      await screen.findByLabelText("여행 제목");

      await fillPeriod("2026-10-24", "2026-10-27");
      await addLeg("도쿄");
      await userEvent.click(screen.getByRole("button", { name: "만들기" }));

      expect(
        await screen.findByText("여행 제목을 입력해 주세요."),
      ).toBeInTheDocument();
      expect(seen).toHaveLength(0);
    });

    it("구간이 없으면 저장하지 않는다 — 어느 날짜도 기준 도시를 갖지 못한다", async () => {
      const seen = captureWrite("post");
      renderApp("/travel/trips/new");
      await screen.findByLabelText("여행 제목");

      await userEvent.type(screen.getByLabelText("여행 제목"), "일본");
      await fillPeriod("2026-10-24", "2026-10-27");
      await userEvent.click(screen.getByRole("button", { name: "만들기" }));

      expect(
        await screen.findByText("구간을 하나 이상 추가해 주세요."),
      ).toBeInTheDocument();
      expect(seen).toHaveLength(0);
    });

    it("고른 도시를 구간으로 보낸다 — 검색 결과를 그대로 실어 보낸다", async () => {
      const seen = captureWrite("post");
      renderApp("/travel/trips/new");
      await screen.findByLabelText("여행 제목");

      await userEvent.type(screen.getByLabelText("여행 제목"), "도쿄 3박4일");
      await fillPeriod("2026-10-24", "2026-10-27");
      await addLeg("도쿄");
      await userEvent.click(screen.getByRole("button", { name: "만들기" }));

      await waitFor(() => expect(seen).toHaveLength(1));
      expect(seen[0]).toMatchObject({
        title: "도쿄 3박4일",
        startDate: "2026-10-24",
        endDate: "2026-10-27",
        legs: [{ cityGooglePlaceId: "ChIJ_tokyo", days: 1 }],
        defaultNotifyMinutes: 15,
      });
      // 타임존·통화·좌표는 도시가 갖는다 — 여행이 따로 들고 있지 않는다.
      expect(seen[0]).not.toHaveProperty("timezone");
      expect(seen[0]).not.toHaveProperty("currency");
    });

    it("만들면 그 여행의 보드로 간다", async () => {
      captureWrite("post");
      renderApp("/travel/trips/new");
      await screen.findByLabelText("여행 제목");

      await userEvent.type(screen.getByLabelText("여행 제목"), "도쿄");
      await fillPeriod("2026-10-24", "2026-10-27");
      await addLeg("도쿄");
      await userEvent.click(screen.getByRole("button", { name: "만들기" }));

      await waitFor(() => {
        expect(screen.getByRole("tab", { name: /1일차/ })).toBeInTheDocument();
      });
    });

    it("종료일이 시작일보다 빠르면 저장하지 않고 알려준다", async () => {
      const seen = captureWrite("post");
      renderApp("/travel/trips/new");
      await screen.findByLabelText("여행 제목");

      await userEvent.type(screen.getByLabelText("여행 제목"), "도쿄");
      await fillPeriod("2026-10-27", "2026-10-24");
      await addLeg("도쿄");
      await userEvent.click(screen.getByRole("button", { name: "만들기" }));

      expect(
        await screen.findByText("종료일은 시작일보다 빠를 수 없습니다."),
      ).toBeInTheDocument();
      expect(seen).toHaveLength(0);
    });
  });

  describe("구간 편집", () => {
    it("일수를 늘리면 그 구간이 차지할 날짜가 즉시 바뀐다", async () => {
      renderApp("/travel/trips/new");
      await screen.findByLabelText("여행 제목");

      await fillPeriod("2026-10-24", "2026-10-27");
      await addLeg("도쿄");

      // 구간이 하나면 남은 날짜를 이어 쓰므로 전 기간을 차지한다.
      expect(await screen.findByText("10.24 – 10.27")).toBeInTheDocument();

      await userEvent.click(
        screen.getByRole("button", { name: /일수 늘리기/ }),
      );

      expect(screen.getByText("2일")).toBeInTheDocument();
    });

    it("구간을 둘 넣으면 순서대로 날짜를 나눠 갖는다", async () => {
      renderApp("/travel/trips/new");
      await screen.findByLabelText("여행 제목");

      await fillPeriod("2026-10-24", "2026-10-27");
      mockCities([TOKYO_CITY]);
      await addLeg("도쿄");
      await userEvent.click(
        screen.getByRole("button", { name: /도쿄 일수 늘리기/ }),
      );
      mockCities([KYOTO_CITY]);
      await addLeg("교토");

      expect(await screen.findByText("10.24 – 10.25")).toBeInTheDocument();
      expect(screen.getByText("10.26 – 10.27")).toBeInTheDocument();
    });

    it("합계가 기간과 다르면 무슨 일이 일어날지 미리 말한다 — 저장을 막지는 않는다", async () => {
      renderApp("/travel/trips/new");
      await screen.findByLabelText("여행 제목");

      await fillPeriod("2026-10-24", "2026-10-27");
      await addLeg("도쿄");

      // 합계 1일 / 기간 4일.
      expect(
        await screen.findByText(/3일 남음 · 마지막 구간 도시를 이어써요/),
      ).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "만들기" })).toBeEnabled();
    });

    it("합계가 기간을 넘으면 잘린다고 말하고, 잘린 구간은 날짜가 없다", async () => {
      renderApp("/travel/trips/new");
      await screen.findByLabelText("여행 제목");

      await fillPeriod("2026-10-24", "2026-10-25");
      mockCities([TOKYO_CITY]);
      await addLeg("도쿄");
      await userEvent.click(
        screen.getByRole("button", { name: /도쿄 일수 늘리기/ }),
      );
      mockCities([KYOTO_CITY]);
      await addLeg("교토");

      expect(
        await screen.findByText(/1일 초과 · 뒤 구간이 잘려요/),
      ).toBeInTheDocument();
      expect(screen.getByText("기간을 넘겨 잘려요")).toBeInTheDocument();
    });

    it("구간 순서를 바꾸면 날짜 배치가 다시 계산된다", async () => {
      const seen = captureWrite("post");
      renderApp("/travel/trips/new");
      await screen.findByLabelText("여행 제목");

      await userEvent.type(screen.getByLabelText("여행 제목"), "일본");
      await fillPeriod("2026-10-24", "2026-10-27");
      mockCities([TOKYO_CITY]);
      await addLeg("도쿄");
      mockCities([KYOTO_CITY]);
      await addLeg("교토");

      await userEvent.click(screen.getByRole("button", { name: "교토 위로" }));
      await userEvent.click(screen.getByRole("button", { name: "만들기" }));

      await waitFor(() => expect(seen).toHaveLength(1));
      expect(seen[0].legs).toEqual([
        { cityGooglePlaceId: "ChIJ_kyoto", days: 1 },
        { cityGooglePlaceId: "ChIJ_tokyo", days: 1 },
      ]);
    });

    it("구간이 하나뿐이면 지울 수 없다 — 도시 없는 여행은 만들 수 없다", async () => {
      renderApp("/travel/trips/new");
      await screen.findByLabelText("여행 제목");

      await addLeg("도쿄");

      expect(screen.getByRole("button", { name: "도쿄 삭제" })).toBeDisabled();
    });

    it("타임존이 둘이면 안내가 개수를 말한다", async () => {
      renderApp("/travel/trips/new");
      await screen.findByLabelText("여행 제목");

      mockCities([TOKYO_CITY]);
      await addLeg("도쿄");
      mockCities([HONOLULU_CITY]);
      await addLeg("호놀룰루");

      expect(await screen.findByText("타임존이 2개예요")).toBeInTheDocument();
      expect(
        screen.getByText(/Asia\/Tokyo \/ Pacific\/Honolulu/),
      ).toBeInTheDocument();
    });
  });

  describe("검색이 막혔을 때", () => {
    it("할당량에 걸리면 재시도가 아니라 직접 입력으로 안내한다 (#1159)", async () => {
      // "잠시 후 다시 시도해 주세요"는 캡에 걸린 사용자에게 틀린 조언이다.
      server.use(
        http.get(`${API_BASE}/travel/places/cities`, () =>
          HttpResponse.json({ code: "TRAVEL-ERR-021" }, { status: 503 }),
        ),
      );
      renderApp("/travel/trips/new");
      await screen.findByLabelText("여행 제목");

      await userEvent.click(screen.getByRole("button", { name: "구간 추가" }));
      await userEvent.type(await screen.findByLabelText("도시 검색"), "삿포로");
      await userEvent.click(screen.getByRole("button", { name: "검색" }));

      expect(
        await screen.findByText(/지금은 새 도시를 검색할 수 없어요/),
      ).toBeInTheDocument();
      // 여행 만들기가 외부 API에 걸려 막히면 안 된다 — 직접 입력은 구글을 부르지 않는다.
      expect(
        screen.getByRole("button", { name: "직접 입력하기" }),
      ).toBeInTheDocument();
    });

    it("직접 입력한 도시를 저장 직전에 만들어 붙인다", async () => {
      server.use(
        http.get(`${API_BASE}/travel/places/cities`, () =>
          HttpResponse.json({ code: "ERR" }, { status: 500 }),
        ),
      );
      const seen = captureWrite("post");
      const cities = captureManualPlaces();
      renderApp("/travel/trips/new");
      await screen.findByLabelText("여행 제목");

      await userEvent.type(screen.getByLabelText("여행 제목"), "삿포로 여행");
      await fillPeriod("2026-10-24", "2026-10-27");

      await userEvent.click(screen.getByRole("button", { name: "구간 추가" }));
      await userEvent.type(await screen.findByLabelText("도시 검색"), "삿포로");
      await userEvent.click(screen.getByRole("button", { name: "검색" }));
      await userEvent.click(
        await screen.findByRole("button", { name: "직접 입력하기" }),
      );
      await userEvent.type(screen.getByLabelText("도시 이름"), "삿포로");
      await userEvent.click(screen.getByRole("button", { name: "이 도시로" }));

      await userEvent.click(screen.getByRole("button", { name: "만들기" }));

      await waitFor(() => expect(seen).toHaveLength(1));
      // 검색으로 고른 도시가 아니면 id가 없다 — 저장 직전에 도시로 만들어 붙인다.
      expect(cities).toEqual([
        expect.objectContaining({ name: "삿포로", kind: "CITY" }),
      ]);
      expect(seen[0].legs).toEqual([{ cityPlaceId: 77, days: 1 }]);
    });

    it("직접 입력에서는 타임존·통화를 고를 수 있다 — 정해 줄 사람이 없다", async () => {
      mockCities([]);
      renderApp("/travel/trips/new");
      await screen.findByLabelText("여행 제목");

      await userEvent.click(screen.getByRole("button", { name: "구간 추가" }));
      await userEvent.type(
        await screen.findByLabelText("도시 검색"),
        "없는도시",
      );
      await userEvent.click(screen.getByRole("button", { name: "검색" }));
      await userEvent.click(
        await screen.findByRole("button", { name: "직접 입력하기" }),
      );

      expect(screen.getByLabelText("타임존")).toBeInTheDocument();
      expect(screen.getByLabelText("통화")).toBeInTheDocument();
    });
  });

  describe("수정", () => {
    it("저장된 구간으로 폼을 채운다 — 구간은 날짜에서 파생한 값이다", async () => {
      mockTripDetail();
      mockCityLegs([
        {
          legIndex: 1,
          cityPlaceId: 21,
          cityName: "도쿄",
          days: 2,
          startDate: "2026-10-24",
          endDate: "2026-10-25",
          timezone: "Asia/Tokyo",
          lat: null,
          lng: null,
        },
        {
          legIndex: 2,
          cityPlaceId: 22,
          cityName: "닛코",
          days: 2,
          startDate: "2026-10-26",
          endDate: "2026-10-27",
          timezone: "Asia/Tokyo",
          lat: null,
          lng: null,
        },
      ]);
      renderApp("/travel/trips/3/edit");

      expect(await screen.findByLabelText("여행 제목")).toHaveValue(
        "도쿄 3박 4일",
      );
      expect(await screen.findByText("10.24 – 10.25")).toBeInTheDocument();
      expect(screen.getByText("10.26 – 10.27")).toBeInTheDocument();
      expect(screen.getByText(/딱 맞아요/)).toBeInTheDocument();
    });

    it("저장하면 담긴 구간을 그대로 보낸다", async () => {
      mockTripDetail();
      mockCityLegs([
        {
          legIndex: 1,
          cityPlaceId: 21,
          cityName: "도쿄",
          days: 4,
          startDate: "2026-10-24",
          endDate: "2026-10-27",
          timezone: "Asia/Tokyo",
          lat: null,
          lng: null,
        },
      ]);
      const seen = captureWrite("put");
      renderApp("/travel/trips/3/edit");
      await screen.findByText("10.24 – 10.27");

      await userEvent.click(screen.getByRole("button", { name: "저장" }));

      await waitFor(() => expect(seen).toHaveLength(1));
      expect(seen[0].legs).toEqual([{ cityPlaceId: 21, days: 4 }]);
    });

    it("기간을 줄이면 무엇이 밀려나는지 숙소까지 말한다", async () => {
      mockTripDetail();
      mockCityLegs([]);
      mockShrinkPreview({
        movedActivityCount: 4,
        shrunkStayCount: 1,
        removedStayCount: 1,
      });
      captureWrite("put");
      renderApp("/travel/trips/3/edit");
      await screen.findByLabelText("종료일");

      await userEvent.clear(screen.getByLabelText("종료일"));
      await userEvent.type(screen.getByLabelText("종료일"), "2026-10-25");
      await userEvent.click(screen.getByRole("button", { name: "저장" }));

      const dialog = await screen.findByRole("dialog", {
        name: "기간을 줄이면 일정이 이동합니다",
      });
      expect(dialog).toHaveTextContent("일정 4개가 미배정 보관함으로 이동");
      expect(dialog).toHaveTextContent("숙소 1곳은 기간이 줄어듦");
      expect(dialog).toHaveTextContent("숙소 1곳은 삭제");
    });

    it("확인하면 confirmArchive를 실어 저장한다", async () => {
      mockTripDetail();
      mockCityLegs([]);
      mockShrinkPreview({ movedActivityCount: 2 });
      const seen = captureWrite("put");
      renderApp("/travel/trips/3/edit");
      await screen.findByLabelText("종료일");

      await userEvent.clear(screen.getByLabelText("종료일"));
      await userEvent.type(screen.getByLabelText("종료일"), "2026-10-25");
      await userEvent.click(screen.getByRole("button", { name: "저장" }));

      const dialog = await screen.findByRole("dialog", {
        name: "기간을 줄이면 일정이 이동합니다",
      });
      await userEvent.click(
        within(dialog).getByRole("button", { name: "이동하고 저장" }),
      );

      await waitFor(() => expect(seen).toHaveLength(1));
      expect(seen[0]).toMatchObject({ confirmArchive: true });
    });

    it("구간을 못 받아오면 legs를 보내지 않는다 — 도시 배치를 되감지 않는다", async () => {
      mockTripDetail();
      server.use(
        http.get(`${API_BASE}/travel/trips/:tripId/city-legs`, () =>
          HttpResponse.json({ code: "ERR" }, { status: 500 }),
        ),
      );
      const seen = captureWrite("put");
      renderApp("/travel/trips/3/edit");
      await screen.findByLabelText("여행 제목");

      await userEvent.click(screen.getByRole("button", { name: "저장" }));

      await waitFor(() => expect(seen).toHaveLength(1));
      expect(seen[0]).not.toHaveProperty("legs");
    });
  });
});
