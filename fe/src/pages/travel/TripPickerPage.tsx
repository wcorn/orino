import { ChevronRight, Plus, TriangleAlert } from "lucide-react";
import { Link } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingText } from "@/components/ui/loading-text";
import { formatCompactAmount } from "@/features/ledger/lib/money";
import type { SidebarTripSummary } from "@/features/travel/api/travel";
import { useTravelSummary } from "@/features/travel/hooks/useTravelSummary";
import { formatShortDate } from "@/features/travel/lib/tripStatus";

/** 이 화면이 대신 열어 주는 탭. 라우트 두 개가 같은 컴포넌트를 쓴다. */
export type PickerScope = "prep" | "expenses";

const COPY: Record<
  PickerScope,
  { title: string; question: string; empty: string }
> = {
  prep: {
    title: "준비",
    question: "어느 여행의 준비인가요?",
    empty:
      "준비 목록은 여행마다 따로 있어요. 여행을 먼저 만들면 여기에 목록이 생깁니다.",
  },
  expenses: {
    title: "경비",
    question: "어느 여행의 경비인가요?",
    empty:
      "경비는 여행마다 따로 봅니다. 여행을 먼저 만들면 여기에 목록이 생깁니다.",
  },
};

/**
 * 여행이 정해지지 않은 진입 — `/travel/prep` · `/travel/expenses` (프레임 `2b`).
 *
 * <p><b>빈 화면도, 조용한 리다이렉트도 아니다. 고르게 한다</b>(D-38). 예전에는 여행을 못
 * 정하면 여행 목록으로 튕겼는데(#1337), 목록에 도착한 사용자는 자기가 왜 거기 왔는지 모른다.
 *
 * <p><b>여기서 대신 골라 주지 않는다.</b> 진행 중 여행이 있더라도 그 여행으로 넘겨 버리면,
 * 지운 여행의 링크로 들어온 사람이 <b>다른 여행의 준비를 자기가 찾던 화면으로 읽는다</b> —
 * 조용한 리다이렉트가 막으려던 바로 그 상태다. 기본 여행 판정(진행 중 → 예정 → 마지막으로
 * 본 여행)은 <b>사이드바가</b> 링크를 만들 때 쓰고, 그게 실패했을 때 여기로 온다.
 *
 * <p>대신 고른 것은 기억한다 — 고르고 나면 사이드바가 그 여행을 편다({@code lastTripId}).
 */
export function TripPickerPage({ scope }: { scope: PickerScope }) {
  const { data, isPending } = useTravelSummary();
  const copy = COPY[scope];

  if (isPending) {
    return (
      <div className="grid min-h-[40svh] place-items-center">
        <LoadingText />
      </div>
    );
  }

  const trips = data?.trips ?? [];

  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-6">
      <PageHeader title={copy.title} description={copy.question} />
      {trips.length === 0 ? (
        <EmptyState>
          <p className="text-muted-foreground max-w-[380px] text-sm">
            {copy.empty}
          </p>
          <Link
            to="/travel/trips/new"
            className={buttonVariants({ variant: "outline", size: "sm" })}
          >
            <Plus className="size-4" />
            여행 만들기
          </Link>
        </EmptyState>
      ) : (
        <div className="flex flex-col gap-3">
          <ul className="flex flex-col gap-2">
            {trips.map((trip) => (
              <li key={trip.id}>
                <TripPickerRow trip={trip} scope={scope} />
              </li>
            ))}
          </ul>
          <div className="flex items-center justify-between gap-3">
            {/* 고르는 일이 한 번으로 끝난다는 사실을 여기서 말해 준다. */}
            <p className="text-muted-foreground text-[13px]">
              한 번 고르면 그 여행을 기억합니다.
            </p>
            <Link
              to="/travel/trips/new"
              className={buttonVariants({ variant: "outline", size: "sm" })}
            >
              <Plus className="size-4" />
              여행 만들기
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * 여행 카드 한 장. <b>고르기 전에 무엇을 고르는지 보여 준다</b> — 기간과 준비·경비 진행이
 * 함께 온다. 제목만 늘어놓으면 두 여행 중 어느 쪽이 지금 급한지 알 수 없다.
 */
function TripPickerRow({
  trip,
  scope,
}: {
  trip: SidebarTripSummary;
  scope: PickerScope;
}) {
  const ongoing = trip.status === "ONGOING";
  return (
    <Link
      to={`/travel/trips/${trip.id}/${scope}`}
      className="bg-card ring-foreground/10 hover:ring-primary flex items-center justify-between gap-3 rounded-xl px-4 py-3.5 ring-1 transition-all"
    >
      <span className="min-w-0">
        <span className="flex items-center gap-2">
          <Badge variant={ongoing ? "success" : "info"}>
            {ongoing ? "진행 중" : "예정"}
          </Badge>
          <span className="truncate text-[15px] font-medium">{trip.title}</span>
        </span>
        {/* 메타는 한 문자열이다 — 조각으로 나누면 「준비 18」 / 「/24」로 갈라진다. */}
        <span className="text-muted-foreground mt-1 block text-[13px]">
          {metaLine(trip)}
        </span>
        {/*
          기한 지남은 카드에서 가장 급한 한 줄이다 — 「고르고 나서 알게 되는」 것이 아니라
          고르기 전에 보여야 한다(§13).
        */}
        {trip.prep.overdueCount > 0 && (
          <span className="text-destructive mt-1 flex items-center gap-1 text-[13px]">
            <TriangleAlert className="size-3.5 shrink-0" />
            기한 지난 것 {trip.prep.overdueCount}개
          </span>
        )}
      </span>
      <span className="flex shrink-0 items-center gap-1.5">
        <span className="text-muted-foreground text-[13px] tabular-nums">
          {dayLabel(trip)}
        </span>
        <ChevronRight className="text-muted-foreground size-4" />
      </span>
    </Link>
  );
}

/** 「10.24 – 10.29 · 준비 18/24 · 경비 41.2만」. 없는 조각은 아예 빼고 이어 붙인다. */
function metaLine(trip: SidebarTripSummary): string {
  const parts = [
    `${formatShortDate(trip.startDate)} – ${formatShortDate(trip.endDate)}`,
  ];
  if (trip.prep.total > 0) {
    parts.push(`준비 ${trip.prep.done}/${trip.prep.total}`);
  }
  if (trip.expense.spent > 0) {
    parts.push(`경비 ${formatCompactAmount(trip.expense.spent)}`);
  }
  return parts.join(" · ");
}

/** 「4일차」 / 「D-49」. 서버가 여행 타임존으로 낸 값이라 여기서 다시 세지 않는다. */
function dayLabel(trip: SidebarTripSummary): string {
  if (trip.dayNumber != null) return `${trip.dayNumber}일차`;
  if (trip.dDay == null) return "";
  return trip.dDay === 0 ? "D-day" : `D-${trip.dDay}`;
}
