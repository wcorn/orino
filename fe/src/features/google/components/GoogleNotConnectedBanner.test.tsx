import { screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { GoogleNotConnectedBanner } from "./GoogleNotConnectedBanner";

const API_BASE = "https://api.orino.dev/api";

function mockConnected(connected: boolean) {
  server.use(
    http.get(`${API_BASE}/planner/google/status`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          connected,
          googleEmail: connected ? "me@gmail.com" : null,
          scopes: connected ? ["s"] : null,
          connectedAt: connected ? "2026-06-15T10:00:00" : null,
        },
      }),
    ),
  );
}

describe("GoogleNotConnectedBanner", () => {
  it("미연동이면 경고 배너와 연결 CTA를 보여준다", async () => {
    mockConnected(false);

    renderWithRouter(<GoogleNotConnectedBanner />);

    const banner = await screen.findByRole("alert");
    expect(banner).toHaveTextContent("Google 캘린더가 연결되지 않았습니다");
    expect(
      screen.getByRole("button", { name: "Google 연결" }),
    ).toBeInTheDocument();
  });

  it("연동된 경우 아무것도 렌더하지 않는다", async () => {
    mockConnected(true);

    renderWithRouter(<GoogleNotConnectedBanner />);

    await waitFor(() =>
      expect(screen.queryByRole("alert")).not.toBeInTheDocument(),
    );
  });
});
