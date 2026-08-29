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
export const importLifelog = () =>
  import("../pages/LifelogPage").then((m) => ({ default: m.LifelogPage }));
export const importLifelogFlows = () =>
  import("../pages/LifelogFlowsPage").then((m) => ({
    default: m.LifelogFlowsPage,
  }));
export const importLifelogFlowDetail = () =>
  import("../pages/LifelogFlowDetailPage").then((m) => ({
    default: m.LifelogFlowDetailPage,
  }));
export const importWorkspaceSelect = () =>
  import("../pages/WorkspaceSelectPage").then((m) => ({
    default: m.WorkspaceSelectPage,
  }));
export const importTravelHome = () =>
  import("../pages/travel/TravelHomePage").then((m) => ({
    default: m.TravelHomePage,
  }));
export const importTripList = () =>
  import("../pages/travel/TripListPage").then((m) => ({
    default: m.TripListPage,
  }));
export const importTripForm = () =>
  import("../pages/travel/TripFormPage").then((m) => ({
    default: m.TripFormPage,
  }));
export const importTripBoard = () =>
  import("../pages/travel/TripBoardPage").then((m) => ({
    default: m.TripBoardPage,
  }));
export const importTravelTools = () =>
  import("../pages/travel/TravelToolsPage").then((m) => ({
    default: m.TravelToolsPage,
  }));
export const importTravelSettings = () =>
  import("../pages/travel/TravelSettingsPage").then((m) => ({
    default: m.TravelSettingsPage,
  }));
export const importTripMap = () =>
  import("../pages/travel/TripMapPage").then((m) => ({
    default: m.TripMapPage,
  }));
export const importPlaceSearch = () =>
  import("../pages/travel/PlaceSearchPage").then((m) => ({
    default: m.PlaceSearchPage,
  }));
export const importActivityDetail = () =>
  import("../pages/travel/ActivityDetailPage").then((m) => ({
    default: m.ActivityDetailPage,
  }));
export const importLinkList = () =>
  import("../pages/links/LinkListPage").then((m) => ({
    default: m.LinkListPage,
  }));
export const importLinkDetail = () =>
  import("../pages/links/LinkDetailPage").then((m) => ({
    default: m.LinkDetailPage,
  }));
export const importLedgerDashboard = () =>
  import("../pages/ledger/LedgerDashboardPage").then((m) => ({
    default: m.LedgerDashboardPage,
  }));
export const importLedgerAssets = () =>
  import("../pages/ledger/LedgerAssetsPage").then((m) => ({
    default: m.LedgerAssetsPage,
  }));
export const importLedgerAssetDetail = () =>
  import("../pages/ledger/LedgerAssetDetailPage").then((m) => ({
    default: m.LedgerAssetDetailPage,
  }));
export const importLedgerTransactions = () =>
  import("../pages/ledger/LedgerTransactionsPage").then((m) => ({
    default: m.LedgerTransactionsPage,
  }));
export const importLedgerCards = () =>
  import("../pages/ledger/LedgerCardsPage").then((m) => ({
    default: m.LedgerCardsPage,
  }));
export const importLedgerStatements = () =>
  import("../pages/ledger/LedgerStatementsPage").then((m) => ({
    default: m.LedgerStatementsPage,
  }));
export const importLedgerRecurring = () =>
  import("../pages/ledger/LedgerRecurringPage").then((m) => ({
    default: m.LedgerRecurringPage,
  }));
export const importLedgerBudget = () =>
  import("../pages/ledger/LedgerBudgetPage").then((m) => ({
    default: m.LedgerBudgetPage,
  }));
export const importLedgerUpcoming = () =>
  import("../pages/ledger/LedgerUpcomingPage").then((m) => ({
    default: m.LedgerUpcomingPage,
  }));
export const importLedgerBulkInput = () =>
  import("../pages/ledger/LedgerBulkInputPage").then((m) => ({
    default: m.LedgerBulkInputPage,
  }));
export const importLedgerStats = () =>
  import("../pages/ledger/LedgerStatsPage").then((m) => ({
    default: m.LedgerStatsPage,
  }));
export const importLedgerSettings = () =>
  import("../pages/ledger/LedgerSettingsPage").then((m) => ({
    default: m.LedgerSettingsPage,
  }));
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
    // 로그인 직후 반드시 거치는 화면이라 가장 먼저 필요해진다.
    void importWorkspaceSelect().catch(ignore);
  };
  if (typeof requestIdleCallback === "function") {
    requestIdleCallback(run);
  } else {
    setTimeout(run, 1500);
  }
}
