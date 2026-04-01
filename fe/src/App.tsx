import { BrowserRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth/AuthProvider";
import { PrivateRoute } from "./auth/PrivateRoute";
import { PublicRoute } from "./auth/PublicRoute";
import { LoginPage } from "./pages/LoginPage";
import { MainPage } from "./pages/MainPage";

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route element={<PublicRoute />}>
            <Route path="/login" element={<LoginPage />} />
          </Route>
          <Route element={<PrivateRoute />}>
            <Route path="/" element={<MainPage />} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
