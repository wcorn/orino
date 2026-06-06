import { lazy, Suspense } from "react";
import { Navigate, Route, Routes } from "react-router-dom";

import { PrivateRoute } from "../features/auth/components/PrivateRoute";
import { PublicRoute } from "../features/auth/components/PublicRoute";
import { LandingPage } from "../pages/LandingPage";
import { LoginPage } from "../pages/LoginPage";
import { AppLayout } from "./layout/AppLayout";
import {
  importHome,
  importMaterialDetail,
  importMaterialList,
  importReviewCalendar,
  importTodayReviews,
} from "./routeImports";

// 로그인 후에만 쓰는 페이지들은 lazy 로드해 초기 번들에서 분리한다.
// (특히 Tiptap 에디터가 무거운 MaterialDetailPage)
const HomePage = lazy(importHome);
const MaterialListPage = lazy(importMaterialList);
const MaterialDetailPage = lazy(importMaterialDetail);
const TodayReviewsPage = lazy(importTodayReviews);
const ReviewCalendarPage = lazy(importReviewCalendar);

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
