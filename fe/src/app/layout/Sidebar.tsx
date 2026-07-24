import {
  BookOpen,
  CalendarDays,
  Camera,
  CheckSquare,
  FileText,
  Home,
  Settings,
} from "lucide-react";
import { NavLink, useLocation } from "react-router-dom";

import { useReviewSummary } from "@/features/review/hooks/useReviewSummary";
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
}

const NAV_ITEMS: NavItem[] = [
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

/** activePaths가 지정된 항목의 활성 여부 — 해당 경로이거나 그 하위 경로면 활성. */
function matchesActivePaths(pathname: string, paths: string[]): boolean {
  return paths.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}

interface SidebarProps {
  open: boolean;
  onClose: () => void;
}

export function Sidebar({ open, onClose }: SidebarProps) {
  const { data } = useReviewSummary();
  const reviewCount = data?.counts.now ?? 0;
  const { pathname } = useLocation();

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
        <ul className="flex flex-col gap-0.5 p-2">
          {NAV_ITEMS.map((item) => {
            const Icon = item.icon;
            const isReviewItem = item.to === "/planner/reviews";
            return (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.to === "/home"}
                  className={({ isActive }) => {
                    const active = item.activePaths
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
