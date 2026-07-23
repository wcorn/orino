// 페이지 lazy import 함수 + prefetch.
// router.tsx에서 분리해 컴포넌트 외 export로 인한 fast-refresh 경고를 피한다.
// lazy()와 prefetch가 같은 import 함수를 공유 → prefetch가 받아둔 청크를 진입 시 재사용.

export const importHome = () =>
  import("../pages/HomePage").then((m) => ({ default: m.HomePage }));
export const importMaterialList = () =>
  import("../pages/planner/MaterialListPage").then((m) => ({
    default: m.MaterialListPage,
  }));
export const importMaterialDetail = () =>
  import("../pages/planner/MaterialDetailPage").then((m) => ({
    default: m.MaterialDetailPage,
  }));
export const importReviewHub = () =>
  import("../pages/planner/ReviewHubPage").then((m) => ({
    default: m.ReviewHubPage,
  }));
export const importReviewSession = () =>
  import("../pages/planner/ReviewSessionPage").then((m) => ({
    default: m.ReviewSessionPage,
  }));
export const importPlannerCalendar = () =>
  import("../pages/planner/PlannerCalendarPage").then((m) => ({
    default: m.PlannerCalendarPage,
  }));
export const importIntegrations = () =>
  import("../pages/integrations/IntegrationsPage").then((m) => ({
    default: m.IntegrationsPage,
  }));
export const importRoutines = () =>
  import("../pages/planner/RoutinesPage").then((m) => ({
    default: m.RoutinesPage,
  }));
export const importWeeklyPlan = () =>
  import("../pages/planner/WeeklyPlanPage").then((m) => ({
    default: m.WeeklyPlanPage,
  }));
export const importNotes = () =>
  import("../pages/NotesPage").then((m) => ({ default: m.NotesPage }));

/**
 * 로그인 후 idle 시간에 페이지 청크를 미리 받아둔다.
 * lazy로 초기 번들은 가볍게 유지하면서, 실제 진입 시에는 이미 캐시돼 즉시 렌더된다.
 */
export function prefetchRoutes() {
  // 테스트 환경에서는 prefetch를 건너뛴다. idle 콜백이 테스트 teardown 후
  // 동적 import를 실행해 EnvironmentTeardownError를 일으키기 때문.
  if (import.meta.env.MODE === "test") return;

  const run = () => {
    const ignore = () => {};
    void importMaterialDetail().catch(ignore);
    void importMaterialList().catch(ignore);
    void importReviewHub().catch(ignore);
    void importPlannerCalendar().catch(ignore);
    void importHome().catch(ignore);
    void importNotes().catch(ignore);
  };
  if (typeof requestIdleCallback === "function") {
    requestIdleCallback(run);
  } else {
    setTimeout(run, 1500);
  }
}
