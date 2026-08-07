import { useQueryClient } from "@tanstack/react-query";
import {
  BookOpen,
  CalendarDays,
  Camera,
  CheckSquare,
  FileText,
  Home,
  LayoutGrid,
  Plane,
  Settings,
  Wrench,
} from "lucide-react";
import { NavLink, useLocation, useNavigate } from "react-router-dom";

import { useReviewSummary } from "@/features/review/hooks/useReviewSummary";
import type { TravelSummary } from "@/features/travel/api/travel";
import { useTravelSummary } from "@/features/travel/hooks/useTravelSummary";
import { travelKeys } from "@/features/travel/queryKeys";
import { cn } from "@/lib/utils";

interface NavItem {
  to: string;
  label: string;
  icon: typeof Home;
  /**
   * 항목을 활성으로 볼 추가 경로. 지정하면 NavLink 기본 매칭 대신 이 경로들로 판정한다.
   * (「플래너」처럼 상위 1탭이 여러 하위 라우트를 대표하는 경우)
   */
  activePaths?: string[];
  /**
   * 경로 접두어로 표현할 수 없는 활성 판정. 여행 쪽은 `/travel/trips/:id/board`처럼
   * 중간에 id가 끼어 접두어 비교로는 「여행 목록」과 구분되지 않는다.
   */
  matchPath?: (pathname: string) => boolean;
}

/** `/travel/trips/12/board` · `/travel/trips/12/map` */
const BOARD_PATH = /^\/travel\/trips\/\d+\/(board|map)$/;
/** `/travel/trips` · `/travel/trips/new` · `/travel/trips/12/edit` (목록과 생성·수정 폼) */
const TRIP_LIST_PATH = /^\/travel\/trips(\/new|\/\d+\/edit)?$/;

const DAILY_NAV_ITEMS: NavItem[] = [
  { to: "/home", label: "홈", icon: Home },
  { to: "/planner/materials", label: "학습 자료", icon: BookOpen },
  { to: "/notes", label: "노트", icon: FileText },
  { to: "/lifelog", label: "일상기록", icon: Camera },
  { to: "/planner/reviews", label: "복습", icon: CheckSquare },
  {
    to: "/planner/calendar",
    label: "플래너",
    icon: CalendarDays,
    activePaths: ["/planner/calendar", "/planner/plan", "/planner/routines"],
  },
  { to: "/integrations", label: "연동 설정", icon: Settings },
];

/**
 * 여행 메뉴. 「일정 보드」의 `to`는 여는 시점에 정해진다(진행 중 여행의 보드 → 없으면 목록)
 * — 보드는 여행 없이는 열 수 없어서다. 그래서 여기서는 자리만 잡고 아래에서 채운다.
 */
const TRAVEL_NAV_ITEMS: NavItem[] = [
  { to: "/travel", label: "홈", icon: Home },
  {
    to: "/travel/trips",
    label: "여행 목록",
    icon: Plane,
    matchPath: (pathname) => TRIP_LIST_PATH.test(pathname),
  },
  {
    to: "/travel/trips",
    label: "일정 보드",
    icon: CalendarDays,
    // 보드·지도·일정 상세를 모두 이 항목이 대표한다.
    matchPath: (pathname) =>
      BOARD_PATH.test(pathname) || pathname.startsWith("/travel/activities/"),
  },
  { to: "/travel/tools", label: "도구", icon: Wrench },
  { to: "/travel/settings", label: "설정", icon: Settings },
];

/** 여행 워크스페이스인지 — 경로 하나로 판정한다(별도 상태를 두면 새로고침에 어긋난다). */
function isTravelWorkspace(pathname: string): boolean {
  return pathname === "/travel" || pathname.startsWith("/travel/");
}

/** activePaths가 지정된 항목의 활성 여부 — 해당 경로이거나 그 하위 경로면 활성. */
function matchesActivePaths(pathname: string, paths: string[]): boolean {
  return paths.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}

interface SidebarProps {
  open: boolean;
  onClose: () => void;
}

