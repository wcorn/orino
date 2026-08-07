import { CalendarDays, CheckSquare, LayoutGrid, Plane } from "lucide-react";
import type { ComponentType } from "react";
import { useNavigate } from "react-router-dom";

import { AppHeader } from "@/app/layout/AppHeader";
import { Toaster } from "@/components/Toaster";
import { Badge } from "@/components/ui/badge";
import { usePlannerCalendar } from "@/features/planner/hooks/usePlannerCalendar";
import { useReviewSummary } from "@/features/review/hooks/useReviewSummary";
import type { TravelSummary } from "@/features/travel/api/travel";
import { useTravelSummary } from "@/features/travel/hooks/useTravelSummary";
import { cn } from "@/lib/utils";

/** "2026-10-24" → "10.24" */
function shortDate(isoDate: string): string {
  const [, month, day] = isoDate.split("-");
  return `${Number(month)}.${day}`;
}

/**
 * 여행 카드의 배지·메타·이동 경로를 요약에서 뽑는다.
 * 데이터가 없으면 메타를 비운다 — 더미 텍스트를 채우지 않는다.
 */
function travelCardContent(summary: TravelSummary | undefined) {
  if (summary?.ongoing) {
    return {
      badge: "진행 중",
      meta: summary.ongoing.title,
      to: summary.ongoing.boardPath,
    };
  }
  if (summary?.next) {
    const { title, startDate, endDate, dDay } = summary.next;
    return {
      badge: `D-${dDay}`,
      meta: `${title} · ${shortDate(startDate)} – ${shortDate(endDate)}`,
      to: "/travel",
    };
  }
  if (summary?.recentCompleted) {
    return { badge: null, meta: "여행 만들기", to: "/travel" };
  }
  // 아직 못 받았거나 여행이 하나도 없는 상태 — 둘 다 메타를 비운다.
  return { badge: null, meta: null, to: "/travel" };
}

/** 기기 시간대 기준 오늘. 일상 워크스페이스는 여행 타임존과 무관하다. */
function today(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

export function WorkspaceSelectPage() {
  const navigate = useNavigate();
  const { data: travel } = useTravelSummary();
  const { data: review } = useReviewSummary();
  // 오늘 하루치 피드에서 루틴 인스턴스만 센다. Google 미연동이면 조회가 실패하고,
  // 그때는 메타 줄을 그리지 않는다(더미 텍스트 대신 빈 자리).
  const { data: feed } = usePlannerCalendar(today(), today());

  const reviewCount = review?.counts.now ?? 0;
  const routineCount =
    feed?.events.filter((event) => event.routine).length ?? 0;
  const { badge, meta, to } = travelCardContent(travel);

  const dailyMeta = routineCount > 0 ? `오늘 루틴 ${routineCount}개` : null;

  return (
    <div className="flex min-h-svh flex-col">
      <AppHeader />
      <main className="grid flex-1 place-items-center px-4 pt-8 pb-16">
        <div className="flex w-full max-w-[680px] flex-col gap-6">
          <div>
            <h1 className="text-title font-semibold">어디로 갈까요</h1>
            <p className="text-muted-foreground mt-1 text-sm">
              사이드바 맨 위에서 언제든 바꿀 수 있어요.
            </p>
          </div>
          <div className="flex flex-wrap gap-4">
            <WorkspaceCard
              title="여행"
              description="일정 보드, 지도, 알림, 환율·날씨"
              icon={Plane}
              iconClassName="bg-accent text-accent-foreground"
              badge={badge}
              metaIcon={CalendarDays}
              meta={meta}
              onClick={() => navigate(to)}
            />
            <WorkspaceCard
              title="일상"
              description="학습 자료, 노트, 일상기록, 복습, 플래너"
              icon={LayoutGrid}
              iconClassName="bg-muted"
              badge={reviewCount > 0 ? `복습 ${reviewCount}` : null}
              metaIcon={CheckSquare}
              meta={dailyMeta}
              onClick={() => navigate("/home")}
            />
          </div>
        </div>
      </main>
      <Toaster />
    </div>
  );
}

interface WorkspaceCardProps {
  title: string;
  description: string;
  icon: ComponentType<{ className?: string }>;
  iconClassName: string;
  badge: string | null;
  metaIcon: ComponentType<{ className?: string }>;
  meta: string | null;
  onClick: () => void;
}

function WorkspaceCard({
  title,
  description,
  icon: Icon,
  iconClassName,
  badge,
  metaIcon: MetaIcon,
  meta,
  onClick,
}: WorkspaceCardProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "bg-card ring-foreground/10 flex flex-1 basis-[260px] flex-col gap-3.5 rounded-xl p-5 text-left ring-1",
        "hover:ring-primary transition-all duration-150 hover:-translate-y-px",
      )}
    >
      <div className="flex items-start justify-between">
        <span
          className={cn(
            "grid size-10 place-items-center rounded-lg",
            iconClassName,
          )}
        >
          <Icon className="size-5" />
        </span>
        {badge && <Badge variant="secondary">{badge}</Badge>}
      </div>
      <div>
        <p className="text-heading font-medium">{title}</p>
        <p className="text-muted-foreground mt-0.5 text-[13px]">
          {description}
        </p>
      </div>
      {/* 데이터가 없으면 줄 자체를 그리지 않는다 — 빈 자리가 더미 텍스트보다 낫다. */}
      {meta && (
        <p className="text-muted-foreground flex items-center gap-1.5 text-[13px]">
          <MetaIcon className="size-3.5" />
          {meta}
        </p>
      )}
    </button>
  );
}
