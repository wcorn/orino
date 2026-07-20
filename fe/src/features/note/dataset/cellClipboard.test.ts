import { describe, expect, it } from "vitest";

import { DATASET_CELLS_MIME, parseCellTsv } from "./cellClipboard";

describe("parseCellTsv", () => {
  it("탭·줄바꿈 TSV를 2차원 배열로 파싱한다", () => {
    expect(parseCellTsv("a\tb\nc\td")).toEqual([
      ["a", "b"],
      ["c", "d"],
    ]);
  });

  it("끝 개행 1개는 무시하고 CRLF도 처리한다", () => {
    expect(parseCellTsv("a\tb\r\nc\td\n")).toEqual([
      ["a", "b"],
      ["c", "d"],
    ]);
  });

  it("한 칸(탭·줄바꿈 없음)도 1x1로 파싱한다", () => {
    expect(parseCellTsv("x")).toEqual([["x"]]);
  });
});

describe("DATASET_CELLS_MIME", () => {
  it("일반 텍스트와 구분되는 커스텀 타입이다", () => {
    expect(DATASET_CELLS_MIME).toBe("text/x-orino-dataset-cells");
  });
});
