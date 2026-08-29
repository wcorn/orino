import {
  ChevronLeft,
  ChevronRight,
  Search,
  TrendingDown,
  TrendingUp,
} from "lucide-react";
import { useState } from "react";
import { useSearchParams } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { LoadingText } from "@/components/ui/loading-text";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type {
  AssetStat,
  CategoryStat,
  FixedVsVariable,
  LedgerPerspective,
  LedgerSettlement,
  MonthlyPoint,
  SearchResponse,
  StatsComparisonBucket,
} from "@/features/ledger/api/ledger";
import { searchTransactions } from "@/features/ledger/api/ledger";
import { useLedgerStats } from "@/features/ledger/hooks/useLedgerQueries";
import { formatAmount, MINUS } from "@/features/ledger/lib/money";
import { cn } from "@/lib/utils";

/**
 * 도넛의 반지름. 둘레가 정확히 100이 되도록 잡은 값(2πr = 100)이라
 * `stroke-dasharray`에 퍼센트를 그대로 넣을 수 있다.
 */
const DONUT_RADIUS = 15.9155;

const PERSPECTIVE_LABELS: Record<LedgerPerspective, string> = {
  SPEND: "소비 기준",
  BILLING: "청구 기준",
};

/**
 * 통계 `/ledger/stats`.
 *
 * <p><b>관점 전환이 v2에서 열렸다</b>(`LDG-086`). v1에서 토글을 안 그린 이유는 할부가 없으면
 * 두 관점이 같은 값이라 아무 일도 안 하는 것처럼 보였기 때문이고, 이제 할부가 있다.
 *
 * <p>전환했을 때 얼마가 달라지는지와 <b>왜 달라지는지</b>는 서버가 계산해 준다 — 화면이 다시
 * 세면 어느 쪽이 맞는지 알 수 없다(D-13).
 *
 * <p>색은 <b>primary 한 색의 농도만</b> 바꾼다(모노크롬 램프). 카테고리마다 새 hue를 주면
 * 색이 의미를 갖는 것처럼 보이는데, 실제로는 순서일 뿐이다.
 */
