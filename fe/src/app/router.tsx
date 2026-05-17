import { Navigate, Route, Routes } from "react-router-dom";

import { PrivateRoute } from "../features/auth/components/PrivateRoute";
import { PublicRoute } from "../features/auth/components/PublicRoute";
import { HomePage } from "../pages/HomePage";
import { LandingPage } from "../pages/LandingPage";
import { LoginPage } from "../pages/LoginPage";
import { AppLayout } from "./layout/AppLayout";

export function AppRouter() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route element={<PublicRoute />}>
        <Route path="/login" element={<LoginPage />} />
      </Route>
      <Route element={<PrivateRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/home" element={<HomePage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
