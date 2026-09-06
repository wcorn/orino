import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { AppRouter } from "@/app/router";
import { useAuthStore } from "@/features/auth/store/authStore";
import type {
  PrepCategory,
  PrepItemView,
  PrepPatchRequest,
  PrepSectionOrder,
} from "@/features/travel/api/prep";
import { usePendingPrepActions } from "@/features/travel/board/pendingActions";
import { useToastStore } from "@/shared/lib/toast";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

const API_BASE = "https://api.orino.dev/api";
const TRIP_ID = 12;
const CATEGORIES: PrepCategory[] = ["DOCUMENT", "BOOKING", "BAG", "TODO"];

interface StoredItem extends PrepItemView {
  category: PrepCategory;
}

function item(partial: Partial<StoredItem> & { id: number; title: string }) {
  return {
    category: "BAG" as PrepCategory,
    done: false,
    sectionLabel: null,
    quantity: null,
    dueDaysBefore: null,
    dueDate: null,
    overdue: false,
    url: null,
    memo: null,
    displayOrder: 0,
    ...partial,
  } satisfies StoredItem;
}

/** 저장본에서 서버 응답 모양만 남긴다 — `category`는 항목이 아니라 그룹이 갖는다. */
function view(stored: StoredItem): PrepItemView {
  return {
    id: stored.id,
    title: stored.title,
    done: stored.done,
    sectionLabel: stored.sectionLabel,
    quantity: stored.quantity,
    dueDaysBefore: stored.dueDaysBefore,
    dueDate: stored.dueDate,
    overdue: stored.overdue,
    url: stored.url,
    memo: stored.memo,
    displayOrder: stored.displayOrder,
  };
}

/**
 * 분류 안을 묶음으로 나눈다 — 서버가 하는 일을 같은 규칙으로 흉내 낸다(#1358).
 *
 * <p>묶음의 자리는 그 안의 최소 `displayOrder`이고, 「묶음 없음」은 언제나 맨 앞이다.
 * 여기서 규칙이 갈리면 화면이 정렬을 스스로 하고 있는지 아닌지를 테스트가 못 잡는다.
 */
function sectionsOf(own: StoredItem[]) {
  const sorted = [...own].sort((a, b) => a.displayOrder - b.displayOrder);
  const labels: (string | null)[] = [];
  sorted.forEach((i) => {
    if (!labels.includes(i.sectionLabel)) labels.push(i.sectionLabel);
  });
  // 「묶음 없음」만 앞으로 뽑는다. 비교 함수로 정렬하면 나머지 차례까지 흔들린다.
  const ordered = labels.includes(null)
    ? [null, ...labels.filter((label) => label !== null)]
    : labels;
  return ordered.map((label) => {
    const inSection = sorted.filter((i) => i.sectionLabel === label);
    return {
      label,
      total: inSection.length,
      done: inSection.filter((i) => i.done).length,
      items: inSection.map(view),
    };
  });
}

/**
 * 서버를 상태로 흉내 낸다. 준비 화면은 <b>한 번 적고 끝</b>이 아니라 계속 고치는 화면이라,
 * 응답을 고정해 두면 「추가했는데 목록에 없다」 같은 실제 흐름을 못 잡는다.
 */
