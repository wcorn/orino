import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { Toaster } from "@/components/Toaster";
import { useToastStore } from "@/shared/lib/toast";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { GoogleConnectionCard } from "./GoogleConnectionCard";

const API_BASE = "https://api.orino.dev/api";

function mockStatus(data: {
  connected: boolean;
  googleEmail: string | null;
  scopes: string[] | null;
  connectedAt: string | null;
}) {
  server.use(
    http.get(`${API_BASE}/planner/google/status`, () =>
      HttpResponse.json({ code: "OK", data }),
    ),
  );
}

describe("GoogleConnectionCard", () => {
  beforeEach(() => {
    useToastStore.setState({ toasts: [] });
  });

  it("미연동이면 '연결되지 않음'과 연결 버튼을 보여준다", async () => {
    mockStatus({
      connected: false,
      googleEmail: null,
      scopes: null,
      connectedAt: null,
    });

    renderWithRouter(<GoogleConnectionCard />);

    expect(await screen.findByText("연결되지 않음")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Google 연결" }),
    ).toBeInTheDocument();
  });

  it("연동되면 계정/연결일과 해제 버튼을 보여준다", async () => {
    mockStatus({
      connected: true,
      googleEmail: "me@gmail.com",
      scopes: ["https://www.googleapis.com/auth/calendar"],
      connectedAt: "2026-06-15T10:00:00",
    });

    renderWithRouter(<GoogleConnectionCard />);

    expect(await screen.findByText("me@gmail.com")).toBeInTheDocument();
    expect(screen.getByText("연결됨")).toBeInTheDocument();
    expect(screen.getByText("2026년 6월 15일")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "연결 해제" }),
    ).toBeInTheDocument();
  });

  it("연결 해제를 누르면 disconnect 후 미연동 상태로 바뀌고 토스트를 띄운다", async () => {
    let connected = true;
    server.use(
      http.get(`${API_BASE}/planner/google/status`, () =>
        HttpResponse.json({
          code: "OK",
          data: connected
            ? {
                connected: true,
                googleEmail: "me@gmail.com",
                scopes: ["s"],
                connectedAt: "2026-06-15T10:00:00",
              }
            : {
                connected: false,
                googleEmail: null,
                scopes: null,
                connectedAt: null,
              },
        }),
      ),
      http.post(`${API_BASE}/planner/google/disconnect`, () => {
        connected = false;
        return HttpResponse.json({ code: "OK", data: null });
      }),
    );

    renderWithRouter(
      <>
        <GoogleConnectionCard />
        <Toaster />
      </>,
    );

    await userEvent.click(await screen.findByRole("button", { name: "연결 해제" }));

    expect(await screen.findByText("연결되지 않음")).toBeInTheDocument();
    expect(
      await screen.findByText("Google 연동을 해제했습니다."),
    ).toBeInTheDocument();
  });
});