export function LedgerStatsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const period = searchParams.get("period") ?? undefined;
  const perspective = (searchParams.get("perspective") ?? undefined) as
    | LedgerPerspective
    | undefined;
  const { data, isPending, isError } = useLedgerStats(period, perspective);

  const shift = (months: number) => {
    const base = data ? new Date(`${data.period.start}T00:00:00`) : new Date();
    const next = new Date(base.getFullYear(), base.getMonth() + months, 1);
    const label = `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, "0")}`;
    searchParams.set("period", label);
    setSearchParams(searchParams);
  };

  const setPerspective = (value: string) => {
    searchParams.set("perspective", value);
    setSearchParams(searchParams);
  };

  return (
    <div className="mx-auto flex max-w-[880px] flex-col gap-5">
      <PageHeader title="통계" />

      <div className="flex flex-wrap items-center gap-3">
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

        {data && (
          <Tabs value={data.perspective} onValueChange={setPerspective}>
            <TabsList>
              <TabsTrigger value="SPEND">소비 기준</TabsTrigger>
              <TabsTrigger value="BILLING">청구 기준</TabsTrigger>
            </TabsList>
          </Tabs>
        )}
      </div>

      {isPending && <LoadingText />}
      {isError && (
        <Alert variant="destructive">통계를 불러오지 못했어요.</Alert>
      )}

      {/*
        전환하면 얼마가 달라지는지 한 줄로 알린다. 벌어지지 않으면 그리지 않는다 —
        할부가 없는 달에 「0원 달라집니다」는 아무 말도 아니다.
      */}
      {data && data.perspectiveDiff.reason && (
        <Alert variant="info">
          <AlertTitle>
            {PERSPECTIVE_LABELS[data.perspectiveDiff.other]}으로 보면{" "}
            {data.perspectiveDiff.diff > 0 ? "+" : MINUS}
            {formatAmount(data.perspectiveDiff.diff)} 달라집니다(
            {data.perspectiveDiff.reason})
          </AlertTitle>
          <AlertDescription>
            <p>
              {PERSPECTIVE_LABELS[data.perspective]} {formatAmount(data.total)}{" "}
              · {PERSPECTIVE_LABELS[data.perspectiveDiff.other]}{" "}
              {formatAmount(data.perspectiveDiff.otherTotal)}. 청구서·예정
              화면은 토글과 무관하게 언제나 청구 기준이에요.
            </p>
          </AlertDescription>
        </Alert>
      )}

      {/*
        이 달에 쓴 돈이 없어도 <b>추이·연간 결산·검색은 남는다</b> — 비어 있는 것은
        카테고리 몫뿐이고, 한 달 쉬었다고 지난 열두 달까지 감출 이유가 없다.
      */}
      {data && data.byCategory.length === 0 && (
        <EmptyState className="min-h-[20svh]">
          <p className="text-muted-foreground text-sm">
            이 기간에 쓴 돈이 없어요.
          </p>
        </EmptyState>
      )}

      {data && (
        <>
          {data.byCategory.length > 0 && (
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
          )}

          <div className="grid items-start gap-4 md:grid-cols-2">
            <FixedVariableCard split={data.fixedVsVariable} />
            <AssetCard stats={data.byAsset} perspective={data.perspective} />
          </div>

          <MonthlyTrend points={data.monthly} />
          <SettlementCard settlement={data.settlement} />

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

      {data && <SearchPanel period={data.period} />}
    </div>
  );
}

/**
 * 고정 대 변동.
 *
 * <p><b>속성을 안 정한 지출을 변동비에 몰아넣지 않는다</b> — 그러면 아무도 분류하지 않은
 * 가계부에서 「변동비가 100%」라는 거짓말이 나온다. 분류가 덜 됐다는 사실 자체가 읽혀야 한다.
 */
function FixedVariableCard({ split }: { split: FixedVsVariable }) {
  const total = split.fixed + split.variable + split.unclassified;
  const pct = (value: number) => (total === 0 ? 0 : (value / total) * 100);

  return (
    <section className="bg-card ring-foreground/10 flex flex-col gap-3 rounded-xl p-5 ring-1">
      <header className="flex flex-col">
        <h2 className="text-sm font-semibold">고정 대 변동</h2>
        <p className="text-muted-foreground text-[13px]">
          절약 여지가 어디에 있는지 가르는 구분이에요
        </p>
      </header>

      <div
        className="bg-muted flex h-2.5 overflow-hidden rounded-full"
        role="img"
        aria-label={`고정 ${formatAmount(split.fixed)}, 변동 ${formatAmount(split.variable)}, 미분류 ${formatAmount(split.unclassified)}`}
      >
        <span
          className="bg-primary h-full"
          style={{ width: `${pct(split.fixed)}%` }}
        />
        <span
          className="h-full bg-[color-mix(in_oklab,var(--primary)_45%,var(--muted))]"
          style={{ width: `${pct(split.variable)}%` }}
        />
      </div>

      <dl className="flex flex-col gap-1 text-sm">
        <Row label="고정비" value={formatAmount(split.fixed)} />
        <Row label="변동비" value={formatAmount(split.variable)} />
        {split.unclassified > 0 && (
          <Row
            label="아직 안 정함"
            value={formatAmount(split.unclassified)}
            muted
          />
        )}
      </dl>

      {split.unclassified > 0 && (
        <p className="text-muted-foreground text-[13px]">
          설정에서 카테고리마다 고정비·변동비를 정하면 여기로 나뉘어요.
        </p>
      )}
    </section>
  );
}

/**
 * 자산별 지출(`LDG-082`).
 *
 * <p><b>설명이 관점을 따라간다.</b> 청구 기준에서는 아직 청구되지 않은 카드 사용이 목록에
 * 없는데, 「카드는 사용 기준」이라고 적혀 있으면 빠진 카드를 버그로 읽게 된다.
 */
function AssetCard({
  stats,
  perspective,
}: {
  stats: AssetStat[];
  perspective: LedgerPerspective;
}) {
  return (
    <section className="bg-card ring-foreground/10 flex flex-col gap-3 rounded-xl p-5 ring-1">
      <header className="flex flex-col">
        <h2 className="text-sm font-semibold">어디서 나갔나</h2>
        <p className="text-muted-foreground text-[13px]">
          {perspective === "SPEND"
            ? "카드는 긁은 날 기준 · 대금 납부는 이체라 여기 없어요"
            : "청구된 것만 있어요 · 아직 청구 전인 카드 사용은 빠집니다"}
        </p>
      </header>
      {stats.length === 0 ? (
        <p className="text-muted-foreground text-[13px]">쓴 돈이 없어요.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {stats.map((stat, index) => (
            <li key={stat.assetId} className="flex flex-col gap-1">
              <span className="flex items-center justify-between gap-2 text-sm">
                <span className="truncate">{stat.assetName ?? "자산"}</span>
                <span className="shrink-0 tabular-nums">
                  {formatAmount(stat.amount)}
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
      )}
    </section>
  );
}

/**
 * 6개월 추이 — 고정은 실선, 변동은 점선.
 *
 * <p><b>고정비 비중이 계속 오르면 커피를 줄일 게 아니라 정기 항목을 정리해야 한다.</b>
 * 그 판단은 총액만 보는 화면에서는 절대 나오지 않는다.
 */
function MonthlyTrend({ points }: { points: MonthlyPoint[] }) {
  const recent = points.slice(-6);
  if (recent.length === 0) {
    return null;
  }
  const max = Math.max(...recent.map((point) => point.expense), 1);
  const anyUnclassified = recent.some((point) => point.unclassified > 0);
  // 실제로 쓴 달만 센다. 빈 달을 0으로 세면 첫 달부터 「오르는 중」이 된다 —
  // 점 하나로 기울기를 말하는 셈이라, 가계부를 막 시작한 사람에게 언제나 뜬다.
  const spent = recent.filter((point) => point.expense > 0);
  const rising =
    spent.length >= 3 &&
    share(spent[spent.length - 1]) > share(spent[0]) &&
    share(spent[spent.length - 1]) > 0;

  return (
    <section className="bg-card ring-foreground/10 flex flex-col gap-3 rounded-xl p-5 ring-1">
      <header className="flex flex-col">
        <h2 className="text-sm font-semibold">6개월 추이</h2>
        {/* 토글을 따라가지 않는다. 달마다 카드 사이클로 흔들리면 습관이 안 보인다. */}
        <p className="text-muted-foreground text-[13px]">
          추이와 결산은 관점과 무관하게 쓴 날 기준이에요
        </p>
      </header>
      <div className="flex items-end gap-2">
        {recent.map((point) => (
          <span
            key={point.month}
            className="flex flex-1 flex-col items-center gap-1"
          >
            <span
              className="flex w-full flex-col justify-end"
              style={{ height: 96 }}
            >
              <span
                className="bg-primary w-full rounded-t-sm"
                style={{ height: `${(point.fixed / max) * 96}px` }}
              />
              <span
                className="w-full bg-[color-mix(in_oklab,var(--primary)_45%,var(--muted))]"
                style={{ height: `${(point.variable / max) * 96}px` }}
              />
              {/* 안 정한 몫도 쌓는다 — 빼면 막대가 그 달 지출보다 짧다. */}
              <span
                className="bg-muted w-full rounded-b-sm"
                style={{ height: `${(point.unclassified / max) * 96}px` }}
              />
            </span>
            <span className="text-caption text-muted-foreground tabular-nums">
              {Number(point.month.slice(5))}월
            </span>
          </span>
        ))}
      </div>
      {rising && (
        <p className="text-muted-foreground text-[13px]">
          고정비 비중이 오르고 있어요 — 절약 여지는 변동비보다 정기 항목 정리에
          있습니다.
        </p>
      )}
      {anyUnclassified && (
        <p className="text-muted-foreground text-[13px]">
          연한 칸은 아직 고정·변동을 안 정한 지출이에요.
        </p>
      )}
    </section>
  );
}

function share(point: MonthlyPoint): number {
  const total = point.fixed + point.variable;
  return total === 0 ? 0 : point.fixed / total;
}

/** 연간 결산. 저축률은 수입이 없으면 <b>「−」</b>다 — 0%는 「못 모았다」로 읽힌다. */
function SettlementCard({ settlement }: { settlement: LedgerSettlement }) {
  return (
    <section className="bg-card ring-foreground/10 flex flex-col gap-2 rounded-xl p-5 ring-1">
      <h2 className="text-sm font-semibold">{settlement.year}년 결산</h2>
      <dl className="flex flex-col gap-1 text-sm">
        <Row label="수입" value={formatAmount(settlement.income)} />
        <Row label="지출" value={formatAmount(settlement.expense)} />
        <Row
          label="저축률"
          value={
            settlement.savingRate === null
              ? "—"
              : `${Math.round(settlement.savingRate * 100)}%`
          }
          strong
        />
      </dl>
      {/* 쓴 달이 하나뿐이면 최다와 최소가 같은 달이다 — 그걸 적으면 사람이 되짚어 본다. */}
      {settlement.highestMonth &&
        settlement.highestMonth !== settlement.lowestMonth && (
          <p className="text-muted-foreground text-[13px]">
            가장 많이 쓴 달 {Number(settlement.highestMonth.slice(5))}월 · 가장
            적게 쓴 달 {Number(settlement.lowestMonth?.slice(5))}월
          </p>
        )}
      <p className="text-muted-foreground text-[13px]">
        결산 제외로 표시한 카테고리는 빠져요 — 저축·투자는 쓴 돈이 아니라 자산
        이동이니까요.
      </p>
    </section>
  );
}

/**
 * 복합 검색(§10.2).
 *
 * <p>결과가 <b>잘렸으면 그 사실을 말한다</b> — 모르고 일괄 편집을 하면 「전부 고쳤다」고
 * 믿은 채 일부만 바뀐다.
 */
function SearchPanel({ period }: { period: { start: string; end: string } }) {
  const [keyword, setKeyword] = useState("");
  const [minAmount, setMinAmount] = useState("");
  const [result, setResult] = useState<SearchResponse | null>(null);
  const [pending, setPending] = useState(false);

  const run = async () => {
    setPending(true);
    try {
      setResult(
        await searchTransactions({
          from: period.start,
          to: period.end,
          keyword: keyword.trim() === "" ? null : keyword.trim(),
          minAmount: minAmount === "" ? null : Number(minAmount),
        }),
      );
    } finally {
      setPending(false);
    }
  };

  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-[13px] font-semibold">찾아보기</h2>
      <div className="flex flex-wrap items-end gap-2">
        <div className="flex min-w-[180px] flex-1 flex-col gap-1.5">
          <Label htmlFor="search-keyword">내용·메모</Label>
          <Input
            id="search-keyword"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                void run();
              }
            }}
            placeholder="스타벅스"
          />
        </div>
        <div className="flex w-[140px] flex-col gap-1.5">
          <Label htmlFor="search-min">최소 금액</Label>
          <Input
            id="search-min"
            inputMode="numeric"
            value={minAmount}
            onChange={(event) => setMinAmount(event.target.value)}
          />
        </div>
        <Button type="button" onClick={() => void run()} disabled={pending}>
          <Search className="size-4" />
          찾기
        </Button>
      </div>

      {result && (
        <>
          <p className="text-muted-foreground text-[13px]">
            {result.count}건 · 지출 합계 {formatAmount(result.total)}
          </p>
          {/* 잘린 사실을 숨기지 않는다. */}
          {result.truncated && (
            <Alert variant="warning">
              <AlertTitle>결과가 잘렸어요</AlertTitle>
              <AlertDescription>
                <p>
                  조건에 맞는 건이 더 있습니다. 기간이나 조건을 좁혀 주세요 —
                  이대로 일괄 편집하면 일부만 바뀝니다.
                </p>
              </AlertDescription>
            </Alert>
          )}
          <ul className="flex flex-col">
            {result.items.slice(0, 20).map((item) => (
              <li
                key={item.id}
                className="border-border flex items-center justify-between gap-3 border-b py-2 text-sm last:border-b-0"
              >
                <span className="flex min-w-0 items-center gap-2">
                  <span className="text-muted-foreground text-[13px] tabular-nums">
                    {item.occurredOn}
                  </span>
                  <span className="truncate">{item.title ?? "제목 없음"}</span>
                  {item.categoryName && (
                    <Badge variant="outline">{item.categoryName}</Badge>
                  )}
                </span>
                <span className="shrink-0 tabular-nums">
                  {formatAmount(item.amount)}
                </span>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
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

function Row({
  label,
  value,
  muted,
  strong,
}: {
  label: string;
  value: string;
  muted?: boolean;
  strong?: boolean;
}) {
  return (
    <div className="flex items-baseline justify-between">
      <span className={cn(muted && "text-muted-foreground")}>{label}</span>
      <span
        className={cn(
          "tabular-nums",
          muted && "text-muted-foreground",
          strong && "font-semibold",
        )}
      >
        {value}
      </span>
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
