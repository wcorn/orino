import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { useAuthStore } from "@/features/auth/store/authStore";
import { server } from "@/test/mocks/server";

import { createDatasetFromTable, createEmptyDataset } from "./datasetImport";

const API_BASE = "https://api.orino.dev/api";

describe("datasetImport", () => {
  let columns: { key: string; label: string }[] | null;
  let bulk: string[][] | null;

  beforeEach(() => {
    useAuthStore.setState({ accessToken: "mock-token" });
    columns = null;
    bulk = null;
    server.use(
      http.post(`${API_BASE}/datasets`, async ({ request }) => {
        const body = (await request.json()) as { columns: typeof columns };
        columns = body.columns;
        return HttpResponse.json({
          code: "OK",
          data: { id: 7, columns: body.columns, rowCount: 0 },
        });
      }),
      http.post(`${API_BASE}/datasets/7/rows/bulk`, async ({ request }) => {
        const body = (await request.json()) as { rows: string[][] };
        bulk = body.rows;
        return HttpResponse.json({
          code: "OK",
          data: { id: 7, columns: [], rowCount: body.rows.length },
        });
      }),
    );
  });

  describe("createEmptyDataset", () => {
    it("기본 3×3 빈 dataset을 만든다", async () => {
      const id = await createEmptyDataset();

      expect(id).toBe(7);
      expect(columns).toEqual([
        { key: "c0", label: "열 1" },
        { key: "c1", label: "열 2" },
        { key: "c2", label: "열 3" },
      ]);
      expect(bulk).toEqual([
        ["", "", ""],
        ["", "", ""],
        ["", "", ""],
      ]);
    });

    it("행 0개면 벌크 호출을 생략한다", async () => {
      await createEmptyDataset(2, 0);

      expect(columns).toHaveLength(2);
      expect(bulk).toBeNull();
    });
  });

  describe("createDatasetFromTable", () => {
    it("헤더가 있으면 라벨로 컬럼을 만들고 본문을 벌크 업로드한다", async () => {
      await createDatasetFromTable({
        headers: ["이름", "점수"],
        rows: [["김", "9"]],
      });

      expect(columns).toEqual([
        { key: "c0", label: "이름" },
        { key: "c1", label: "점수" },
      ]);
      expect(bulk).toEqual([["김", "9"]]);
    });

    it("헤더가 없으면 열 N 라벨을 만든다", async () => {
      await createDatasetFromTable({ headers: null, rows: [["a", "b"]] });

      expect(columns).toEqual([
        { key: "c0", label: "열 1" },
        { key: "c1", label: "열 2" },
      ]);
    });

    it("빈 헤더 라벨은 열 N으로 대체한다", async () => {
      await createDatasetFromTable({ headers: ["", "x"], rows: [] });

      expect(columns?.[0].label).toBe("열 1");
      expect(columns?.[1].label).toBe("x");
      // rows 0개 → 벌크 생략
      expect(bulk).toBeNull();
    });
  });
});
