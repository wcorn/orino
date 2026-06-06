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
export const importTodayReviews = () =>
  import("../pages/planner/TodayReviewsPage").then((m) => ({
    default: m.TodayReviewsPage,
  }));
export const importReviewCalendar = () =>
  import("../pages/planner/ReviewCalendarPage").then((m) => ({
    default: m.ReviewCalendarPage,
  }));

/**
 * 로그인 후 idle 시간에 페이지 청크를 미리 받아둔다.
 * lazy로 초기 번들은 가볍게 유지하면서, 실제 진입 시에는 이미 캐시돼 즉시 렌더된다.
 */
export function prefetchRoutes() {
  const run = () => {
    void importMaterialDetail();
    void importMaterialList();
    void importTodayReviews();
    void importReviewCalendar();
    void importHome();
  };
  if (typeof requestIdleCallback === "function") {
    requestIdleCallback(run);
  } else {
    setTimeout(run, 1500);
  }
}
