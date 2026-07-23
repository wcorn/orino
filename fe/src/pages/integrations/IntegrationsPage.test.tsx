import { screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import { Toaster } from "@/components/Toaster";
import { useToastStore } from "@/shared/lib/toast";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { IntegrationsPage } from "./IntegrationsPage";

const API_BASE = "https://api.orino.dev/api";

function mockStatus(connected = false) {
  server.use(
    http.get(`${API_BASE}/integrations/google/status`, () =>
      HttpResponse.json({
        code: "OK",
        data: {
          connected,
          googleEmail: connected ? "me@gmail.com" : null,
          scopes: connected ? ["calendar", "tasks"] : null,
          connectedAt: connected ? "2026-05-18T00:00:00" : null,
          reviewMirrorEnabled: false,
        },
      }),
    ),
  );
}

function renderAt(entry: string) {
  return renderWithRouter(
    <>
      <Toaster />
      <Routes>
        <Route path="/integrations" element={<IntegrationsPage />} />
      </Routes>
    </>,
    { initialEntries: [entry] },
  );
}

describe("IntegrationsPage", () => {
  beforeEach(() => {
    useToastStore.setState({ toasts: [] });
    mockStatus(false);
  });

  it("연동 설정 헤더와 Google 연결 카드를 렌더한다", async () => {
    renderAt("/integrations");
    expect(
      await screen.findByRole("heading", { name: "연동 설정" }),
    ).toBeInTheDocument();
  });

  it("google=connected로 복귀하면 성공 토스트를 띄운다", async () => {
    mockStatus(true);
    renderAt("/integrations?google=connected");
    expect(
      await screen.findByText("Google 계정이 연결되었습니다."),
    ).toBeInTheDocument();
  });

  it("google=error로 복귀하면 에러 토스트를 띄운다", async () => {
    renderAt("/integrations?google=error");
    expect(
      await screen.findByText(
        "Google 연결에 실패했습니다. 다시 시도해 주세요.",
      ),
    ).toBeInTheDocument();
  });

  it("파라미터가 없으면 토스트를 띄우지 않는다", async () => {
    renderAt("/integrations");
    await screen.findByRole("heading", { name: "연동 설정" });
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });
});
