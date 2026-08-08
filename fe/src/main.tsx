import "./index.css";

import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import App from "./app/App.tsx";
import { initFaro } from "./shared/faro/init";
import { installChunkReloadHandler } from "./shared/lib/chunkReload";
import { registerServiceWorker } from "./shared/lib/serviceWorker";
import { initTheme } from "./shared/lib/theme";
import { useToastStore } from "./shared/lib/toast";

initFaro();
initTheme();
installChunkReloadHandler();

// SW는 앱 전체에 걸린다. 새 버전을 말없이 적용하면 보고 있던 화면이 갈아치워지므로
// 안내하고 사용자가 새로고침하게 한다(무기한 대기 — 지금 하던 일을 끊지 않는다).
registerServiceWorker((applyUpdate) => {
  useToastStore.getState().show("새 버전이 있어요.", {
    action: { label: "새로고침", onAction: applyUpdate },
    durationMs: Number.POSITIVE_INFINITY,
  });
});

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
