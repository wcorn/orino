import { beforeEach, describe, expect, it, vi } from "vitest";

import { initTheme, useThemeStore } from "./theme";

describe("theme", () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove("dark");
    useThemeStore.setState({ theme: "system" });
  });

  describe("useThemeStore", () => {
    it("setTheme으로 테마를 변경하고 localStorage에 저장한다", () => {
      useThemeStore.getState().setTheme("dark");

      expect(useThemeStore.getState().theme).toBe("dark");
      expect(localStorage.getItem("theme")).toBe("dark");
    });

    it("dark 테마 설정 시 documentElement에 dark 클래스를 추가한다", () => {
      useThemeStore.getState().setTheme("dark");

      expect(document.documentElement.classList.contains("dark")).toBe(true);
    });

    it("light 테마 설정 시 dark 클래스를 제거한다", () => {
      document.documentElement.classList.add("dark");

      useThemeStore.getState().setTheme("light");

      expect(document.documentElement.classList.contains("dark")).toBe(false);
    });
  });

  describe("initTheme", () => {
    beforeEach(() => {
      window.matchMedia = vi.fn().mockReturnValue({
        matches: false,
        addEventListener: vi.fn(),
      });
    });

    it("저장된 테마를 적용한다", () => {
      useThemeStore.setState({ theme: "dark" });

      initTheme();

      expect(document.documentElement.classList.contains("dark")).toBe(true);
    });

    it("system 테마일 때 시스템 설정 변경 리스너를 등록한다", () => {
      const addEventListener = vi.fn();
      window.matchMedia = vi.fn().mockReturnValue({
        matches: false,
        addEventListener,
      });

      useThemeStore.setState({ theme: "system" });

      initTheme();

      expect(addEventListener).toHaveBeenCalledWith(
        "change",
        expect.any(Function),
      );
    });
  });
});
