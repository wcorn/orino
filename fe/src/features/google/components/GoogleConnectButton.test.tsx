import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { GoogleConnectButton } from "./GoogleConnectButton";

const API_BASE = "https://api.orino.dev/api";

describe("GoogleConnectButton", () => {
  const originalLocation = window.location;

  beforeEach(() => {
    // URL 인스턴스로 교체: origin/protocol을 보존해 jsdom XHR이 깨지지 않으면서
    // href는 settable이라 리다이렉트 대상을 검증할 수 있다.
    Object.defineProperty(window, "location", {
      configurable: true,
      writable: true,
      value: new URL("http://localhost:3000/"),
    });
  });

  afterEach(() => {
    Object.defineProperty(window, "location", {
      configurable: true,
      writable: true,
      value: originalLocation,
    });
  });

  it("클릭하면 인증 URL을 받아 그 주소로 리다이렉트한다", async () => {
    const authUrl = "https://accounts.google.com/o/oauth2/v2/auth?client_id=x";
    server.use(
      http.get(`${API_BASE}/planner/google/oauth/url`, () =>
        HttpResponse.json({
          code: "OK",
          data: { authorizationUrl: authUrl },
        }),
      ),
    );

    renderWithRouter(<GoogleConnectButton />);
    await userEvent.click(screen.getByRole("button", { name: "Google 연결" }));

    await waitFor(() => expect(window.location.href).toBe(authUrl));
  });
});
