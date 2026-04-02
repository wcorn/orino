import { Route, Routes } from "react-router-dom";
import { PrivateRoute } from "../features/auth/components/PrivateRoute";
import { PublicRoute } from "../features/auth/components/PublicRoute";
import { LoginPage } from "../pages/LoginPage";
import { MainPage } from "../pages/MainPage";

export function AppRouter() {
  return (
    <Routes>
      <Route element={<PublicRoute />}>
        <Route path="/login" element={<LoginPage />} />
      </Route>
      <Route element={<PrivateRoute />}>
        <Route path="/" element={<MainPage />} />
      </Route>
    </Routes>
  );
}
