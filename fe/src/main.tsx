import "./index.css";

import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import App from "./app/App.tsx";
import { logUnhandledError } from "./shared/error/error-logger";
import { initTheme } from "./shared/lib/theme";

initTheme();

window.addEventListener("error", (event) => {
  logUnhandledError(event.error);
});

window.addEventListener("unhandledrejection", (event) => {
  logUnhandledError(event.reason);
});

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
