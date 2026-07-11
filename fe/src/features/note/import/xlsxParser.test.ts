import { describe, expect, it } from "vitest";
import * as XLSX from "xlsx";

import { parseXlsxSheets } from "./xlsxParser";

/** 테스트용 .xlsx File을 SheetJS로 생성한다. */
function makeXlsx(sheets: Record<string, unknown[][]>): File {
  const wb = XLSX.utils.book_new();
  for (const [name, aoa] of Object.entries(sheets)) {
    XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(aoa), name);
  }
  const buf = XLSX.write(wb, { type: "array", bookType: "xlsx" });
  return new File([buf], "test.xlsx", {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
}

describe("parseXlsxSheets", () => {
  it("셀 값을 행 우선 2D 문자열로 파싱한다", async () => {
    const file = makeXlsx({
      Sheet1: [
        ["이름", "점수"],
        ["김철수", 90],
        ["이영희", 85],
      ],
    });
    const sheets = await parseXlsxSheets(file);
    expect(sheets).toHaveLength(1);
    expect(sheets[0].name).toBe("Sheet1");
    expect(sheets[0].rows).toEqual([
      ["이름", "점수"],
      ["김철수", "90"],
      ["이영희", "85"],
    ]);
  });

  it("여러 시트를 모두 반환한다", async () => {
    const file = makeXlsx({
      A: [["a"]],
      B: [["b1", "b2"]],
    });
    const sheets = await parseXlsxSheets(file);
    expect(sheets.map((s) => s.name)).toEqual(["A", "B"]);
    expect(sheets[1].rows).toEqual([["b1", "b2"]]);
  });

  it("전 행이 빈 뒤쪽 열을 잘라 직사각형으로 만든다", async () => {
    const file = makeXlsx({
      S: [
        ["a", "", ""],
        ["b", "", ""],
      ],
    });
    const sheets = await parseXlsxSheets(file);
    // 값 있는 행 길이는 다르지만 정규화로 직사각형 + 빈 뒤쪽 열 제거
    expect(sheets[0].rows).toEqual([["a"], ["b"]]);
  });

  it("빈 시트는 빈 rows", async () => {
    const file = makeXlsx({ Empty: [] });
    const sheets = await parseXlsxSheets(file);
    expect(sheets[0].rows).toEqual([]);
  });
});
