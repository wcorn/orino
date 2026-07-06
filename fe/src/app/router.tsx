import { lazy, Suspense } from "react";
import { Navigate, Route, Routes } from "react-router-dom";

import { BrandMark } from "@/components/brand/Logo";
import { LoadingText } from "@/components/ui/loading-text";

import { PrivateRoute } from "../features/auth/components/PrivateRoute";
import { PublicRoute } from "../features/auth/components/PublicRoute";
import { LandingPage } from "../pages/LandingPage";
import { LoginPage } from "../pages/LoginPage";
import { PlannerCallbackRedirect } from "../pages/planner/PlannerCallbackRedirect";
import { AppLayout } from "./layout/AppLayout";
import {
  importHome,
  importMaterialDetail,
  importMaterialList,
  importNotes,
  importPlannerCalendar,
  importPlannerSettings,
  importRoutines,
  importTodayReviews,
  importWeeklyPlan,
} from "./routeImports";

// 로그인 후에만 쓰는 페이지들은 lazy 로드해 초기 번들에서 분리한다.
// (특히 Tiptap 에디터가 무거운 MaterialDetailPage)
const HomePage = lazy(importHome);
const MaterialListPage = lazy(importMaterialList);
const MaterialDetailPage = lazy(importMaterialDetail);
const TodayReviewsPage = lazy(importTodayReviews);
const PlannerCalendarPage = lazy(importPlannerCalendar);
const PlannerSettingsPage = lazy(importPlannerSettings);
const RoutinesPage = lazy(importRoutines);
const WeeklyPlanPage = lazy(importWeeklyPlan);
const NotesPage = lazy(importNotes);

function RouteFallback() {
  return (
    <div className="flex flex-col items-center justify-center gap-3 p-10">
      <BrandMark size={40} animated />
      <LoadingText />
    </div>
  );
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
                <PlannerCalendarPage />
              </Suspense>
            }
          />
          <Route
            path="/planner/routines"
            element={
              <Suspense fallback={<RouteFallback />}>
                <RoutinesPage />
              </Suspense>
            }
          />
          <Route
            path="/planner/plan"
            element={
              <Suspense fallback={<RouteFallback />}>
                <WeeklyPlanPage />
              </Suspense>
            }
          />
          <Route
            path="/planner/settings"
            element={
              <Suspense fallback={<RouteFallback />}>
                <PlannerSettingsPage />
              </Suspense>
            }
          />
          <Route
            path="/notes"
            element={
              <Suspense fallback={<RouteFallback />}>
                <NotesPage />
              </Suspense>
            }
          />
          {/* OAuth 콜백 복귀 지점: 토스트 후 캘린더로 */}
          <Route path="/planner" element={<PlannerCallbackRedirect />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
