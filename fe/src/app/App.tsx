import { BrowserRouter } from "react-router-dom";

import { Providers } from "./providers";
import { AppRouter } from "./router";

function App() {
  return (
    <BrowserRouter>
      <Providers>
        <AppRouter />
      </Providers>
    </BrowserRouter>
  );
}

export default App;
