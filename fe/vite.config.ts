import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import path from "path";
import { defineConfig } from "vite";
import { VitePWA } from "vite-plugin-pwa";

import pkg from "./package.json";

export default defineConfig(() => {
  return {
    plugins: [
      react(),
      tailwindcss(),
      VitePWA({
        // 웹푸시 수신 핸들러가 필요해 커스텀 SW를 쓴다(generateSW로는 못 넣는다).
        strategies: "injectManifest",
        srcDir: "src",
        filename: "sw.ts",
        // 새 SW를 말없이 적용하지 않는다 — 앱이 안내하고 사용자가 새로고침한다.
        registerType: "prompt",
        injectRegister: null,
        injectManifest: {
          // sourcemap(.map)까지 precache하면 용량만 커진다. 앱 셸만 담는다.
          globPatterns: ["**/*.{js,css,html,svg,png,woff2}"],
        },
        // dev에서 SW가 돌면 HMR과 싸운다. 빌드에서만 동작한다.
        devOptions: { enabled: false },
        // manifest는 이미 public/site.webmanifest로 있고 index.html이 링크한다.
        // 플러그인이 또 만들면 링크 두 개가 서로 다른 값을 말하게 된다.
        manifest: false,
      }),
    ],
    define: {
      __APP_VERSION__: JSON.stringify(pkg.version),
    },
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "./src"),
      },
    },
    test: {
      globals: true,
      environment: "jsdom",
      setupFiles: "./src/test/setup.ts",
      css: true,
      exclude: ["e2e/**", "node_modules/**"],
    },
    base: "/",
    build: {
      outDir: "dist",
      assetsDir: "assets",
      // sourcemap: true → .map 파일 생성 + JS 끝에 //# sourceMappingURL 코멘트 추가.
      // Alloy faro.receiver의 sourcemaps { download = true } 가 이 URL로 .map을 받아와
      // Grafana 에러 화면에서 unminified stack을 보여준다.
      // 소스는 이미 공개 저장소이므로 .map 공개 노출 무관.
      sourcemap: true,
      rollupOptions: {
        output: {
          // 무거운 의존성을 별도 청크로 분리해 초기 로딩/캐시 효율을 높인다.
          // 경로 기준이라 @tiptap/pm처럼 entry 없는 서브패키지도 안전하게 묶인다.
          manualChunks(id) {
            if (!id.includes("node_modules")) return undefined;
            if (/[\\/]node_modules[\\/]@tiptap[\\/]/.test(id)) return "tiptap";
            if (/[\\/]node_modules[\\/]prosemirror-/.test(id)) return "tiptap";
            if (/[\\/]node_modules[\\/]@grafana[\\/]/.test(id)) return "faro";
            if (
              /[\\/]node_modules[\\/](react|react-dom|react-router|react-router-dom)[\\/]/.test(
                id,
              )
            ) {
              return "react";
            }
            return undefined;
          },
        },
      },
    },
    // SW는 빌드 결과에서만 도므로 푸시·오프라인 확인은 preview로 한다.
    // dev와 같은 프록시가 없으면 그때 API를 못 부른다.
    preview: {
      port: 4173,
      proxy: {
        "/api": {
          target: process.env.API_TARGET ?? "http://localhost:8080",
          changeOrigin: true,
        },
      },
    },
    server: {
      host: "0.0.0.0",
      port: 3000,
      watch: {
        usePolling: true,
      },
      proxy: {
        "/api": {
          target: process.env.API_TARGET ?? "http://localhost:8080",
          changeOrigin: true,
        },
      },
    },
  };
});
