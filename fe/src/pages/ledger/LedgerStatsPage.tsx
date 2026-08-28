import {
  ChevronLeft,
  ChevronRight,
  TrendingDown,
  TrendingUp,
} from "lucide-react";
import { useSearchParams } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingText } from "@/components/ui/loading-text";
import type {
  CategoryStat,
  StatsComparisonBucket,
} from "@/features/ledger/api/ledger";
import { useLedgerStats } from "@/features/ledger/hooks/useLedgerQueries";
import { formatAmount, MINUS } from "@/features/ledger/lib/money";
import { cn } from "@/lib/utils";

/**
 * 도넛의 반지름. 둘레가 정확히 100이 되도록 잡은 값(2πr = 100)이라
 * `stroke-dasharray`에 퍼센트를 그대로 넣을 수 있다.
 */
const DONUT_RADIUS = 15.9155;

/**
 * 카테고리 통계 `/ledger/stats`.
 *
 * <p><b>관점 전환(소비/청구)은 v2다.</b> 여기에 토글을 그리지 않는다 — 할부가 없으면 두 관점이
 * 같은 값이라, 토글이 아무 일도 안 하는 것처럼 보인다.
 *
 * <p>색은 <b>primary 한 색의 농도만</b> 바꾼다(모노크롬 램프). 카테고리마다 새 hue를 주면
 * 색이 의미를 갖는 것처럼 보이는데, 실제로는 순서일 뿐이다.
 */
export function LedgerStatsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const period = searchParams.get("period") ?? undefined;
  const { data, isPending, isError } = useLedgerStats(period);

  const shift = (months: number) => {
    const base = data ? new Date(`${data.period.start}T00:00:00`) : new Date();
    const next = new Date(base.getFullYear(), base.getMonth() + months, 1);
    const label = `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, "0")}`;
    searchParams.set("period", label);
    setSearchParams(searchParams);
  };

  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-5">
      <PageHeader title="통계" />

      <div className="flex items-center gap-1">
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label="이전 달"
          onClick={() => shift(-1)}
        >
          <ChevronLeft className="size-4" />
        </Button>
        <span className="min-w-[104px] text-center text-sm font-medium">
          {data ? monthLabel(data.period.label) : "…"}
        </span>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label="다음 달"
          onClick={() => shift(1)}
        >
          <ChevronRight className="size-4" />
        </Button>
      </div>

      {isPending && <LoadingText />}
      {isError && (
        <Alert variant="destructive">통계를 불러오지 못했어요.</Alert>
      )}

      {data && data.byCategory.length === 0 && (
        <EmptyState className="min-h-[30svh]">
          <p className="text-muted-foreground text-sm">
            이 기간에 쓴 돈이 없어요.
          </p>
        </EmptyState>
      )}

      {data && data.byCategory.length > 0 && (
        <>
          <div className="flex flex-col items-center gap-4 sm:flex-row sm:items-start">
            <Donut stats={data.byCategory} total={data.total} />
            <ul className="flex w-full min-w-0 flex-col gap-2">
              {data.byCategory.map((stat, index) => (
                <li
                  key={stat.categoryId ?? "uncategorized"}
                  className="flex flex-col gap-1"
                >
                  <span className="flex items-center justify-between gap-2 text-sm">
                    <span className="flex min-w-0 items-center gap-2">
                      <span
                        aria-hidden
                        className="size-2.5 shrink-0 rounded-full"
                        style={{ background: rampColor(index) }}
                      />
                      {/* 미분류를 빼지 않는다 — 안 보이면 정리하지 않는다. */}
                      <span className="truncate">
                        {stat.categoryName ?? "미분류"}
                      </span>
                    </span>
                    <span className="shrink-0 tabular-nums">
                      {formatAmount(stat.amount)}
                      <span className="text-muted-foreground ml-1.5 text-[13px]">
                        {Math.round(stat.share * 100)}%
                      </span>
                    </span>
                  </span>
                  <span
                    aria-hidden
                    className="bg-muted h-1.5 overflow-hidden rounded-full"
                  >
                    <span
                      className="block h-full rounded-full"
                      style={{
                        width: `${stat.share * 100}%`,
                        background: rampColor(index),
                      }}
                    />
                  </span>
                </li>
              ))}
            </ul>
          </div>

          <section className="flex flex-col gap-2">
            <h2 className="text-[13px] font-semibold">견줘 보기</h2>
            <Comparison
              label="지난 달"
              bucket={data.comparison.previousPeriod}
            />
            <Comparison
              label="작년 같은 달"
              bucket={data.comparison.previousYear}
            />
          </section>
        </>
      )}
    </div>
  );
}

/**
 * 도넛. 둘레가 100인 원 하나에 `stroke-dasharray`로 조각을 얹는다 —
 * 조각마다 path를 만들 필요가 없고, 퍼센트를 그대로 쓸 수 있다.
 */
function Donut({ stats, total }: { stats: CategoryStat[]; total: number }) {
  let offset = 0;
  return (
    <div className="relative shrink-0">
      <svg
        viewBox="0 0 36 36"
        role="img"
        aria-label="카테고리 분포"
        className="size-40"
      >
        {stats.map((stat, index) => {
          const dash = stat.share * 100;
          const element = (
            <circle
              key={stat.categoryId ?? "uncategorized"}
              cx="18"
              cy="18"
              r={DONUT_RADIUS}
              fill="none"
              stroke={rampColor(index)}
              strokeWidth="3.6"
              strokeDasharray={`${dash} ${100 - dash}`}
              // 12시 방향에서 시작해 시계 방향으로 돈다.
              strokeDashoffset={25 - offset}
            />
          );
          offset += dash;
          return element;
        })}
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="text-muted-foreground text-[13px]">이미 쓴 돈</span>
        <span className="text-heading font-semibold tabular-nums">
          {formatAmount(total)}
        </span>
      </div>
    </div>
  );
}

function Comparison({
  label,
  bucket,
}: {
  label: string;
  bucket: StatsComparisonBucket;
}) {
  const more = bucket.diff > 0;
  const Icon = more ? TrendingUp : TrendingDown;
  return (
    <div className="bg-muted flex items-center justify-between rounded-lg px-4 py-2.5 text-sm">
      <span className="text-muted-foreground">
        {label} {formatAmount(bucket.total)}
      </span>
      {bucket.diff === 0 ? (
        <span className="text-muted-foreground text-[13px]">같아요</span>
      ) : (
        <span
          className={cn(
            "flex items-center gap-1.5 tabular-nums",
            // 더 쓴 것은 경고가 아니라 사실이다. 색으로 겁주지 않는다.
            "text-foreground",
          )}
        >
          <Icon className="text-muted-foreground size-3.5" />
          {more ? "+" : MINUS}
          {formatAmount(bucket.diff)}
        </span>
      )}
    </div>
  );
}

/**
 * primary 모노크롬 램프. 새 hue를 만들지 않는다 — 색은 순서를 나타낼 뿐이고,
 * 카테고리마다 다른 색을 주면 그 색이 의미를 갖는 것처럼 읽힌다.
 */
function rampColor(index: number): string {
  const steps = [100, 78, 60, 44, 30, 16];
  const step = steps[Math.min(index, steps.length - 1)];
  return `color-mix(in oklab, var(--primary) ${step}%, var(--muted))`;
}

/** `2026-08` → `2026년 8월`. */
function monthLabel(label: string): string {
  const [year, month] = label.split("-");
  return `${year}년 ${Number(month)}월`;
}
