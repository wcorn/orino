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
      testIgnore: /(service-worker|offline)\.spec\.ts/,
      use: { browserName: "chromium" },
    },
    // SW·precache는 빌드 결과에서만 확인된다. dev 서버에는 SW가 없다.
    {
      name: "built",
      testMatch: /(service-worker|offline)\.spec\.ts/,
      use: { browserName: "chromium", baseURL: "http://localhost:4173" },
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
  webServer: [
    {
      command: "npm run dev",
      url: "http://localhost:3000",
      reuseExistingServer: true,
      timeout: 10_000,
    },
    // Service Worker는 dev에서 돌지 않는다(HMR과 싸워서 꺼 뒀다).
    // 실제로 배포되는 것은 빌드 결과이므로 SW 검증은 preview로 한다.
    {
      command: "npm run build && npm run preview -- --port 4173",
      url: "http://localhost:4173",
      reuseExistingServer: true,
      timeout: 120_000,
    },
  ],
});
