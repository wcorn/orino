import "./index.css";

import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import App from "./app/App.tsx";
import { initFaro } from "./shared/faro/init";
import { installChunkReloadHandler } from "./shared/lib/chunkReload";
import { initTheme } from "./shared/lib/theme";

initFaro();
initTheme();
installChunkReloadHandler();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
