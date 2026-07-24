import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import type { FlowSummary } from "../api/flows";
import { FlowListPage } from "./FlowListPage";

const API = "https://api.orino.dev/api";

function flow(overrides: Partial<FlowSummary> = {}): FlowSummary {
  return {
    id: 1,
    title: "제주 여행",
    description: null,
    coverUrl: null,
    startedAt: "2026-07-20T00:00:00Z",
    endedAt: "2026-07-22T00:00:00Z",
    momentCount: 5,
    status: "ACTIVE",
    ...overrides,
  };
}

describe("FlowListPage", () => {
  it("상태별로 흐름을 보여주고 필터를 바꾸면 다시 조회한다", async () => {
    server.use(
      http.get(`${API}/lifelog/flows`, ({ request }) => {
        const status = new URL(request.url).searchParams.get("status");
        const data =
          status === "ARCHIVED"
            ? [flow({ id: 2, title: "보관된흐름", status: "ARCHIVED" })]
            : [flow({ id: 1, title: "진행흐름" })];
        return HttpResponse.json({ code: "OK", data });
      }),
    );

    renderWithRouter(<FlowListPage />, { initialEntries: ["/lifelog/flows"] });

    expect(await screen.findByText("진행흐름")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "보관" }));

    expect(await screen.findByText("보관된흐름")).toBeInTheDocument();
    expect(screen.queryByText("진행흐름")).not.toBeInTheDocument();
  });

  it("흐름이 없으면 안내를 보여준다", async () => {
    server.use(
      http.get(`${API}/lifelog/flows`, () =>
        HttpResponse.json({ code: "OK", data: [] }),
      ),
    );

    renderWithRouter(<FlowListPage />, { initialEntries: ["/lifelog/flows"] });

    expect(await screen.findByText(/아직 흐름이 없어요/)).toBeInTheDocument();
  });
});