function mockPrep(initial: StoredItem[] = []) {
  const items = [...initial];
  let nextId = 100;
  const requests = {
    deleted: [] as number[],
    patched: [] as PrepPatchRequest[],
    /** 순서 바꾸기로 나간 배치(#1364). */
    ordered: [] as { category: PrepCategory; sections: PrepSectionOrder[] }[],
  };

  const summary = () => ({
    total: items.length,
    done: items.filter((i) => i.done).length,
    // 기한 지남은 서버가 「첫날 기준 도시의 오늘」로 판정한다. 화면은 세지 않는다.
    overdueCount: items.filter((i) => i.overdue && !i.done).length,
  });

  server.use(
    http.get(`${API_BASE}/travel/trips/${TRIP_ID}/prep`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          tripId: TRIP_ID,
          startDate: "2026-10-24",
          dday: 49,
          ...summary(),
          groups: CATEGORIES.map((category) => {
            const own = items.filter((i) => i.category === category);
            return {
              category,
              total: own.length,
              done: own.filter((i) => i.done).length,
              sections: sectionsOf(own),
            };
          }),
        },
      }),
    ),

    http.post(
      `${API_BASE}/travel/trips/${TRIP_ID}/prep/items`,
      async ({ request }) => {
        const body = (await request.json()) as {
          category?: PrepCategory;
          title: string;
          sectionLabel?: string;
        };
        const category = body.category ?? "TODO";
        const stored = item({
          id: nextId++,
          title: body.title,
          category,
          sectionLabel: body.sectionLabel ?? null,
          displayOrder: items.filter((i) => i.category === category).length,
        });
        items.push(stored);
        return HttpResponse.json({
          code: "OK",
          data: { category, item: view(stored), summary: summary() },
        });
      },
    ),

    http.patch(
      `${API_BASE}/travel/prep/items/:itemId`,
      async ({ params, request }) => {
        const body = (await request.json()) as PrepPatchRequest;
        requests.patched.push(body);
        const stored = items.find((i) => i.id === Number(params.itemId))!;
        if (body.done !== undefined) stored.done = body.done;
        if (body.title !== undefined) stored.title = body.title;
        if (body.category !== undefined) stored.category = body.category;
        if (body.dueDaysBefore !== undefined) {
          stored.dueDaysBefore = body.dueDaysBefore;
        }
        if (body.quantity !== undefined) stored.quantity = body.quantity;
        // 묶음을 옮기면 그 묶음의 맨 뒤다 — 서버와 같은 규칙이라야 화면 순서를 볼 수 있다.
        if (body.sectionLabel !== undefined) {
          stored.sectionLabel = body.sectionLabel;
          stored.displayOrder =
            Math.max(
              ...items
                .filter((i) => i.category === stored.category)
                .map((i) => i.displayOrder),
            ) + 1;
        }
        if (body.clear?.includes("SECTION_LABEL")) stored.sectionLabel = null;
        if (body.clear?.includes("DUE_DAYS_BEFORE")) {
          stored.dueDaysBefore = null;
          stored.dueDate = null;
          stored.overdue = false;
        }
        if (body.clear?.includes("QUANTITY")) stored.quantity = null;
        return HttpResponse.json({
          code: "OK",
          data: {
            category: stored.category,
            item: view(stored),
            summary: summary(),
          },
        });
      },
    ),

    // 사이드바 배지가 읽는 요약. 같은 저장소에서 센다 — 두 값이 갈리면
    // 배지와 화면이 다른 말을 하게 되고, 그게 이 화면이 막으려는 상태다.
    http.get(`${API_BASE}/travel/summary`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          ongoing: null,
          next: {
            id: TRIP_ID,
            title: "일본 가을",
            prepPath: `/travel/trips/${TRIP_ID}/prep`,
            prep: summary(),
          },
          recentCompleted: null,
          // 사이드바 트리는 이 배열을 읽는다(#1346). 배지도 여기서 나온다.
          trips: [
            {
              id: TRIP_ID,
              title: "일본 가을",
              status: "UPCOMING",
              startDate: "2026-10-24",
              endDate: "2026-10-27",
              dDay: 49,
              dayNumber: null,
              prep: summary(),
              expense: { budget: null, spent: 0 },
            },
          ],
          completedCount: 0,
        },
      }),
    ),

    // 순서 바꾸기(#1364). 화면이 보낸 배치가 곧 결과다 — 서버와 같은 규칙으로 저장한다.
    http.put(
      `${API_BASE}/travel/trips/${TRIP_ID}/prep/order`,
      async ({ request }) => {
        const body = (await request.json()) as {
          category: PrepCategory;
          sections: PrepSectionOrder[];
        };
        requests.ordered.push(body);
        let order = 0;
        body.sections.forEach((section) =>
          section.itemIds.forEach((id) => {
            const stored = items.find((i) => i.id === id)!;
            stored.sectionLabel = section.label;
            stored.displayOrder = order++;
          }),
        );
        return HttpResponse.json({ code: "OK", data: null });
      },
    ),

    http.delete(`${API_BASE}/travel/prep/items/:itemId`, ({ params }) => {
      const id = Number(params.itemId);
      requests.deleted.push(id);
      const index = items.findIndex((i) => i.id === id);
      if (index >= 0) items.splice(index, 1);
      return HttpResponse.json({ code: "OK", data: null });
    }),
  );

  return requests;
}

/** 마지막 요청. `Array.prototype.at`은 테스트 tsconfig의 lib보다 새 문법이다. */
function lastOf<T>(list: T[]): T | undefined {
  return list[list.length - 1];
}

function renderPrep() {
  return renderWithRouter(
    <Providers>
      <AppRouter />
    </Providers>,
    { initialEntries: [`/travel/trips/${TRIP_ID}/prep`] },
  );
}

/** 「짐」 카드. 분류가 넷이라 이름만으로 찾으면 다른 카드의 줄을 집는다. */
function bagCard() {
  return screen.getByRole("button", { name: /^짐 / }).closest("section")!;
}

