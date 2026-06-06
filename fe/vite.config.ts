import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import path from "path";
import { defineConfig } from "vite";

import pkg from "./package.json";

export default defineConfig(() => {
  return {
    plugins: [react(), tailwindcss()],
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
