import { lazy, Suspense } from "react";
import { Navigate, Route, Routes } from "react-router-dom";

import { PrivateRoute } from "../features/auth/components/PrivateRoute";
import { PublicRoute } from "../features/auth/components/PublicRoute";
import { LandingPage } from "../pages/LandingPage";
import { LoginPage } from "../pages/LoginPage";
import { AppLayout } from "./layout/AppLayout";

// 로그인 후에만 쓰는 페이지들은 lazy 로드해 초기 번들에서 분리한다.
// (특히 Tiptap 에디터가 무거운 MaterialDetailPage)
const HomePage = lazy(() =>
  import("../pages/HomePage").then((m) => ({ default: m.HomePage })),
);
const MaterialListPage = lazy(() =>
  import("../pages/planner/MaterialListPage").then((m) => ({
    default: m.MaterialListPage,
  })),
);
const MaterialDetailPage = lazy(() =>
  import("../pages/planner/MaterialDetailPage").then((m) => ({
    default: m.MaterialDetailPage,
  })),
);
const TodayReviewsPage = lazy(() =>
  import("../pages/planner/TodayReviewsPage").then((m) => ({
    default: m.TodayReviewsPage,
  })),
);
const ReviewCalendarPage = lazy(() =>
  import("../pages/planner/ReviewCalendarPage").then((m) => ({
    default: m.ReviewCalendarPage,
  })),
);

function RouteFallback() {
  return <div className="text-muted-foreground p-6 text-sm">불러오는 중…</div>;
}

export function AppRouter() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route element={<PublicRoute />}>
        <Route path="/login" element={<LoginPage />} />
      </Route>
      <Route element={<PrivateRoute />}>
        <Route element={<AppLayout />}>
          <Route
            path="/home"
            element={
              <Suspense fallback={<RouteFallback />}>
                <HomePage />
              </Suspense>
            }
          />
          <Route
            path="/planner/materials"
            element={
              <Suspense fallback={<RouteFallback />}>
                <MaterialListPage />
              </Suspense>
            }
          />
          <Route
            path="/planner/materials/:id"
            element={
              <Suspense fallback={<RouteFallback />}>
                <MaterialDetailPage />
              </Suspense>
            }
          />
          <Route
            path="/planner/reviews/today"
            element={
              <Suspense fallback={<RouteFallback />}>
                <TodayReviewsPage />
              </Suspense>
            }
          />
          <Route
            path="/planner/calendar"
            element={
              <Suspense fallback={<RouteFallback />}>
                <ReviewCalendarPage />
              </Suspense>
            }
          />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
