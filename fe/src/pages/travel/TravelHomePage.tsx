import {
  CalendarDays,
  ChevronRight,
  MapPin,
  Plus,
  SquareCheckBig,
  TriangleAlert,
} from "lucide-react";
import { Link } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingText } from "@/components/ui/loading-text";
import type { PrepSummary } from "@/features/travel/api/prep";
import type {
  CompletedTripSummary,
  NextTripSummary,
  TravelSummary,
  TripCitySummary,
  TripDetail,
} from "@/features/travel/api/travel";
import { useTravelSummary } from "@/features/travel/hooks/useTravelSummary";
import { useTrip } from "@/features/travel/hooks/useTrip";
import { firstDayZone } from "@/features/travel/lib/baseCity";
import {
  formatCities,
  formatCityCount,
  formatTodayCity,
} from "@/features/travel/lib/cityPath";
import {
  dayChips,
  daysUntil,
  formatPeriod,
} from "@/features/travel/lib/tripStatus";

/**
 * S-01 여행 홈. 다음에 갈 여행 하나를 크게 보여주고 보드로 들여보내는 것이 전부다.
 *
 * <p>요약이 "어떤 여행을 볼지"를 정하고, 타임존·통화·기간은 상세에서 가져온다.
 */
export function TravelHomePage() {
  const { data: summary, isPending } = useTravelSummary();
  const featuredId = summary?.ongoing?.id ?? summary?.next?.id ?? null;
  const { data: featured } = useTrip(featuredId);

  if (isPending) {
    return (
      <div className="grid min-h-[40svh] place-items-center">
        <LoadingText />
      </div>
    );
  }

  const hasAnyTrip = Boolean(featuredId || summary?.recentCompleted);
  if (!hasAnyTrip) {
    return (
      <div className="mx-auto flex max-w-[720px] flex-col gap-6">
        <PageHeader title="여행" />
        <EmptyState>
          <p className="text-muted-foreground text-sm">
            아직 만든 여행이 없어요.
          </p>
          <Link
            to="/travel/trips/new"
            className={buttonVariants({ variant: "default" })}
          >
            <Plus className="size-4" />
            여행 만들기
          </Link>
        </EmptyState>
      </div>
    );
  }

  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-6">
      <PageHeader title="여행" description={describeState(summary, featured)} />
      {featured && (
        <FeaturedTripCard
          trip={featured}
          cities={summary?.ongoing?.cities ?? summary?.next?.cities}
          prep={summary?.ongoing?.prep ?? summary?.next?.prep}
        />
      )}
      {/* 진행 중 여행에 가려진 다음 여행. 첫 구간 도시와 D-day만 한 줄로 남긴다. */}
      {summary?.ongoing && summary.next && <NextTripRow trip={summary.next} />}
      {summary?.recentCompleted && (
        <RecentCompleted trip={summary.recentCompleted} />
      )}
    </div>
  );
}

/** 헤더 설명 — 진행 중이면 그 여행을, 아니면 다음 여행까지 남은 일수를 말한다. */
function describeState(
  summary: TravelSummary | undefined,
  featured: TripDetail | undefined,
): string | undefined {
  if (summary?.ongoing) return `${summary.ongoing.title} 여행 중이에요.`;
  if (!featured) return undefined;

  const days = daysUntil(featured.startDate, firstDayZone(featured.timezone));
  if (days <= 0) return "오늘 출발이에요.";
  return `진행 중인 여행이 없어요. 다음 여행까지 ${days}일.`;
}

