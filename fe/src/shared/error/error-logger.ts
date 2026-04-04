interface ErrorLog {
  type: "api" | "render" | "unhandled";
  message: string;
  url?: string;
  status?: number;
  stack?: string;
  timestamp: string;
}

const ERROR_ENDPOINT = "/api/client-logs";

function send(log: ErrorLog): void {
  if (import.meta.env.DEV) {
    console.error("[ErrorLog]", log);
    return;
  }
  navigator.sendBeacon(
    ERROR_ENDPOINT,
    new Blob([JSON.stringify(log)], { type: "application/json" }),
  );
}

export function logApiError(
  url: string | undefined,
  status: number | undefined,
  message: string,
): void {
  send({
    type: "api",
    message,
    url,
    status,
    timestamp: new Date().toISOString(),
  });
}

export function logRenderError(error: unknown): void {
  const err = error instanceof Error ? error : new Error(String(error));
  send({
    type: "render",
    message: err.message,
    stack: err.stack,
    timestamp: new Date().toISOString(),
  });
}

export function logUnhandledError(error: unknown): void {
  const err = error instanceof Error ? error : new Error(String(error));
  send({
    type: "unhandled",
    message: err.message,
    stack: err.stack,
    timestamp: new Date().toISOString(),
  });
}
