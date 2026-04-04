import { beforeEach, describe, expect, it, vi } from "vitest";

import { logApiError, logRenderError, logUnhandledError } from "./error-logger";

describe("error-logger", () => {
  beforeEach(() => {
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  describe("logApiError", () => {
    it("DEV 환경에서 console.error로 로그를 출력한다", () => {
      logApiError("/api/test", 500, "서버 오류");

      expect(console.error).toHaveBeenCalledWith(
        "[ErrorLog]",
        expect.objectContaining({
          type: "api",
          message: "서버 오류",
          url: "/api/test",
          status: 500,
          timestamp: expect.any(String),
        }),
      );
    });

    it("url과 status가 undefined일 수 있다", () => {
      logApiError(undefined, undefined, "네트워크 오류");

      expect(console.error).toHaveBeenCalledWith(
        "[ErrorLog]",
        expect.objectContaining({
          type: "api",
          url: undefined,
          status: undefined,
        }),
      );
    });
  });

  describe("logRenderError", () => {
    it("Error 객체를 전달하면 message와 stack을 포함한다", () => {
      const error = new Error("렌더링 오류");

      logRenderError(error);

      expect(console.error).toHaveBeenCalledWith(
        "[ErrorLog]",
        expect.objectContaining({
          type: "render",
          message: "렌더링 오류",
          stack: expect.any(String),
        }),
      );
    });

    it("Error가 아닌 값을 전달하면 문자열로 변환한다", () => {
      logRenderError("문자열 에러");

      expect(console.error).toHaveBeenCalledWith(
        "[ErrorLog]",
        expect.objectContaining({
          type: "render",
          message: "문자열 에러",
        }),
      );
    });
  });

  describe("logUnhandledError", () => {
    it("Error 객체를 전달하면 unhandled 타입으로 로그를 출력한다", () => {
      const error = new Error("처리되지 않은 오류");

      logUnhandledError(error);

      expect(console.error).toHaveBeenCalledWith(
        "[ErrorLog]",
        expect.objectContaining({
          type: "unhandled",
          message: "처리되지 않은 오류",
          stack: expect.any(String),
        }),
      );
    });
  });
});
