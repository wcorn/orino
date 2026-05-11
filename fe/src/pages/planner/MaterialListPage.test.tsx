import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { MaterialListPage } from "./MaterialListPage";

const API_BASE = "https://api.orino.dev/api";

function renderPage() {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route path="/planner/materials" element={<MaterialListPage />} />
        <Route path="/planner/materials/:id" element={<div>상세 페이지</div>} />
      </Routes>
    </Providers>,
    { initialEntries: ["/planner/materials"] },
  );
}

describe("MaterialListPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
  });

  it("자료 목록이 있으면 카드 리스트를 렌더링한다", async () => {
    server.use(
      http.get(`${API_BASE}/planner/materials`, () => {
        return HttpResponse.json({
          code: "OK",
          data: {
            materials: [
              {
                id: 1,
                title: "이펙티브 자바",
                type: "BOOK",
                status: "ACTIVE",
                totalUnits: 90,
                completedUnits: 12,
                createdAt: "2026-05-01T10:00:00",
                updatedAt: "2026-05-01T10:00:00",
              },
              {
                id: 2,
                title: "토비의 스프링",
                type: "BOOK",
                status: "ACTIVE",
                totalUnits: 0,
                completedUnits: 0,
                createdAt: "2026-05-02T10:00:00",
                updatedAt: "2026-05-02T10:00:00",
              },
            ],
          },
        });
      }),
    );

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("이펙티브 자바")).toBeInTheDocument();
    });
    expect(screen.getByText("토비의 스프링")).toBeInTheDocument();
    expect(screen.getByText("12 / 90 (13%)")).toBeInTheDocument();
    expect(screen.getByText("0 / 0 (0%)")).toBeInTheDocument();
  });

  it("자료가 0건이면 빈 상태와 첫 자료 추가 버튼이 보인다", async () => {
    server.use(
      http.get(`${API_BASE}/planner/materials`, () => {
        return HttpResponse.json({
          code: "OK",
          data: { materials: [] },
        });
      }),
    );

    renderPage();

    await waitFor(() => {
      expect(
        screen.getByText("아직 등록된 학습 자료가 없습니다."),
      ).toBeInTheDocument();
    });
    expect(
      screen.getByRole("button", { name: /첫 자료 추가/ }),
    ).toBeInTheDocument();
  });

  it("자료 추가 버튼 클릭 시 다이얼로그가 열린다", async () => {
    server.use(
      http.get(`${API_BASE}/planner/materials`, () => {
        return HttpResponse.json({
          code: "OK",
          data: { materials: [] },
        });
      }),
    );

    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /자료 추가/ }),
      ).toBeInTheDocument();
    });

    await user.click(screen.getAllByRole("button", { name: /자료 추가/ })[0]);

    expect(
      await screen.findByRole("dialog", { name: /학습 자료 추가/ }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("제목")).toBeInTheDocument();
  });

  it("다이얼로그에서 자료를 추가하면 목록이 갱신된다", async () => {
    let materials: unknown[] = [];
    server.use(
      http.get(`${API_BASE}/planner/materials`, () => {
        return HttpResponse.json({
          code: "OK",
          data: { materials },
        });
      }),
      http.post(`${API_BASE}/planner/materials`, async ({ request }) => {
        const body = (await request.json()) as {
          title: string;
          type: string;
        };
        const created = {
          id: 1,
          title: body.title,
          type: body.type,
          status: "ACTIVE",
          totalUnits: 0,
          completedUnits: 0,
          createdAt: "2026-05-12T00:00:00",
          updatedAt: "2026-05-12T00:00:00",
        };
        materials = [created];
        return HttpResponse.json(
          { code: "OK", data: created },
          { status: 201 },
        );
      }),
    );

    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(
        screen.getByText("아직 등록된 학습 자료가 없습니다."),
      ).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /첫 자료 추가/ }));

    const dialog = await screen.findByRole("dialog", {
      name: /학습 자료 추가/,
    });
    await user.type(within(dialog).getByLabelText("제목"), "이펙티브 자바");
    await user.click(within(dialog).getByRole("button", { name: "추가" }));

    await waitFor(() => {
      expect(screen.getByText("이펙티브 자바")).toBeInTheDocument();
    });
  });

  it("API 에러 시 에러 메시지를 표시한다", async () => {
    server.use(
      http.get(`${API_BASE}/planner/materials`, () => {
        return HttpResponse.json(
          { code: "GLB-ERR-003", message: "서버 오류" },
          { status: 500 },
        );
      }),
    );

    renderPage();

    await waitFor(() => {
      expect(
        screen.getByText(/학습 자료를 불러오지 못했어요/),
      ).toBeInTheDocument();
    });
  });
});
