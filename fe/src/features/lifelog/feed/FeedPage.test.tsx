import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import type { MomentCard } from "../api/types";
import { FeedPage } from "./FeedPage";

const API = "https://api.orino.dev/api";

function moment(overrides: Partial<MomentCard> = {}): MomentCard {
  return {
    id: 1,
    occurredAt: "2026-07-20T05:00:00Z",
    body: "성산일출봉",
    mood: "EXCITED",
    lat: null,
    lng: null,
    placeName: "성산일출봉",
    tags: ["제주"],
    photos: [],
    flows: [],
    createdAt: "2026-07-20T05:00:00Z",
    ...overrides,
  };
}

function feedHandler(items: MomentCard[], nextCursor: string | null = null) {
  return http.get(`${API}/lifelog/moments`, () =>
    HttpResponse.json({ code: "OK", data: { items, nextCursor } }),
  );
}

describe("FeedPage", () => {
  it("피드의 기록 카드를 렌더한다", async () => {
    server.use(
      feedHandler([
        moment({
          id: 1,
          body: "정상 도착",
          placeName: "성산일출봉",
          tags: ["제주"],
        }),
      ]),
    );

    renderWithRouter(<FeedPage />);

    expect(await screen.findByText("정상 도착")).toBeInTheDocument();
    expect(screen.getByText("성산일출봉")).toBeInTheDocument();
    expect(screen.getByText("#제주")).toBeInTheDocument();
  });

  it("기록이 없으면 안내를 보여준다", async () => {
    server.use(feedHandler([]));

    renderWithRouter(<FeedPage />);

    expect(
      await screen.findByText("첫 순간을 기록해보세요."),
    ).toBeInTheDocument();
  });

  it("더 보기로 다음 페이지를 이어 붙인다", async () => {
    let call = 0;
    server.use(
      http.get(`${API}/lifelog/moments`, () => {
        call += 1;
        return call === 1
          ? HttpResponse.json({
              code: "OK",
              data: {
                items: [moment({ id: 1, body: "첫페이지" })],
                nextCursor: "CURSOR",
              },
            })
          : HttpResponse.json({
              code: "OK",
              data: {
                items: [moment({ id: 2, body: "둘째페이지" })],
                nextCursor: null,
              },
            });
      }),
    );

    renderWithRouter(<FeedPage />);
    expect(await screen.findByText("첫페이지")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "더 보기" }));

    expect(await screen.findByText("둘째페이지")).toBeInTheDocument();
    expect(screen.getByText("첫페이지")).toBeInTheDocument();
  });

  it("기록을 작성하면 피드에 나타난다", async () => {
    const store: MomentCard[] = [];
    server.use(
      http.get(`${API}/lifelog/moments`, () =>
        HttpResponse.json({
          code: "OK",
          data: { items: store, nextCursor: null },
        }),
      ),
      http.post(`${API}/lifelog/moments`, async ({ request }) => {
        const body = (await request.json()) as { body: string };
        const created = moment({
          id: 99,
          body: body.body,
          tags: [],
          placeName: null,
        });
        store.unshift(created);
        return HttpResponse.json({ code: "OK", data: created });
      }),
    );

    renderWithRouter(<FeedPage />);
    await screen.findByText("첫 순간을 기록해보세요.");

    await userEvent.click(screen.getByRole("button", { name: "기록" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.type(
      within(dialog).getByLabelText("본문"),
      "새 기록입니다",
    );
    await userEvent.click(within(dialog).getByRole("button", { name: "기록" }));

    expect(await screen.findByText("새 기록입니다")).toBeInTheDocument();
  });

  it("발생 시각은 날짜와 24시간제 시각으로 넣는다 — 오전/오후를 고르지 않는다", async () => {
    let sent: { occurredAt: string | null } | undefined;
    server.use(
      feedHandler([]),
      http.post(`${API}/lifelog/moments`, async ({ request }) => {
        sent = (await request.json()) as { occurredAt: string | null };
        return HttpResponse.json({ code: "OK", data: moment({ id: 98 }) });
      }),
    );

    renderWithRouter(<FeedPage />);
    await screen.findByText("첫 순간을 기록해보세요.");
    await userEvent.click(screen.getByRole("button", { name: "기록" }));
    const dialog = within(await screen.findByRole("dialog"));

    // 시각은 날짜가 있어야 의미가 있다.
    expect(dialog.getByLabelText("발생 시각")).toBeDisabled();

    await userEvent.type(dialog.getByLabelText("발생 날짜"), "2026-07-20");
    await userEvent.clear(dialog.getByLabelText("발생 시각"));
    await userEvent.type(dialog.getByLabelText("발생 시각"), "2130");
    await userEvent.type(dialog.getByLabelText("본문"), "야경");
    await userEvent.click(dialog.getByRole("button", { name: "기록" }));

    await waitFor(() => {
      // 로컬 21:30이다. 기기 타임존과 무관하게 같은 순간인지 본다.
      expect(sent?.occurredAt).toBe(
        new Date(2026, 6, 20, 21, 30).toISOString(),
      );
    });
  });

  it("기록을 삭제하면 피드에서 사라진다", async () => {
    const store: MomentCard[] = [moment({ id: 7, body: "지울기록", tags: [] })];
    server.use(
      http.get(`${API}/lifelog/moments`, () =>
        HttpResponse.json({
          code: "OK",
          data: { items: store, nextCursor: null },
        }),
      ),
      http.delete(`${API}/lifelog/moments/7`, () => {
        store.length = 0;
        return HttpResponse.json({ code: "OK", data: null });
      }),
    );

    renderWithRouter(<FeedPage />);
    expect(await screen.findByText("지울기록")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "기록 메뉴" }));
    await userEvent.click(
      await screen.findByRole("menuitem", { name: "삭제" }),
    );
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "삭제" }));

    await waitFor(() =>
      expect(screen.queryByText("지울기록")).not.toBeInTheDocument(),
    );
  });
});
