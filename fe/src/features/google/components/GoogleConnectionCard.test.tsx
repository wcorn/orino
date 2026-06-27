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
  reviewMirrorEnabled: boolean;
}) {
  server.use(
    http.get(`${API_BASE}/planner/google/status`, () =>
      HttpResponse.json({ code: "OK", data }),
    ),
  );
}

const MIRROR_LABEL = "복습 일정을 Google 캘린더에 표시";

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
      reviewMirrorEnabled: false,
    });

    renderWithRouter(<GoogleConnectionCard />);

    expect(await screen.findByText("연결되지 않음")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Google 연결" }),
    ).toBeInTheDocument();
    // 미연동이면 복습 미러 토글은 노출되지 않는다(연결 선행)
    expect(
      screen.queryByRole("switch", { name: MIRROR_LABEL }),
    ).not.toBeInTheDocument();
  });

  it("연동되면 계정/연결일과 해제 버튼을 보여준다", async () => {
    mockStatus({
      connected: true,
      googleEmail: "me@gmail.com",
      scopes: ["https://www.googleapis.com/auth/calendar"],
      connectedAt: "2026-06-15T10:00:00",
      reviewMirrorEnabled: false,
    });

    renderWithRouter(<GoogleConnectionCard />);

    expect(await screen.findByText("me@gmail.com")).toBeInTheDocument();
    expect(screen.getByText("연결됨")).toBeInTheDocument();
    expect(screen.getByText("2026년 6월 15일")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "연결 해제" }),
    ).toBeInTheDocument();
  });

  it("연동되면 복습 미러 토글이 보이고 reviewMirrorEnabled 초기 상태를 반영한다", async () => {
    mockStatus({
      connected: true,
      googleEmail: "me@gmail.com",
      scopes: ["s"],
      connectedAt: "2026-06-15T10:00:00",
      reviewMirrorEnabled: true,
    });

    renderWithRouter(<GoogleConnectionCard />);

    const toggle = await screen.findByRole("switch", { name: MIRROR_LABEL });
    expect(toggle).toHaveAttribute("aria-checked", "true");
  });

  it("토글을 켜면 PUT 호출 후 켜진 상태와 성공 토스트를 반영한다", async () => {
    let enabled = false;
    server.use(
      http.get(`${API_BASE}/planner/google/status`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            connected: true,
            googleEmail: "me@gmail.com",
            scopes: ["s"],
            connectedAt: "2026-06-15T10:00:00",
            reviewMirrorEnabled: enabled,
          },
        }),
      ),
      http.put(`${API_BASE}/planner/reviews/mirror`, async ({ request }) => {
        const body = (await request.json()) as { enabled: boolean };
        enabled = body.enabled;
        return HttpResponse.json({
          code: "OK",
          data: { enabled, reviewCalendarId: "review-cal" },
        });
      }),
    );

    renderWithRouter(
      <>
        <GoogleConnectionCard />
        <Toaster />
      </>,
    );

    const toggle = await screen.findByRole("switch", { name: MIRROR_LABEL });
    expect(toggle).toHaveAttribute("aria-checked", "false");

    await userEvent.click(toggle);

    expect(
      await screen.findByText("복습 일정을 Google 캘린더에 표시합니다."),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole("switch", { name: MIRROR_LABEL }),
    ).toHaveAttribute("aria-checked", "true");
  });

  it("토글 PUT이 실패하면 에러 토스트를 띄우고 상태는 그대로다", async () => {
    server.use(
      http.get(`${API_BASE}/planner/google/status`, () =>
        HttpResponse.json({
          code: "OK",
          data: {
            connected: true,
            googleEmail: "me@gmail.com",
            scopes: ["s"],
            connectedAt: "2026-06-15T10:00:00",
            reviewMirrorEnabled: false,
          },
        }),
      ),
      http.put(`${API_BASE}/planner/reviews/mirror`, () =>
        HttpResponse.json(
          { code: "PLN-ERR-003", message: "Google 연동이 필요합니다." },
          { status: 409 },
        ),
      ),
    );

    renderWithRouter(
      <>
        <GoogleConnectionCard />
        <Toaster />
      </>,
    );

    await userEvent.click(
      await screen.findByRole("switch", { name: MIRROR_LABEL }),
    );

    expect(
      await screen.findByText("설정 변경에 실패했습니다. 다시 시도해 주세요."),
    ).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: MIRROR_LABEL })).toHaveAttribute(
      "aria-checked",
      "false",
    );
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
                reviewMirrorEnabled: false,
              }
            : {
                connected: false,
                googleEmail: null,
                scopes: null,
                connectedAt: null,
                reviewMirrorEnabled: false,
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

    await userEvent.click(
      await screen.findByRole("button", { name: "연결 해제" }),
    );

    expect(await screen.findByText("연결되지 않음")).toBeInTheDocument();
    expect(
      await screen.findByText("Google 연동을 해제했습니다."),
    ).toBeInTheDocument();
  });
});
