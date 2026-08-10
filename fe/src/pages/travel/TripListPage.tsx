import { ChevronRight, Plus } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";
import { buttonVariants } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingText } from "@/components/ui/loading-text";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { TripCounts, TripSummary } from "@/features/travel/api/travel";
import { useTrips } from "@/features/travel/hooks/useTrips";
import { formatCities, formatTodayCity } from "@/features/travel/lib/cityPath";
import type { TripStatus } from "@/features/travel/lib/tripStatus";
import { formatShortDate } from "@/features/travel/lib/tripStatus";

const TABS: { value: TripStatus; label: string; countKey: keyof TripCounts }[] =
  [
    { value: "UPCOMING", label: "예정", countKey: "upcoming" },
    { value: "ONGOING", label: "진행 중", countKey: "ongoing" },
    { value: "COMPLETED", label: "완료", countKey: "completed" },
  ];

/** 정렬은 서버가 확정한다. 사용자에게 규칙을 한 줄로 알려 준다. */
const SORT_HINT: Record<TripStatus, string> = {
  UPCOMING: "시작일 오름차순",
  ONGOING: "시작일 오름차순",
  COMPLETED: "종료일 내림차순",
};

/** S-02 여행 목록. 상태 탭으로 나눠 보고, 카드를 누르면 그 여행의 보드로 간다. */
export function TripListPage() {
  const [status, setStatus] = useState<TripStatus>("UPCOMING");
  const { data, isPending } = useTrips(status);

  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-6">
      <PageHeader
        title="여행 목록"
        actions={
          <Link
            to="/travel/trips/new"
            className={buttonVariants({ variant: "default" })}
          >
            <Plus className="size-4" />
            여행 만들기
          </Link>
        }
      />
      <Tabs
        value={status}
        onValueChange={(value) => setStatus(value as TripStatus)}
      >
        <TabsList>
          {TABS.map((tab) => (
            <TabsTrigger key={tab.value} value={tab.value}>
              {tab.label}
              {/* 건수는 어느 탭을 보든 전체 기준으로 온다. */}
              <span className="tabular-nums opacity-60">
                {data?.counts[tab.countKey] ?? 0}
              </span>
            </TabsTrigger>
          ))}
        </TabsList>
        {TABS.map((tab) => (
          <TabsContent key={tab.value} value={tab.value}>
            <div className="flex flex-col gap-3">
              <p className="text-caption text-muted-foreground">
                {SORT_HINT[tab.value]}
              </p>
              {isPending ? (
                <div className="grid min-h-[30svh] place-items-center">
                  <LoadingText />
                </div>
              ) : data && data.trips.length > 0 ? (
                <ul className="flex flex-col gap-2">
                  {data.trips.map((trip) => (
                    <li key={trip.id}>
                      <TripRow trip={trip} />
                    </li>
                  ))}
                </ul>
              ) : (
                <EmptyState className="min-h-[30svh]">
                  <p className="text-muted-foreground text-sm">
                    {tab.label} 여행이 없어요.
                  </p>
                  <Link
                    to="/travel/trips/new"
                    className={buttonVariants({ variant: "outline" })}
                  >
                    <Plus className="size-4" />
                    여행 만들기
                  </Link>
                </EmptyState>
              )}
            </div>
          </TabsContent>
        ))}
      </Tabs>
    </div>
  );
}

function TripRow({ trip }: { trip: TripSummary }) {
  return (
    <Link
      to={`/travel/trips/${trip.id}/board`}
      className="bg-card ring-foreground/10 hover:ring-primary flex items-center justify-between gap-3 rounded-xl p-4 ring-1 transition-all"
    >
      <span className="min-w-0">
        <span className="block truncate text-[15px] font-medium">
          {trip.title}
        </span>
        {/* 도시 나열은 별도 줄이다 — 6개 도시가 기간·일정과 한 줄에 들어가지 않는다. */}
        <span className="block truncate text-[13px]">{cityLine(trip)}</span>
        {/* 메타는 한 문자열이다 — 조각으로 나누면 "일정 6" / "개"처럼 숫자와 단위가 갈라진다. */}
        <span className="text-muted-foreground block text-[13px]">
          {metaLine(trip)}
        </span>
      </span>
      <ChevronRight className="text-muted-foreground size-4 shrink-0" />
    </Link>
  );
}

function metaLine(trip: TripSummary): string {
  const period = `${formatShortDate(trip.startDate)} – ${formatShortDate(trip.endDate)}`;
  return `${period} · 일정 ${trip.activityCount}개`;
}

/**
 * 구간 순서대로 도시를 나열한다 — `오사카 → 교토 → … → 도쿄 (6개 도시)`.
 *
 * <p>진행 중 여행은 <b>오늘의 도시를 강조한다.</b> 그 카드에서 가장 급한 정보는 전체 경로가
 * 아니라 "지금 어디"라서, 나열 앞에 오늘 도시를 세우고 굵게 남긴다.
 */
function cityLine(trip: TripSummary) {
  const path = formatCities(trip.cities) || trip.destinationName;
  const today = formatTodayCity(trip.cities);
  if (!today) return path;
  return (
    <>
      <span className="text-foreground font-medium">오늘 {today}</span>
      <span className="text-muted-foreground"> · {path}</span>
    </>
  );
}
