import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  timeout: 30_000,
  retries: 0,
  use: {
    baseURL: "http://localhost:3000",
    headless: true,
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { browserName: "chromium" },
    },
    // 여행은 Android Chrome 전용이다. 드래그·스와이프는 마우스와 터치의 동작이 달라
    // 터치 입력으로도 함께 돌린다(실기기 감각까지 대신하지는 못한다).
    {
      name: "mobile-touch",
      testMatch: /travel-board-.*\.spec\.ts/,
      use: {
        browserName: "chromium",
        viewport: { width: 412, height: 915 },
        hasTouch: true,
        isMobile: true,
      },
    },
  ],
  webServer: {
    command: "npm run dev",
    url: "http://localhost:3000",
    reuseExistingServer: true,
    timeout: 10_000,
  },
});
