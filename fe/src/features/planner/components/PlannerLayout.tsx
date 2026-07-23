import { Calendar, CalendarRange, Repeat } from "lucide-react";
import { NavLink, Outlet } from "react-router-dom";

import { cn } from "@/lib/utils";

const SUB_TABS = [
  { to: "/planner/calendar", label: "캘린더", icon: Calendar },
  { to: "/planner/plan", label: "주간 계획표", icon: CalendarRange },
  { to: "/planner/routines", label: "루틴", icon: Repeat },
];

/**
 * 플래너 통합 레이아웃 — 캘린더·주간 계획표·루틴을 하나의 상위 영역으로 묶고
 * 본문 상단 하위 탭으로 전환한다. 라우트(/planner/calendar|plan|routines)는 그대로이며,
 * 좌측 사이드바에는 「플래너」 단일 항목으로 노출된다. 세그먼트 스타일은
 * 디자인 시스템 Tabs 토큰(bg-muted 리스트 · 활성 bg-background)을 따른다.
 */
export function PlannerLayout() {
  return (
    <div className="flex flex-col gap-6">
      <nav aria-label="플래너 하위 메뉴" className="-mx-1 overflow-x-auto px-1">
        <div className="bg-muted text-muted-foreground inline-flex h-9 w-fit items-center gap-1 rounded-lg p-1">
          {SUB_TABS.map((tab) => {
            const Icon = tab.icon;
            return (
              <NavLink
                key={tab.to}
                to={tab.to}
                className={({ isActive }) =>
                  cn(
                    "inline-flex h-7 items-center gap-1.5 rounded-md px-3 text-sm font-medium whitespace-nowrap transition-colors",
                    isActive
                      ? "bg-background text-foreground"
                      : "hover:text-foreground",
                  )
                }
              >
                <Icon className="size-4" />
                {tab.label}
              </NavLink>
            );
          })}
        </div>
      </nav>
      <Outlet />
    </div>
  );
}
