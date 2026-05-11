import { Navigate, Route, Routes } from "react-router-dom";

import { PrivateRoute } from "../features/auth/components/PrivateRoute";
import { PublicRoute } from "../features/auth/components/PublicRoute";
import { HomePage } from "../pages/HomePage";
import { LandingPage } from "../pages/LandingPage";
import { LoginPage } from "../pages/LoginPage";
import { MaterialDetailPage } from "../pages/planner/MaterialDetailPage";
import { MaterialListPage } from "../pages/planner/MaterialListPage";
import { TodayReviewsPage } from "../pages/planner/TodayReviewsPage";
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
          <Route path="/planner/materials" element={<MaterialListPage />} />
          <Route
            path="/planner/materials/:id"
            element={<MaterialDetailPage />}
          />
          <Route path="/planner/reviews/today" element={<TodayReviewsPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
