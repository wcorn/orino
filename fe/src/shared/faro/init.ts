import {
  createReactRouterV7Options,
  getWebInstrumentations,
  initializeFaro,
  ReactIntegration,
} from "@grafana/faro-react";
import { TracingInstrumentation } from "@grafana/faro-web-tracing";
import {
  createRoutesFromChildren,
  matchRoutes,
  Routes,
  useLocation,
  useNavigationType,
} from "react-router-dom";

import { API_BASE_URL } from "@/shared/api/client";

let initialized = false;

export function initFaro(): void {
  if (initialized) return;

  const url = import.meta.env.VITE_FARO_URL;
  const apiKey = import.meta.env.VITE_FARO_KEY;

  if (!url || !apiKey) {
    return;
  }

  try {
    initializeFaro({
      url,
      apiKey,
      app: {
        name: "orino-fe",
        version: __APP_VERSION__,
        environment: import.meta.env.MODE,
      },
      instrumentations: [
        ...getWebInstrumentations({ captureConsole: true }),
        new TracingInstrumentation({
          instrumentationOptions: {
            propagateTraceHeaderCorsUrls: [
              new RegExp(escapeRegExp(API_BASE_URL)),
            ],
          },
        }),
        new ReactIntegration({
          router: createReactRouterV7Options({
            createRoutesFromChildren,
            matchRoutes,
            Routes,
            useLocation,
            useNavigationType,
          }),
        }),
      ],
    });
    initialized = true;
  } catch (error) {
    console.warn("[Faro] 초기화 실패 — 앱 동작에는 영향 없음", error);
  }
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
