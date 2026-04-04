import { BrowserRouter } from "react-router-dom";

import { ErrorBoundary } from "../shared/error/ErrorBoundary";
import { Providers } from "./providers";
import { AppRouter } from "./router";

function App() {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <Providers>
          <AppRouter />
        </Providers>
      </BrowserRouter>
    </ErrorBoundary>
  );
}

export default App;