describe("TripPrepPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "valid-token" });
    useToastStore.setState({ toasts: [] });
    usePendingPrepActions.setState({ pendingIds: [], commits: new Map() });
    server.use(
      http.get(`${API_BASE}/travel/summary`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            ongoing: null,
            next: null,
            recentCompleted: null,
            trips: [],
            completedCount: 0,
          },
        }),
      ),
    );
  });

  /**
   * 화면이 스스로 스코프를 말한다(#1348 · 화면 §10.8). 모바일에서는 사이드바가 닫혀 있어,
   * 이 두 줄이 없으면 어느 여행의 준비인지가 화면 어디에도 없다.
   */
  describe("스코프", () => {
    it("브레드크럼이 여행 이름을 말하고, 그 이름이 그 여행의 보드로 간다", async () => {
      mockPrep();
      renderPrep();

      const crumb = within(
        await screen.findByRole("navigation", { name: "현재 위치" }),
      );
      expect(crumb.getByRole("link", { name: "여행" })).toHaveAttribute(
        "href",
        "/travel/trips",
      );
      // 이름을 눌렀을 때 가고 싶은 곳은 목록이 아니라 그 여행이다.
      expect(crumb.getByRole("link", { name: "일본 가을" })).toHaveAttribute(
        "href",
        `/travel/trips/${TRIP_ID}/board`,
      );
      expect(crumb.getByText("준비")).toBeVisible();
    });

    it("준비가 여행마다 따로라는 사실을 화면이 말해 준다", async () => {
      mockPrep();
      renderPrep();

      // 템플릿·「지난 여행에서 가져오기」를 안 만들기로 한 결정(§15)이 이 줄을 필요하게
      // 만든다 — 이유가 없으면 빈 목록이 버그로 읽힌다(D-40).
      expect(
        await screen.findByText(/준비 목록은 여행마다 따로입니다/),
      ).toBeVisible();
    });
  });

  it("항목이 없어도 분류 카드 넷이 다 보인다 — 화면이 분류 목록을 따로 들지 않는다", async () => {
    mockPrep();
    renderPrep();

    expect(await screen.findByRole("button", { name: /^서류 / })).toBeVisible();
    expect(screen.getByRole("button", { name: /^예약 / })).toBeVisible();
    expect(screen.getByRole("button", { name: /^짐 / })).toBeVisible();
    expect(screen.getByRole("button", { name: /^할 일 / })).toBeVisible();
  });

  it("엔터를 치면 입력만 비워지고 포커스가 남아 계속 이어 칠 수 있다", async () => {
    mockPrep();
    const user = userEvent.setup();
    renderPrep();

    const input = await screen.findByLabelText("준비 항목 추가");
    await user.type(input, "멀티어댑터{Enter}");

    // 시트가 열리고 닫히지 않는다. 입력만 비워지고 손은 그대로다.
    await waitFor(() => expect(input).toHaveValue(""));
    expect(input).toHaveFocus();
    expect(await screen.findByText("멀티어댑터")).toBeVisible();

    await user.type(input, "양말{Enter}");
    expect(await screen.findByText("양말")).toBeVisible();
    expect(input).toHaveFocus();
  });

  it("적은 분류를 이어받는다 — 한 번 짐으로 바꾸면 다음 줄도 짐이다", async () => {
    mockPrep();
    const user = userEvent.setup();
    renderPrep();

    await user.click(await screen.findByLabelText(/추가할 분류/));
    await user.click(await screen.findByRole("menuitem", { name: "짐" }));

    const input = screen.getByLabelText("준비 항목 추가");
    await user.type(input, "멀티어댑터{Enter}");
    await user.type(input, "양말{Enter}");

    await waitFor(() => {
      expect(within(bagCard()).getByText("양말")).toBeVisible();
    });
    expect(within(bagCard()).getByText("멀티어댑터")).toBeVisible();
  });

  it("체크해도 자리를 옮기지 않는다 — 어디까지 했는지 잃지 않게", async () => {
    mockPrep([
      item({ id: 1, title: "멀티어댑터", displayOrder: 0 }),
      item({ id: 2, title: "양말", displayOrder: 1 }),
      item({ id: 3, title: "상비약", displayOrder: 2 }),
    ]);
    const user = userEvent.setup();
    renderPrep();

    await user.click(
      await screen.findByRole("checkbox", { name: "멀티어댑터" }),
    );

    await waitFor(() => {
      expect(
        screen.getByRole("checkbox", { name: "멀티어댑터" }),
      ).toBeChecked();
    });
    // 체크한 줄이 맨 아래로 내려가지 않는다.
    const titles = within(bagCard())
      .getAllByRole("listitem")
      .map((li) => li.textContent);
    expect(titles[0]).toContain("멀티어댑터");
    expect(titles[1]).toContain("양말");
    expect(titles[2]).toContain("상비약");
  });

  it("완료 숨기기는 목록에서만 빼고 진행률은 그대로 둔다", async () => {
    mockPrep([
      item({ id: 1, title: "멀티어댑터", done: true }),
      item({ id: 2, title: "양말" }),
    ]);
    const user = userEvent.setup();
    renderPrep();

    expect(await screen.findByLabelText("2개 중 1개 완료")).toBeVisible();

    await user.click(screen.getByRole("switch", { name: "완료 숨기기" }));

    await waitFor(() => {
      expect(screen.queryByText("멀티어댑터")).not.toBeInTheDocument();
    });
    expect(screen.getByText("양말")).toBeVisible();
    // 숨기는 것과 진행률은 다른 일이다 — 1/2가 2/2가 되면 다 끝난 줄 안다.
    expect(screen.getByLabelText("2개 중 1개 완료")).toBeVisible();
    expect(screen.getByRole("button", { name: "짐 1/2" })).toBeVisible();
  });

  it("기한 지난 개수는 서버가 준 값을 그대로 쓴다", async () => {
    mockPrep([
      item({
        id: 1,
        title: "여권 갱신",
        category: "BOOKING",
        dueDaysBefore: 20,
        dueDate: "2026-10-04",
        overdue: true,
      }),
    ]);
    renderPrep();

    expect(await screen.findByText("기한 지난 것 1개")).toBeVisible();
    expect(screen.getByText("D-20")).toBeVisible();
  });

  it("삭제는 5초 안에 되돌리면 요청이 나가지 않는다", async () => {
    const requests = mockPrep([item({ id: 1, title: "멀티어댑터" })]);
    const user = userEvent.setup();
    renderPrep();

    await user.click(await screen.findByLabelText("멀티어댑터 삭제"));

    // 화면에서는 곧바로 사라진다 — 실제 요청은 아직 나가지 않았다.
    await waitFor(() => {
      expect(screen.queryByText("멀티어댑터")).not.toBeInTheDocument();
    });
    expect(requests.deleted).toEqual([]);

    await user.click(await screen.findByRole("button", { name: /실행취소/ }));

    expect(requests.deleted).toEqual([]);
    expect(usePendingPrepActions.getState().pendingIds).toEqual([]);
    expect(await screen.findByText("멀티어댑터")).toBeVisible();
  });

  it("기한은 「출발 N일 전」 숫자 하나로 저장한다 — 날짜 칸이 없다", async () => {
    const requests = mockPrep([
      item({ id: 1, title: "숙소 잔금 결제", category: "BOOKING" }),
    ]);
    const user = userEvent.setup();
    renderPrep();

    await user.click(
      await screen.findByRole("button", { name: "숙소 잔금 결제" }),
    );

    // 날짜를 고르는 자리는 어디에도 없다(§12).
    expect(screen.queryByLabelText(/날짜/)).not.toBeInTheDocument();
    await user.type(screen.getByLabelText("기한 (출발 며칠 전)"), "14");
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(lastOf(requests.patched)).toMatchObject({ dueDaysBefore: 14 });
    });
  });

  it("기한을 비우면 「지워 달라」고 적어 보낸다 — 안 보낸 것과 다르다", async () => {
    const requests = mockPrep([
      item({
        id: 1,
        title: "숙소 잔금 결제",
        category: "BOOKING",
        dueDaysBefore: 14,
        dueDate: "2026-10-10",
      }),
    ]);
    const user = userEvent.setup();
    renderPrep();

    await user.click(
      await screen.findByRole("button", { name: "숙소 잔금 결제" }),
    );
    await user.clear(screen.getByLabelText("기한 (출발 며칠 전)"));
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => {
      expect(lastOf(requests.patched)?.clear).toContain("DUE_DAYS_BEFORE");
    });
    expect(lastOf(requests.patched)?.dueDaysBefore).toBeUndefined();
  });

  it("수량은 짐에서만 적을 수 있다", async () => {
    mockPrep([item({ id: 1, title: "환전", category: "TODO" })]);
    const user = userEvent.setup();
    renderPrep();

    await user.click(await screen.findByRole("button", { name: /^할 일 / }));
    await user.click(await screen.findByRole("button", { name: "환전" }));

    expect(screen.getByLabelText("수량")).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "짐" }));
    expect(screen.getByLabelText("수량")).toBeEnabled();
  });

  it("기한 지난 항목을 체크하면 사이드바 배지도 함께 사라진다", async () => {
    mockPrep([
      item({
        id: 1,
        title: "여권 갱신",
        category: "BOOKING",
        dueDaysBefore: 20,
        dueDate: "2026-10-04",
        overdue: true,
      }),
    ]);
    const user = userEvent.setup();
    renderPrep();

    // 배지와 화면 상단이 같은 값을 읽는지가 요지다(§13).
    expect(await screen.findByLabelText("기한 지난 것 1개")).toBeVisible();
    expect(screen.getByText("기한 지난 것 1개")).toBeVisible();

    await user.click(screen.getByRole("checkbox", { name: "여권 갱신" }));

    await waitFor(() => {
      expect(
        screen.queryByLabelText("기한 지난 것 1개"),
      ).not.toBeInTheDocument();
    });
    expect(screen.queryByText("기한 지난 것 1개")).not.toBeInTheDocument();
  });

  /**
   * 묶음(#1358). 짐이 수십 줄이 되면서 분류 하나가 한 화면에 안 들어간다 — 분류를 늘리는
   * 대신 그 안에 사용자가 이름 붙이는 겹을 하나 두었다.
   */
  describe("묶음", () => {
    it("아무도 묶지 않았으면 소제목이 없다 — 묶기 전과 같은 화면이다", async () => {
      mockPrep([
        item({ id: 1, title: "멀티어댑터" }),
        item({ id: 2, title: "양말", displayOrder: 1 }),
      ]);
      renderPrep();

      expect(await screen.findByText("멀티어댑터")).toBeVisible();
      /*
        안 쓰는 사람에게 「묶음 없음」 한 줄이 늘어나면 그건 기능이 아니라 군더더기다.
        입력줄의 묶음 버튼은 남는다 — 첫 묶음을 만들 자리가 거기뿐이라, 목록에서 빼는 것과
        만들 길을 없애는 것은 다른 일이다.
      */
      expect(
        within(bagCard()).queryByText("묶음 없음"),
      ).not.toBeInTheDocument();
    });

    it("묶음이 하나라도 생기면 소제목이 뜨고, 묶음 없음이 맨 위다", async () => {
      mockPrep([
        item({ id: 1, title: "충전기", sectionLabel: "캐리어" }),
        item({ id: 2, title: "옷", sectionLabel: "캐리어", displayOrder: 1 }),
        // 나중에 적혔어도 묶음 없음은 맨 위다 — 아무것도 안 나눈 목록이 가운데로 밀리면
        // 묶음을 만든 대가를 안 만든 항목이 치른다.
        item({ id: 3, title: "여권 지갑", displayOrder: 2 }),
      ]);
      renderPrep();

      await screen.findByRole("button", { name: "짐 0/3" });
      const headers = within(bagCard())
        .getAllByRole("button", { expanded: true })
        .map((button) => button.getAttribute("aria-label"));
      expect(headers).toEqual(["짐 0/3", "묶음 없음 0/1", "캐리어 0/2"]);
    });

    it("묶음을 접으면 그 안의 줄만 사라진다 — 개수는 그대로다", async () => {
      mockPrep([
        item({ id: 1, title: "충전기", sectionLabel: "캐리어" }),
        item({ id: 2, title: "여권 지갑", displayOrder: 1 }),
      ]);
      const user = userEvent.setup();
      renderPrep();

      await user.click(
        await screen.findByRole("button", { name: "캐리어 0/1" }),
      );

      await waitFor(() => {
        expect(screen.queryByText("충전기")).not.toBeInTheDocument();
      });
      // 접은 것과 진행률은 다른 일이다. 접었다고 0/1이 사라지면 뭐가 남았는지 잃는다.
      expect(screen.getByRole("button", { name: "캐리어 0/1" })).toBeVisible();
      expect(screen.getByText("여권 지갑")).toBeVisible();
    });

    it("새 묶음을 만들면 다음 줄도 그 묶음으로 이어서 적힌다", async () => {
      mockPrep();
      const user = userEvent.setup();
      renderPrep();

      await user.click(await screen.findByLabelText(/추가할 분류/));
      await user.click(await screen.findByRole("menuitem", { name: "짐" }));

      await user.click(screen.getByLabelText(/추가할 묶음/));
      await user.click(
        await screen.findByRole("menuitem", { name: "새 묶음…" }),
      );
      await user.type(
        await screen.findByLabelText("새 묶음 이름"),
        "캐리어{Enter}",
      );

      const input = screen.getByLabelText("준비 항목 추가");
      await user.type(input, "충전기{Enter}");
      await user.type(input, "옷{Enter}");

      // 줄마다 묶음을 다시 고르게 하면 묶는 것이 적는 것보다 오래 걸린다.
      await waitFor(() => {
        expect(
          within(bagCard()).getByRole("button", { name: "캐리어 0/2" }),
        ).toBeVisible();
      });
      expect(screen.getByLabelText("추가할 묶음: 캐리어")).toBeVisible();
    });

    it("분류를 바꾸면 묶음은 따라가지 않는다", async () => {
      mockPrep([item({ id: 1, title: "충전기", sectionLabel: "캐리어" })]);
      const user = userEvent.setup();
      renderPrep();

      await user.click(await screen.findByLabelText(/추가할 분류/));
      await user.click(await screen.findByRole("menuitem", { name: "짐" }));
      await user.click(screen.getByLabelText(/추가할 묶음/));
      await user.click(await screen.findByRole("menuitem", { name: "캐리어" }));
      expect(screen.getByLabelText("추가할 묶음: 캐리어")).toBeVisible();

      await user.click(screen.getByLabelText(/추가할 분류/));
      await user.click(await screen.findByRole("menuitem", { name: "할 일" }));

      // 짐에만 있던 이름을 들고 넘어가면, 할 일에 「캐리어」가 하나 더 생긴다.
      expect(screen.getByLabelText("추가할 묶음: 묶음 없음")).toBeVisible();
    });

    it("시트에서 묶음을 적으면 그 묶음으로 옮겨 간다", async () => {
      const requests = mockPrep([
        item({ id: 1, title: "충전기", sectionLabel: "캐리어" }),
        item({ id: 2, title: "이어폰", displayOrder: 1 }),
      ]);
      const user = userEvent.setup();
      renderPrep();

      await user.click(await screen.findByRole("button", { name: "이어폰" }));
      await user.click(
        screen.getByRole("button", { name: "묶음 캐리어(으)로" }),
      );
      await user.click(screen.getByRole("button", { name: "저장" }));

      await waitFor(() => {
        expect(lastOf(requests.patched)).toMatchObject({
          sectionLabel: "캐리어",
        });
      });
      // 옮긴 항목은 그 묶음의 맨 뒤다 — 한가운데 나타나면 방금 누른 줄을 다시 찾아야 한다.
      await waitFor(() => {
        expect(
          within(bagCard()).getByRole("button", { name: "캐리어 0/2" }),
        ).toBeVisible();
      });
      const rows = within(bagCard())
        .getAllByRole("listitem")
        .map((li) => li.textContent);
      expect(rows[0]).toContain("충전기");
      expect(rows[1]).toContain("이어폰");
    });

    it("묶음을 비우면 「지워 달라」고 적어 보낸다 — 안 보낸 것과 다르다", async () => {
      const requests = mockPrep([
        item({ id: 1, title: "충전기", sectionLabel: "캐리어" }),
      ]);
      const user = userEvent.setup();
      renderPrep();

      await user.click(await screen.findByRole("button", { name: "충전기" }));
      await user.clear(screen.getByLabelText("묶음"));
      await user.click(screen.getByRole("button", { name: "저장" }));

      await waitFor(() => {
        expect(lastOf(requests.patched)?.clear).toContain("SECTION_LABEL");
      });
      expect(lastOf(requests.patched)?.sectionLabel).toBeUndefined();
    });
  });

  /**
   * 옛 모양 응답(#1361). 서비스워커가 `/api/travel/*`를 캐시하므로, 묶음이 없던 시절의
   * 응답이 기내에서 그대로 꺼내질 수 있다 — 그때 흰 화면이 되면 「기내에서 목록만 확인한다」가
   * 통째로 무너진다.
   */
  it("묶음이 없던 응답도 읽는다 — 캐시에 남은 옛 모양", async () => {
    server.use(
      http.get(`${API_BASE}/travel/trips/${TRIP_ID}/prep`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            tripId: TRIP_ID,
            startDate: "2026-10-24",
            dday: 49,
            total: 2,
            done: 1,
            overdueCount: 0,
            // `sections`도 `sectionLabel`도 없던 시절의 응답 그대로다.
            groups: CATEGORIES.map((category) => ({
              category,
              total: category === "BAG" ? 2 : 0,
              done: category === "BAG" ? 1 : 0,
              items:
                category === "BAG"
                  ? [
                      {
                        id: 1,
                        title: "멀티어댑터",
                        done: true,
                        quantity: null,
                        dueDaysBefore: null,
                        dueDate: null,
                        overdue: false,
                        url: null,
                        memo: null,
                        displayOrder: 0,
                      },
                      {
                        id: 2,
                        title: "양말",
                        done: false,
                        quantity: null,
                        dueDaysBefore: null,
                        dueDate: null,
                        overdue: false,
                        url: null,
                        memo: null,
                        displayOrder: 1,
                      },
                    ]
                  : [],
            })),
          },
        }),
      ),
    );
    renderPrep();

    expect(await screen.findByText("멀티어댑터")).toBeVisible();
    expect(screen.getByText("양말")).toBeVisible();
    expect(screen.getByRole("button", { name: "짐 1/2" })).toBeVisible();
    // 「묶음 없음」 하나로 감싸지므로 소제목은 뜨지 않는다 — 캐시를 읽었다는 사실이 안 보인다.
    expect(within(bagCard()).queryByText("묶음 없음")).not.toBeInTheDocument();
  });

  /**
   * 순서 바꾸기(#1364). 드래그 자체는 jsdom에서 검증되지 않는다 — 좌표·충돌 판정이 전부
   * 실제 레이아웃에 달려 있어 E2E가 본다. 여기서는 <b>끌기의 대안</b>인 위/아래 버튼으로
   * 같은 계산을 확인한다(그 둘은 같은 함수를 쓴다).
   */
  describe("순서 바꾸기", () => {
    /** 주 입력 장치를 마우스로 바꾼다. 폭이 아니라 입력 장치가 기준이다. */
    function setFinePointer() {
      const original = window.matchMedia;
      window.matchMedia = ((query: string) => ({
        matches: query.includes("pointer: fine"),
        media: query,
        onchange: null,
        addEventListener: () => {},
        removeEventListener: () => {},
        addListener: () => {},
        removeListener: () => {},
        dispatchEvent: () => false,
      })) as unknown as typeof window.matchMedia;
      return () => {
        window.matchMedia = original;
      };
    }

    it("마우스면 길게 누르지 않아도 손잡이와 이동 버튼이 있다", async () => {
      const restore = setFinePointer();
      try {
        mockPrep([
          item({ id: 1, title: "멀티어댑터" }),
          item({ id: 2, title: "양말", displayOrder: 1 }),
        ]);
        renderPrep();

        expect(
          await screen.findByRole("button", { name: "멀티어댑터 순서 바꾸기" }),
        ).toBeInTheDocument();
        expect(
          screen.getByRole("button", { name: "멀티어댑터 아래로" }),
        ).toBeEnabled();
        // 첫 줄은 더 올라갈 곳이 없다.
        expect(
          screen.getByRole("button", { name: "멀티어댑터 위로" }),
        ).toBeDisabled();
        // 데스크톱에는 들어갈 모드가 없다.
        expect(screen.queryByText(/드래그 모드/)).not.toBeInTheDocument();
      } finally {
        restore();
      }
    });

    it("아래로 누르면 그 분류의 배치가 통째로 간다", async () => {
      const restore = setFinePointer();
      try {
        const requests = mockPrep([
          item({ id: 1, title: "멀티어댑터" }),
          item({ id: 2, title: "양말", displayOrder: 1 }),
          item({ id: 3, title: "상비약", displayOrder: 2 }),
        ]);
        const user = userEvent.setup();
        renderPrep();

        await user.click(
          await screen.findByRole("button", { name: "멀티어댑터 아래로" }),
        );

        await waitFor(() => {
          expect(lastOf(requests.ordered)).toEqual({
            category: "BAG",
            sections: [{ label: null, itemIds: [2, 1, 3] }],
          });
        });
        // 낙관적 반영 — 왕복을 기다리지 않고 그 자리에서 순서가 바뀐다.
        const titles = within(bagCard())
          .getAllByRole("listitem")
          .map((li) => li.textContent);
        expect(titles[0]).toContain("양말");
        expect(titles[1]).toContain("멀티어댑터");
      } finally {
        restore();
      }
    });

    it("묶음의 마지막 줄에서 한 번 더 누르면 다음 묶음으로 넘어간다", async () => {
      const restore = setFinePointer();
      try {
        const requests = mockPrep([
          item({ id: 1, title: "충전기", sectionLabel: "캐리어" }),
          item({
            id: 2,
            title: "칫솔",
            sectionLabel: "세면백",
            displayOrder: 1,
          }),
        ]);
        const user = userEvent.setup();
        renderPrep();

        await user.click(
          await screen.findByRole("button", { name: "충전기 아래로" }),
        );

        // 순서와 묶음이 한 요청으로 나간다 — 옮기고 나서 정렬하는 두 걸음이 아니다.
        await waitFor(() => {
          expect(lastOf(requests.ordered)).toEqual({
            category: "BAG",
            sections: [{ label: "세면백", itemIds: [2, 1] }],
          });
        });
        expect(
          await within(bagCard()).findByRole("button", { name: "세면백 0/2" }),
        ).toBeVisible();
      } finally {
        restore();
      }
    });

    it("소제목에도 손잡이와 이동 버튼이 있다 — 묶음 없음에는 없다", async () => {
      const restore = setFinePointer();
      try {
        mockPrep([
          item({ id: 1, title: "여권 지갑" }),
          item({
            id: 2,
            title: "충전기",
            sectionLabel: "캐리어",
            displayOrder: 1,
          }),
          item({
            id: 3,
            title: "칫솔",
            sectionLabel: "세면백",
            displayOrder: 2,
          }),
        ]);
        renderPrep();

        expect(
          await screen.findByRole("button", {
            name: "캐리어 묶음 순서 바꾸기",
          }),
        ).toBeInTheDocument();
        // 첫 묶음은 더 올라갈 곳이 없다.
        expect(
          screen.getByRole("button", { name: "캐리어 묶음 위로" }),
        ).toBeDisabled();
        expect(
          screen.getByRole("button", { name: "세면백 묶음 위로" }),
        ).toBeEnabled();

        // 「묶음 없음」은 자리가 고정이다(#1358) — 끌 손잡이를 주지 않는다.
        expect(
          screen.queryByRole("button", { name: "묶음 없음 묶음 순서 바꾸기" }),
        ).not.toBeInTheDocument();
        expect(
          screen.queryByRole("button", { name: "묶음 없음 묶음 아래로" }),
        ).not.toBeInTheDocument();
      } finally {
        restore();
      }
    });

    it("묶음을 아래로 내리면 그 안의 항목이 통째로 따라간다", async () => {
      const restore = setFinePointer();
      try {
        const requests = mockPrep([
          item({ id: 1, title: "충전기", sectionLabel: "캐리어" }),
          item({ id: 2, title: "옷", sectionLabel: "캐리어", displayOrder: 1 }),
          item({
            id: 3,
            title: "칫솔",
            sectionLabel: "세면백",
            displayOrder: 2,
          }),
        ]);
        const user = userEvent.setup();
        renderPrep();

        await user.click(
          await screen.findByRole("button", { name: "캐리어 묶음 아래로" }),
        );

        await waitFor(() => {
          expect(lastOf(requests.ordered)).toEqual({
            category: "BAG",
            sections: [
              { label: "세면백", itemIds: [3] },
              { label: "캐리어", itemIds: [1, 2] },
            ],
          });
        });
        // 낙관적 반영 — 소제목 차례가 그 자리에서 바뀐다.
        const headers = within(bagCard())
          .getAllByRole("button", { expanded: true })
          .map((button) => button.getAttribute("aria-label"));
        expect(headers).toEqual(["짐 0/3", "세면백 0/1", "캐리어 0/2"]);
      } finally {
        restore();
      }
    });

    it("손가락은 길게 눌러 모드로 들어간다 — 그 전에는 이동 버튼이 없다", async () => {
      mockPrep([
        item({ id: 1, title: "멀티어댑터" }),
        item({ id: 2, title: "양말", displayOrder: 1 }),
      ]);
      const user = userEvent.setup();
      renderPrep();

      const row = await screen.findByText("멀티어댑터");
      expect(
        screen.queryByRole("button", { name: "멀티어댑터 아래로" }),
      ).not.toBeInTheDocument();

      // 400ms 길게 누른다. 스크롤하려던 손짓과 구분되는 최소한의 길이다.
      await user.pointer({ keys: "[TouchA>]", target: row });
      await new Promise((resolve) => setTimeout(resolve, 450));
      await user.pointer({ keys: "[/TouchA]", target: row });

      expect(await screen.findByText(/드래그 모드/)).toBeVisible();
      expect(
        screen.getByRole("button", { name: "멀티어댑터 아래로" }),
      ).toBeEnabled();

      await user.click(screen.getByRole("button", { name: "완료" }));
      await waitFor(() => {
        expect(screen.queryByText(/드래그 모드/)).not.toBeInTheDocument();
      });
    });
  });

  it("오프라인이면 체크도 입력도 막는다 — 큐잉하지 않는다", async () => {
    mockPrep([item({ id: 1, title: "멀티어댑터" })]);
    renderPrep();
    await screen.findByText("멀티어댑터");

    Object.defineProperty(navigator, "onLine", {
      configurable: true,
      value: false,
    });
    window.dispatchEvent(new Event("offline"));

    expect(
      await screen.findByText("오프라인 · 준비 조회만 가능합니다"),
    ).toBeVisible();
    expect(screen.getByRole("checkbox", { name: "멀티어댑터" })).toBeDisabled();
    expect(screen.getByLabelText("준비 항목 추가")).toBeDisabled();
    // 볼 수는 있어야 한다. 기내에서 목록을 확인하는 것이 이 화면의 완료 판정 중 하나다.
    expect(screen.getByText("멀티어댑터")).toBeVisible();

    Object.defineProperty(navigator, "onLine", {
      configurable: true,
      value: true,
    });
    window.dispatchEvent(new Event("online"));
  });
});
