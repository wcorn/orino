import { lazy, Suspense } from "react";
import { Navigate, Route, Routes } from "react-router-dom";

import { BrandMark } from "@/components/brand/Logo";
import { LoadingText } from "@/components/ui/loading-text";

import { PrivateRoute } from "../features/auth/components/PrivateRoute";
import { PublicRoute } from "../features/auth/components/PublicRoute";
import { PlannerLayout } from "../features/planner/components/PlannerLayout";
import { LandingPage } from "../pages/LandingPage";
import { LoginPage } from "../pages/LoginPage";
import { AppLayout } from "./layout/AppLayout";
import {
  importActivityDetail,
  importHome,
  importIntegrations,
  importLifelog,
  importLifelogFlowDetail,
  importLifelogFlows,
  importMaterialDetail,
  importMaterialList,
  importNotes,
  importPlaceSearch,
  importPlannerCalendar,
  importReviewHub,
  importReviewSession,
  importRoutines,
  importTravelHome,
  importTravelSettings,
  importTravelTools,
  importTripBoard,
  importTripForm,
  importTripList,
  importTripMap,
  importWeeklyPlan,
  importWorkspaceSelect,
} from "./routeImports";

// 로그인 후에만 쓰는 페이지들은 lazy 로드해 초기 번들에서 분리한다.
// (특히 Tiptap 에디터가 무거운 MaterialDetailPage)
const HomePage = lazy(importHome);
const MaterialListPage = lazy(importMaterialList);
const MaterialDetailPage = lazy(importMaterialDetail);
const ReviewHubPage = lazy(importReviewHub);
const ReviewSessionPage = lazy(importReviewSession);
const PlannerCalendarPage = lazy(importPlannerCalendar);
const IntegrationsPage = lazy(importIntegrations);
const RoutinesPage = lazy(importRoutines);
const WeeklyPlanPage = lazy(importWeeklyPlan);
const NotesPage = lazy(importNotes);
const LifelogPage = lazy(importLifelog);
const LifelogFlowsPage = lazy(importLifelogFlows);
const LifelogFlowDetailPage = lazy(importLifelogFlowDetail);
const WorkspaceSelectPage = lazy(importWorkspaceSelect);
const TravelHomePage = lazy(importTravelHome);
const TripListPage = lazy(importTripList);
const TripFormPage = lazy(importTripForm);
const TripBoardPage = lazy(importTripBoard);
const PlaceSearchPage = lazy(importPlaceSearch);
const TripMapPage = lazy(importTripMap);
const TravelSettingsPage = lazy(importTravelSettings);
const TravelToolsPage = lazy(importTravelTools);
const ActivityDetailPage = lazy(importActivityDetail);

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
        {/* 워크스페이스 선택 — 자체 헤더를 쓰고 사이드바가 없어 AppLayout 밖에 둔다. */}
        <Route
          path="/select"
          element={
            <Suspense fallback={<RouteFallback />}>
              <WorkspaceSelectPage />
            </Suspense>
          }
        />
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
            path="/planner/reviews"
            element={
              <Suspense fallback={<RouteFallback />}>
                <ReviewHubPage />
              </Suspense>
            }
          />
          <Route
            path="/planner/reviews/session"
            element={
              <Suspense fallback={<RouteFallback />}>
                <ReviewSessionPage />
              </Suspense>
            }
          />
          {/* 구 "오늘 복습" 경로 → 허브로 리다이렉트 */}
          <Route
            path="/planner/reviews/today"
            element={<Navigate to="/planner/reviews" replace />}
          />
          {/* 플래너 통합: 캘린더·주간 계획표·루틴을 상단 하위 탭으로 묶는다 */}
          <Route element={<PlannerLayout />}>
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
          </Route>
          {/* 연동 설정(공용) — Google 연결. OAuth 콜백 복귀 지점(/integrations?google=)이기도 하다 */}
          <Route
            path="/integrations"
            element={
              <Suspense fallback={<RouteFallback />}>
                <IntegrationsPage />
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
          <Route
            path="/lifelog"
            element={
              <Suspense fallback={<RouteFallback />}>
                <LifelogPage />
              </Suspense>
            }
          />
          <Route
            path="/lifelog/flows"
            element={
              <Suspense fallback={<RouteFallback />}>
                <LifelogFlowsPage />
              </Suspense>
            }
          />
          <Route
            path="/lifelog/flows/:id"
            element={
              <Suspense fallback={<RouteFallback />}>
                <LifelogFlowDetailPage />
              </Suspense>
            }
          />
          {/* 여행 워크스페이스. 화면은 후속 이슈에서 채우고 여기서는 라우트 자리를 잡는다.
              푸시 알림 클릭이 /travel/* 딥링크로 들어오므로 선택 화면으로 되돌리지 않는다. */}
          <Route
            path="/travel"
            element={
              <Suspense fallback={<RouteFallback />}>
                <TravelHomePage />
              </Suspense>
            }
          />
          <Route
            path="/travel/trips"
            element={
              <Suspense fallback={<RouteFallback />}>
                <TripListPage />
              </Suspense>
            }
          />
          <Route
            path="/travel/trips/new"
            element={
              <Suspense fallback={<RouteFallback />}>
                <TripFormPage />
              </Suspense>
            }
          />
          <Route
            path="/travel/trips/:tripId/edit"
            element={
              <Suspense fallback={<RouteFallback />}>
                <TripFormPage />
              </Suspense>
            }
          />
          <Route
            path="/travel/trips/:tripId/board"
            element={
              <Suspense fallback={<RouteFallback />}>
                <TripBoardPage />
              </Suspense>
            }
          />
          <Route
            path="/travel/trips/:tripId/map"
            element={
              <Suspense fallback={<RouteFallback />}>
                <TripMapPage />
              </Suspense>
            }
          />
          <Route
            path="/travel/trips/:tripId/places"
            element={
              <Suspense fallback={<RouteFallback />}>
                <PlaceSearchPage />
              </Suspense>
            }
          />
          <Route
            path="/travel/activities/:activityId"
            element={
              <Suspense fallback={<RouteFallback />}>
                <ActivityDetailPage />
              </Suspense>
            }
          />
          <Route
            path="/travel/tools"
            element={
              <Suspense fallback={<RouteFallback />}>
                <TravelToolsPage />
              </Suspense>
            }
          />
          <Route
            path="/travel/settings"
            element={
              <Suspense fallback={<RouteFallback />}>
                <TravelSettingsPage />
              </Suspense>
            }
          />
          {/* 아직 없는 여행 하위 경로는 랜딩이 아니라 여행 홈으로 보낸다. */}
          <Route path="/travel/*" element={<Navigate to="/travel" replace />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