function FeaturedTripCard({
  trip,
  cities,
  prep,
}: {
  trip: TripDetail;
  cities: TripCitySummary | undefined;
  prep: PrepSummary | undefined;
}) {
  const ongoing = trip.status === "ONGOING";
  // 서버가 준 dDay를 그대로 쓰지 않고 다시 계산한다 — 오프라인 캐시(4단계)로 어제 응답이
  // 돌아와도 숫자가 맞아야 한다. 기준은 첫날 도시다(`trip.timezone`이 그 값이다).
  const days = daysUntil(trip.startDate, firstDayZone(trip.timezone));
  const chips = dayChips(trip.startDate, trip.endDate);
  const cityCount = formatCityCount(cities?.count ?? 0);
  const todayCity = formatTodayCity(cities);
  // 진행 중이면 <b>오늘 도시의</b> 타임존·통화다 — 여행 하나에 값 하나이던 v2.0과 다르다.
  const timezone = cities?.todayTimezone ?? trip.timezone;
  const currency = cities?.todayCurrency ?? trip.currency;

  return (
    <section className="bg-card ring-foreground/10 flex flex-col gap-4 rounded-xl py-4 ring-1">
      <header className="grid grid-cols-[1fr_auto] items-start gap-1 px-4">
        <div className="col-start-1 min-w-0">
          <Badge variant={ongoing ? "success" : "info"}>
            {ongoing ? "진행 중" : "예정"}
          </Badge>
          <h2 className="text-heading mt-1 font-medium">{trip.title}</h2>
          <p className="text-muted-foreground text-sm">
            {formatPeriod(trip.startDate, trip.endDate)}
            {cityCount && ` · ${cityCount}`} · 일정 {trip.activityCount}개
          </p>
        </div>
        <div className="col-start-2 text-right">
          <p className="text-display text-primary font-semibold tabular-nums">
            {ongoing && cities?.todayDayIndex
              ? `${cities.todayDayIndex}일차`
              : formatDDay(days)}
          </p>
          <p className="text-caption text-muted-foreground">
            {timezone} · {currency}
          </p>
        </div>
      </header>
      {/* 오늘 어디에 있는지가 이 카드에서 가장 먼저 읽혀야 한다. 옮기는 날이면 `A → B`. */}
      {todayCity && (
        <div className="px-4">
          <p className="bg-accent text-accent-foreground flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium">
            <MapPin className="size-[15px] shrink-0" />
            오늘 · {todayCity}
          </p>
        </div>
      )}
      <div className="px-4">
        {/* 날씨(3행)는 4단계. 1단계는 일차·요일만 채운다. */}
        <ul className="flex gap-2 overflow-x-auto pb-1">
          {chips.map((chip) => (
            <li
              key={chip.date}
              className="border-border min-w-[84px] shrink-0 rounded-lg border px-2.5 py-2"
            >
              <p className="text-caption text-muted-foreground">
                {chip.dayIndex}일차 · {chip.weekday}
              </p>
            </li>
          ))}
        </ul>
      </div>
      {/*
        준비 줄. <b>적은 게 하나도 없으면 줄 자체를 숨긴다</b>(§7) — 「0/0」은 아무것도
        알려주지 않으면서 자리만 차지한다. 경비 줄은 경비 API가 선 뒤에 여기 붙는다(#1328).
      */}
      {prep && prep.total > 0 && (
        <div className="px-4">
          <Link
            to={`/travel/trips/${trip.id}/prep`}
            className="border-border hover:bg-muted flex items-center gap-2 rounded-lg border px-3 py-2.5 text-sm transition-colors"
          >
            <SquareCheckBig className="text-muted-foreground size-[15px] shrink-0" />
            <span className="tabular-nums">
              준비 {prep.done}/{prep.total}
            </span>
            {prep.overdueCount > 0 && (
              <span className="text-destructive ml-auto flex items-center gap-1 text-[13px] font-semibold">
                <TriangleAlert className="size-3.5" />
                기한 지난 것 {prep.overdueCount}개
              </span>
            )}
          </Link>
        </div>
      )}
      <footer className="bg-muted/50 flex items-center gap-2 rounded-b-xl border-t p-4">
        <Link
          to={`/travel/trips/${trip.id}/board`}
          className={buttonVariants({ variant: "default" })}
        >
          <CalendarDays className="size-4" />
          일정 보드 열기
        </Link>
        <Link
          to="/travel/trips/new"
          className={buttonVariants({ variant: "outline" })}
        >
          <Plus className="size-4" />
          여행 만들기
        </Link>
      </footer>
    </section>
  );
}

/** 시작 당일은 D-DAY, 이미 시작했으면 D+n. */
function formatDDay(days: number): string {
  if (days > 0) return `D-${days}`;
  if (days === 0) return "D-DAY";
  return `D+${-days}`;
}

/** 진행 중 여행 뒤에 오는 다음 여행 — 첫 구간 도시와 D-day만. */
function NextTripRow({ trip }: { trip: NextTripSummary }) {
  const where = formatCities(trip.cities) || trip.destinationName;
  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-caption text-muted-foreground font-semibold">
        다음 여행
      </h2>
      <Link
        to={`/travel/trips/${trip.id}/board`}
        className="bg-card ring-foreground/10 hover:ring-primary flex items-center justify-between gap-3 rounded-xl p-4 ring-1 transition-all"
      >
        <span className="min-w-0">
          <span className="block truncate text-[15px] font-medium">
            {trip.title}
          </span>
          <span className="text-muted-foreground block truncate text-[13px]">
            {where}
          </span>
        </span>
        <span className="text-primary shrink-0 text-sm font-semibold tabular-nums">
          {formatDDay(trip.dDay)}
        </span>
      </Link>
    </section>
  );
}

function RecentCompleted({ trip }: { trip: CompletedTripSummary }) {
  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-caption text-muted-foreground font-semibold">
        최근 완료
      </h2>
      <Link
        to={`/travel/trips/${trip.id}/board`}
        className="bg-card ring-foreground/10 hover:ring-primary flex items-center justify-between gap-3 rounded-xl p-4 ring-1 transition-all"
      >
        <span className="min-w-0">
          <span className="block truncate text-[15px] font-medium">
            {trip.title}
          </span>
          <span className="text-muted-foreground block text-[13px]">
            {formatPeriod(trip.endDate, trip.endDate)} 종료 · 일정{" "}
            {trip.activityCount}개
          </span>
        </span>
        <ChevronRight className="text-muted-foreground size-4 shrink-0" />
      </Link>
    </section>
  );
}