export function Sidebar({ open, onClose }: SidebarProps) {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const travel = isTravelWorkspace(pathname);

  const { data: reviewData } = useReviewSummary();
  const reviewCount = reviewData?.counts.now ?? 0;
  // 훅은 항상 호출하되(조건부 호출 금지) 일상 워크스페이스에서는 요청을 끈다 —
  // 일상 화면이 여행 API를 부르기 시작하면 그건 일상 쪽 동작 변경이다.
  const { data: travelData } = useTravelSummary({ enabled: travel });
  const boardPath = travelData?.ongoing?.boardPath ?? "/travel/trips";

  const navItems = travel ? TRAVEL_NAV_ITEMS : DAILY_NAV_ITEMS;

  /**
   * 여행으로 전환 — 진행 중 여행이 있으면 곧바로 그 보드로 들어간다.
   *
   * <p>일상에 있는 동안은 요약 쿼리가 꺼져 있어 캐시를 직접 읽는다. 로그인하면 반드시
   * `/select`를 거치므로 대개 이미 채워져 있고, 없으면 여행 홈으로 보낸다.
   */
  const goToTravel = () => {
    const cached = queryClient.getQueryData<TravelSummary>(travelKeys.summary);
    navigate(cached?.ongoing?.boardPath ?? "/travel");
    onClose();
  };

  const goToDaily = () => {
    navigate("/home");
    onClose();
  };

  return (
    <>
      {open && (
        <button
          type="button"
          aria-label="사이드바 닫기"
          className="bg-foreground/40 fixed inset-0 z-40 md:hidden"
          onClick={onClose}
        />
      )}
      <nav
        aria-label="주 메뉴"
        className={cn(
          "border-border bg-background w-56 shrink-0 border-r",
          "fixed inset-y-0 left-0 z-50 transition-transform",
          "md:static md:translate-x-0",
          open ? "translate-x-0" : "-translate-x-full md:translate-x-0",
        )}
      >
        <div className="p-2 pb-0">
          <div
            role="group"
            aria-label="워크스페이스"
            className="bg-muted flex gap-0.5 rounded-lg p-1"
          >
            <WorkspaceButton
              label="여행"
              icon={Plane}
              active={travel}
              onClick={goToTravel}
            />
            <WorkspaceButton
              label="일상"
              icon={LayoutGrid}
              active={!travel}
              onClick={goToDaily}
            />
          </div>
        </div>
        <ul className="flex flex-col gap-0.5 p-2">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isReviewItem = item.to === "/planner/reviews";
            const isBoardItem = item.label === "일정 보드";
            return (
              <li key={item.label}>
                <NavLink
                  to={isBoardItem ? boardPath : item.to}
                  end={item.to === "/home" || item.to === "/travel"}
                  className={({ isActive }) => {
                    const active = item.matchPath
                      ? item.matchPath(pathname)
                      : item.activePaths
                        ? matchesActivePaths(pathname, item.activePaths)
                        : isActive;
                    return cn(
                      "flex h-9 items-center justify-between rounded-md px-3 text-sm font-medium transition-colors",
                      active
                        ? "bg-primary/10 text-primary"
                        : "text-foreground/70 hover:bg-muted hover:text-foreground",
                    );
                  }}
                >
                  <span className="flex items-center gap-2">
                    <Icon className="size-4" />
                    {item.label}
                  </span>
                  {isReviewItem && reviewCount > 0 && (
                    <span
                      aria-label={`미완료 ${reviewCount}건`}
                      className="bg-primary text-primary-foreground inline-flex h-5 min-w-5 items-center justify-center rounded-full px-1.5 text-xs font-semibold"
                    >
                      {reviewCount}
                    </span>
                  )}
                </NavLink>
              </li>
            );
          })}
        </ul>
      </nav>
    </>
  );
}

interface WorkspaceButtonProps {
  label: string;
  icon: typeof Home;
  active: boolean;
  onClick: () => void;
}

function WorkspaceButton({
  label,
  icon: Icon,
  active,
  onClick,
}: WorkspaceButtonProps) {
  return (
    <button
      type="button"
      aria-current={active ? "true" : undefined}
      onClick={onClick}
      className={cn(
        "inline-flex h-7 flex-1 items-center justify-center gap-1.5 rounded-md text-[13px] font-medium transition-colors",
        active
          ? "bg-background text-foreground shadow-sm"
          : "text-muted-foreground",
      )}
    >
      <Icon className="size-3.5" />
      {label}
    </button>
  );
}
