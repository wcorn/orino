import { describe, expect, it } from "vitest";

import { fileNameFromDisposition } from "./download";

describe("fileNameFromDisposition", () => {
  it("한글 이름은 RFC 5987 쪽에서 읽는다 — ASCII 대체본은 뭉개져 있다", () => {
    const header =
      "attachment; filename=\"?? ??.xlsx\"; filename*=UTF-8''%EC%A3%BC%EB%AC%B8%20%EB%82%B4%EC%97%AD.xlsx";
    expect(fileNameFromDisposition(header)).toBe("주문 내역.xlsx");
  });

  it("RFC 5987 자리가 없으면 옛 filename을 쓴다", () => {
    expect(fileNameFromDisposition('attachment; filename="orders.xlsx"')).toBe(
      "orders.xlsx",
    );
  });

  it("퍼센트 인코딩이 깨져 있으면 ASCII 대체본으로 물러난다", () => {
    const header =
      "attachment; filename=\"fallback.xlsx\"; filename*=UTF-8''%E0%A4%A";
    expect(fileNameFromDisposition(header)).toBe("fallback.xlsx");
  });

  it("헤더가 없으면 이름도 없다 — 부르는 쪽이 기본값을 정한다", () => {
    expect(fileNameFromDisposition(undefined)).toBeNull();
    expect(fileNameFromDisposition("attachment")).toBeNull();
  });
});
