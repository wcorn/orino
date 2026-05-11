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
      sourcemap: false,
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
