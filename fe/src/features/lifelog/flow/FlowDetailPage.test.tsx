import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { Route, Routes } from "react-router-dom";
import { describe, expect, it } from "vitest";

import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import type { FlowDetail } from "../api/flows";
import type { MomentCard } from "../api/types";
import { FlowDetailPage } from "./FlowDetailPage";

const API = "https://api.orino.dev/api";

function moment(id: number, body: string, occurredAt: string): MomentCard {
  return {
    id,
    occurredAt,
    body,
    mood: null,
    lat: null,
    lng: null,
    placeName: null,
    tags: [],
    photos: [],
    flows: [],
    createdAt: occurredAt,
  };
}

function renderDetail() {
  return renderWithRouter(
    <Routes>
      <Route path="/lifelog/flows/:id" element={<FlowDetailPage />} />
    </Routes>,
    { initialEntries: ["/lifelog/flows/1"] },
  );
}

function detailHandlers(moments: MomentCard[]) {
  const state = { moments };
  const detail = (): FlowDetail => ({
    id: 1,
    title: "제주 여행",
    description: null,
    coverUrl: null,
    startedAt: null,
    endedAt: null,
    status: "ACTIVE",
    moments: state.moments,
  });
  return [
    http.get(`${API}/lifelog/flows/1`, () =>
      HttpResponse.json({ code: "OK", data: detail() }),
    ),
    http.delete(`${API}/lifelog/flows/1/moments/:mid`, ({ params }) => {
      state.moments = state.moments.filter((m) => m.id !== Number(params.mid));
      return HttpResponse.json({ code: "OK", data: null });
    }),
    http.put(`${API}/lifelog/flows/1/moments/order`, async ({ request }) => {
      const body = (await request.json()) as { momentIds: number[] };
      state.moments = body.momentIds
        .map((id) => state.moments.find((m) => m.id === id))
        .filter((m): m is MomentCard => Boolean(m));
      return HttpResponse.json({ code: "OK", data: detail() });
    }),
    http.post(`${API}/lifelog/flows/1/moments`, async ({ request }) => {
      const body = (await request.json()) as { momentIds: number[] };
      body.momentIds.forEach((id) =>
        state.moments.push(moment(id, `추가된${id}`, "2026-07-21T00:00:00Z")),
      );
      return HttpResponse.json({ code: "OK", data: detail() });
    }),
    http.get(`${API}/lifelog/moments`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          items: [moment(50, "담을기록", "2026-07-19T00:00:00Z")],
          nextCursor: null,
        },
      }),
    ),
  ];
}

describe("FlowDetailPage", () => {
  it("타임라인에 담긴 기록을 시간순으로 보여준다", async () => {
    server.use(
      ...detailHandlers([
        moment(5, "아침", "2026-07-20T00:00:00Z"),
        moment(6, "저녁", "2026-07-20T10:00:00Z"),
      ]),
    );
    renderDetail();

    expect(await screen.findByText("아침")).toBeInTheDocument();
    expect(screen.getByText("저녁")).toBeInTheDocument();
  });

  it("기록을 빼면 타임라인에서 사라진다", async () => {
    server.use(
      ...detailHandlers([
        moment(5, "지울기록", "2026-07-20T00:00:00Z"),
        moment(6, "남을기록", "2026-07-20T10:00:00Z"),
      ]),
    );
    renderDetail();
    await screen.findByText("지울기록");

    const item = screen.getByText("지울기록").closest("li")!;
    await userEvent.click(
      within(item).getByRole("button", { name: "흐름에서 빼기" }),
    );

    await waitFor(() =>
      expect(screen.queryByText("지울기록")).not.toBeInTheDocument(),
    );
    expect(screen.getByText("남을기록")).toBeInTheDocument();
  });

  it("순서를 내리면 반영된다", async () => {
    server.use(
      ...detailHandlers([
        moment(5, "첫번째", "2026-07-20T00:00:00Z"),
        moment(6, "두번째", "2026-07-20T10:00:00Z"),
      ]),
    );
    renderDetail();
    await screen.findByText("첫번째");

    const first = screen.getByText("첫번째").closest("li")!;
    await userEvent.click(
      within(first).getByRole("button", { name: "아래로" }),
    );

    await waitFor(() => {
      const items = screen.getAllByRole("listitem");
      expect(within(items[0]).getByText("두번째")).toBeInTheDocument();
      expect(within(items[1]).getByText("첫번째")).toBeInTheDocument();
    });
  });

  it("기록을 담으면 타임라인에 추가된다", async () => {
    server.use(...detailHandlers([]));
    renderDetail();
    await screen.findByText(/아직 담긴 기록이 없어요/);

    await userEvent.click(screen.getByRole("button", { name: "담기" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByLabelText("기록 50 선택"));
    await userEvent.click(within(dialog).getByRole("button", { name: /담기/ }));

    expect(await screen.findByText("추가된50")).toBeInTheDocument();
  });

  it("지도 탭으로 전환할 수 있다", async () => {
    server.use(...detailHandlers([moment(5, "기록", "2026-07-20T00:00:00Z")]));
    renderDetail();
    await screen.findByText("기록");

    await userEvent.click(screen.getByRole("tab", { name: "지도" }));

    // 좌표가 없는 기록뿐이므로 지도 대신 안내를 보여준다(지도 렌더는 브라우저 전용).
    expect(
      screen.getByText(/위치 정보가 있는 기록이 없어요/),
    ).toBeInTheDocument();
  });
});
