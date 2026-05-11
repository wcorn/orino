import { BookOpen, CheckSquare, Home } from "lucide-react";
import { NavLink } from "react-router-dom";

import { useTodayReviews } from "@/features/review/hooks/useTodayReviews";
import { cn } from "@/lib/utils";

interface NavItem {
  to: string;
  label: string;
  icon: typeof Home;
}

const NAV_ITEMS: NavItem[] = [
  { to: "/home", label: "홈", icon: Home },
  { to: "/planner/materials", label: "학습 자료", icon: BookOpen },
  { to: "/planner/reviews/today", label: "오늘 복습", icon: CheckSquare },
];

export function Sidebar() {
  const { data } = useTodayReviews();
  const reviewCount = data?.reviews.length ?? 0;

  return (
    <nav
      aria-label="주 메뉴"
      className="border-border bg-background w-56 shrink-0 border-r"
    >
      <ul className="flex flex-col gap-0.5 p-2">
        {NAV_ITEMS.map((item) => {
          const Icon = item.icon;
          const isReviewItem = item.to === "/planner/reviews/today";
          return (
            <li key={item.to}>
              <NavLink
                to={item.to}
                end={item.to === "/home"}
                className={({ isActive }) =>
                  cn(
                    "flex h-9 items-center justify-between rounded-md px-3 text-sm font-medium transition-colors",
                    isActive
                      ? "bg-primary/10 text-primary"
                      : "text-foreground/70 hover:bg-muted hover:text-foreground",
                  )
                }
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
  );
}
