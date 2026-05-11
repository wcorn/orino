import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import { Providers } from "@/app/providers";
import { Toaster } from "@/components/Toaster";
import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";
import { renderWithRouter } from "@/test/render";

import { MaterialDetailPage } from "./MaterialDetailPage";

const API_BASE = "https://api.orino.dev/api";

interface UnitState {
  id: number;
  title: string;
  sortOrder: number;
  status: "PENDING" | "COMPLETED";
  completedAt: string | null;
}

function setupMaterialApis(initialUnits: UnitState[] = []) {
  let units: UnitState[] = [...initialUnits];
  let nextUnitId = 100;
  let deletedMaterial = false;
  let materialTitle = "이펙티브 자바";

  server.use(
    http.get(`${API_BASE}/planner/materials/1`, () => {
      if (deletedMaterial) {
        return HttpResponse.json(
          { code: "SP-ERR-001", message: "Not found" },
          { status: 404 },
        );
      }
      return HttpResponse.json({
        code: "OK",
        data: {
          id: 1,
          title: materialTitle,
          type: "BOOK",
          status: "ACTIVE",
          totalUnits: units.length,
          completedUnits: units.filter((u) => u.status === "COMPLETED").length,
          createdAt: "2026-05-01T10:00:00",
          updatedAt: "2026-05-01T10:00:00",
          units,
        },
      });
    }),
    http.post(`${API_BASE}/planner/materials/1/units`, async ({ request }) => {
      const body = (await request.json()) as {
        units: { title: string }[];
      };
      const created = body.units.map((u, idx) => ({
        id: nextUnitId++,
        materialId: 1,
        title: u.title,
        sortOrder: units.length + idx + 1,
        status: "PENDING" as const,
        completedAt: null,
      }));
      units = [...units, ...created];
      return HttpResponse.json(
        { code: "OK", data: { units: created } },
        { status: 201 },
      );
    }),
    http.post(`${API_BASE}/planner/units/:id/complete`, ({ params }) => {
      const id = Number(params.id);
      const idx = units.findIndex((u) => u.id === id);
      if (idx >= 0) {
        units[idx] = {
          ...units[idx],
          status: "COMPLETED",
          completedAt: "2026-05-12T10:00:00",
        };
      }
      return HttpResponse.json({
        code: "OK",
        data: {
          unit: {
            id,
            title: units[idx]?.title ?? "",
            status: "COMPLETED",
            completedAt: "2026-05-12T10:00:00",
          },
          firstReview: {
            id: 200,
            studyUnitId: id,
            sequence: 1,
            scheduledDate: "2026-05-13",
            intervalDays: 1,
            easeFactor: 2.5,
            status: "PENDING",
            completedAt: null,
          },
        },
      });
    }),
    http.delete(`${API_BASE}/planner/units/:id`, ({ params }) => {
      const id = Number(params.id);
      units = units.filter((u) => u.id !== id);
      return new HttpResponse(null, { status: 204 });
    }),
    http.delete(`${API_BASE}/planner/materials/1`, () => {
      deletedMaterial = true;
      return new HttpResponse(null, { status: 204 });
    }),
    http.patch(`${API_BASE}/planner/materials/1`, async ({ request }) => {
      const body = (await request.json()) as { title?: string };
      if (body.title) materialTitle = body.title;
      return HttpResponse.json({
        code: "OK",
        data: {
          id: 1,
          title: materialTitle,
          type: "BOOK",
          status: "ACTIVE",
          totalUnits: units.length,
          completedUnits: units.filter((u) => u.status === "COMPLETED").length,
          createdAt: "2026-05-01T10:00:00",
          updatedAt: "2026-05-01T10:00:00",
        },
      });
    }),
  );
}

function renderPage() {
  return renderWithRouter(
    <Providers>
      <Routes>
        <Route path="/planner/materials/:id" element={<MaterialDetailPage />} />
        <Route path="/planner/materials" element={<div>목록 페이지</div>} />
      </Routes>
      <Toaster />
    </Providers>,
    { initialEntries: ["/planner/materials/1"] },
  );
}

describe("MaterialDetailPage", () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
  });

  it("자료 메타와 단위가 없을 때 빈 메시지를 표시한다", async () => {
    setupMaterialApis([]);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("이펙티브 자바")).toBeInTheDocument();
    });
    expect(screen.getByText(/등록된 단위가 없습니다/)).toBeInTheDocument();
  });

  it("단위 추가 다이얼로그에서 여러 줄 입력 시 배열로 POST된다", async () => {
    setupMaterialApis([]);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("이펙티브 자바")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /단위 추가/ }));

    const dialog = await screen.findByRole("dialog", {
      name: /학습 단위 추가/,
    });
    await user.type(
      within(dialog).getByLabelText(/한 줄에 하나씩/),
      "아이템 1\n아이템 2\n아이템 3",
    );
    await user.click(within(dialog).getByRole("button", { name: "추가" }));

    await waitFor(() => {
      expect(screen.getByText("아이템 1")).toBeInTheDocument();
    });
    expect(screen.getByText("아이템 2")).toBeInTheDocument();
    expect(screen.getByText("아이템 3")).toBeInTheDocument();
  });

  it("완료 버튼 클릭 시 단위가 COMPLETED 상태로 표시되고 토스트가 뜬다", async () => {
    setupMaterialApis([
      {
        id: 10,
        title: "아이템 1",
        sortOrder: 1,
        status: "PENDING",
        completedAt: null,
      },
    ]);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("아이템 1")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "완료" }));

    await waitFor(() => {
      expect(
        screen.getByText(/2026-05-13에 첫 복습이 예정되었어요/),
      ).toBeInTheDocument();
    });
  });

  it("단위 삭제 확인 후 목록에서 사라진다", async () => {
    setupMaterialApis([
      {
        id: 10,
        title: "삭제할 단위",
        sortOrder: 1,
        status: "PENDING",
        completedAt: null,
      },
    ]);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("삭제할 단위")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "단위 삭제" }));

    const confirm = await screen.findByRole("dialog", {
      name: /단위를 삭제할까요/,
    });
    await user.click(within(confirm).getByRole("button", { name: "삭제" }));

    await waitFor(() => {
      expect(screen.queryByText("삭제할 단위")).not.toBeInTheDocument();
    });
  });

  it("자료 삭제 후 목록 페이지로 이동한다", async () => {
    setupMaterialApis([]);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("이펙티브 자바")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /편집/ }));

    const editDialog = await screen.findByRole("dialog", {
      name: /자료 편집/,
    });
    await user.click(
      within(editDialog).getByRole("button", { name: "자료 삭제" }),
    );

    const confirmDialog = await screen.findByRole("dialog", {
      name: /자료를 삭제할까요/,
    });
    await user.click(
      within(confirmDialog).getByRole("button", { name: "삭제" }),
    );

    await waitFor(() => {
      expect(screen.getByText("목록 페이지")).toBeInTheDocument();
    });
  });
});
