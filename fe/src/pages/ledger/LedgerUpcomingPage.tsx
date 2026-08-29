import { TrendingDown } from "lucide-react";
import { useMemo, useState } from "react";

import { PageHeader } from "@/components/PageHeader";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingText } from "@/components/ui/loading-text";
import type { UpcomingItem, UpcomingKind } from "@/features/ledger/api/ledger";
import { OverdueAlert } from "@/features/ledger/components/OverdueAlert";
import { useLedgerUpcoming } from "@/features/ledger/hooks/useLedgerQueries";
import {
  balanceToneClass,
  formatDday,
  UPCOMING_KIND_LABELS,
} from "@/features/ledger/lib/balance";
import {
  formatAmount,
  formatBalance,
  formatDateHeader,
} from "@/features/ledger/lib/money";
import { cn } from "@/lib/utils";

type KindFilter = "ALL" | UpcomingKind;

const KIND_ORDER: UpcomingKind[] = [
  "RECURRING",
  "ONE_OFF",
  "CARD_PAYMENT",
  "INSTALLMENT",
];

/** 기본 30일. 「더 보기」로 넓힌다 — 최대 12개월이고 그 너머는 예정이 아니라 추측이다. */
const RANGES = [30, 90, 366];

/**
 * 예정 `/ledger/upcoming`.
 *
 * <p><b>네 출처가 한 목록이다</b> — 정기 회차·직접 예약·카드 대금·할부 잔여(확정 명세 §8.1).
 * 종류 배지를 다는 이유는 색깔이 예뻐서가 아니라 <b>같은 돈이 두 번 세어지지 않았음</b>을
 * 사람이 눈으로 확인할 수 있어야 하기 때문이다.
 *
 * <p>「월말에 얼마 남나」보다 <b>「중간에 모자라지 않나」</b>가 먼저다. 최저 예상 잔액이
 * 스탯 한 칸을 차지하고, 그 날짜와 이유가 경고로 따로 나온다.
 *
 * <p>예상 잔액 곡선은 v2(#1267)다. 이번엔 숫자만.
 */
export function LedgerUpcomingPage() {
  const [days, setDays] = useState(RANGES[0]);
  const [kind, setKind] = useState<KindFilter>("ALL");
  const { data, isPending, isError } = useLedgerUpcoming(days);

  const items = useMemo(
    () =>
      (data?.items ?? []).filter(
        (item) => kind === "ALL" || item.kind === kind,
      ),
    [data?.items, kind],
  );
  const months = useMemo(() => groupByMonth(items), [items]);

  return (
    <div className="mx-auto flex max-w-[880px] flex-col gap-4">
      <PageHeader
        title="예정"
        description={
          data && `${data.from} ~ ${data.to} · 앞으로 ${data.days}일`
        }
      />

      {isPending && <LoadingText />}
      {isError && (
        <Alert variant="destructive">예정을 불러오지 못했어요.</Alert>
      )}

      {data && (
        <>
          <OverdueAlert items={data.items} />

          <div className="grid grid-cols-[repeat(auto-fit,minmax(180px,1fr))] gap-3">
            <StatCard
              label={`예정 출금 ${data.days}일`}
              value={formatAmount(data.stats.outflow)}
            />
            <StatCard
              label="예정 수입"
              value={formatAmount(data.stats.income)}
              tone="text-success"
            />
            <StatCard
              label="최저 예상 잔액"
              value={formatBalance(data.stats.minBalance.amount)}
              tone={balanceToneClass(data.stats.minBalance.amount)}
            />
            <StatCard label="예정 건수" value={String(data.stats.count)} />
          </div>

          {/*
            월말 숫자만 보면 괜찮아 보이는 달이 있다. 바닥이 언제인지, 무엇 때문인지가
            그 자리에서 읽혀야 한다.
          */}
          {data.stats.minBalance.reason && (
            <Alert variant="warning">
              <TrendingDown />
              <AlertTitle>
                {formatDateHeader(data.stats.minBalance.date)} 잔액이{" "}
                {formatBalance(data.stats.minBalance.amount)}까지 내려갑니다
              </AlertTitle>
              <AlertDescription>
                <p>
                  「{data.stats.minBalance.reason}」 직후가 가장 낮은
                  지점이에요. 월말 예상 잔액은{" "}
                  {formatBalance(data.stats.expectedBalance)}입니다.
                </p>
              </AlertDescription>
            </Alert>
          )}

          <div className="flex flex-wrap items-center gap-2">
            <KindChip
              label={`전체 ${data.stats.count}`}
              active={kind === "ALL"}
              onClick={() => setKind("ALL")}
            />
            {KIND_ORDER.filter((value) => data.stats.byKind[value]).map(
              (value) => (
                <KindChip
                  key={value}
                  label={`${UPCOMING_KIND_LABELS[value]} ${data.stats.byKind[value]}`}
                  active={kind === value}
                  onClick={() => setKind(value)}
                />
              ),
            )}
          </div>

          {items.length === 0 ? (
            <EmptyState className="min-h-[30svh]">
              <p className="text-muted-foreground text-sm">
                앞으로 {data.days}일 안에 나갈 돈이 없어요.
              </p>
            </EmptyState>
          ) : (
            months.map(([month, rows]) => (
              <section key={month} className="flex flex-col">
                <h2 className="bg-muted px-4 py-2.5 text-[13px] font-medium">
                  {monthLabel(month)}
                </h2>
                <ul>
                  {rows.map((item) => (
                    <li key={itemKey(item)}>
                      <UpcomingRow item={item} />
                    </li>
                  ))}
                </ul>
              </section>
            ))
          )}

          <div className="flex justify-center pt-1">
            {RANGES.filter((value) => value > days).length > 0 && (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() =>
                  setDays(RANGES.find((value) => value > days) ?? days)
                }
              >
                더 보기 — 최대 12개월
              </Button>
            )}
          </div>
        </>
      )}
    </div>
  );
}

function StatCard({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone?: string;
}) {
  return (
    <div className="bg-card ring-foreground/10 flex flex-col gap-1 rounded-xl p-4 ring-1">
      <span className="text-muted-foreground text-[13px]">{label}</span>
      <span
        className={cn(
          "text-[22px]/[1.15] font-semibold tracking-[-0.02em] tabular-nums",
          tone,
        )}
      >
        {value}
      </span>
    </div>
  );
}

function KindChip({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={cn(
        "h-8 shrink-0 rounded-lg px-3 text-[13px] transition-colors",
        active
          ? "bg-secondary text-secondary-foreground"
          : "border-border text-muted-foreground hover:bg-muted border",
      )}
    >
      {label}
    </button>
  );
}

function UpcomingRow({ item }: { item: UpcomingItem }) {
  return (
    <div className="grid grid-cols-[64px_minmax(0,1fr)_auto] items-center gap-3 px-4 py-2.5">
      <span className="text-muted-foreground text-[13px] tabular-nums">
        {formatDday(item.dday)}
      </span>
      <span className="flex min-w-0 flex-wrap items-center gap-2">
        <span className="truncate text-sm">{item.title ?? "예정"}</span>
        <Badge variant="secondary">{UPCOMING_KIND_LABELS[item.kind]}</Badge>
        {/* 이체 성격은 소비가 아니다. 배지를 하나 더 달아 그 사실을 표시한다(§8.3). */}
        {item.isTransfer && <Badge variant="outline">이체</Badge>}
        {item.estimated && <Badge variant="outline">예상</Badge>}
        {item.overdue && <Badge variant="destructive">미납</Badge>}
        <span className="text-muted-foreground shrink-0 text-[13px]">
          {formatDateHeader(item.date)}
          {item.assetName && ` · ${item.assetName}`}
        </span>
      </span>
      <span className="text-right text-sm tabular-nums">
        {formatAmount(item.amount)}
      </span>
    </div>
  );
}

/** 월 그룹. 12개월을 펼치면 어느 달 이야기인지 표시가 있어야 읽힌다. */
function groupByMonth(items: UpcomingItem[]): [string, UpcomingItem[]][] {
  const byMonth = new Map<string, UpcomingItem[]>();
  for (const item of items) {
    const month = item.date.slice(0, 7);
    const rows = byMonth.get(month);
    if (rows) {
      rows.push(item);
    } else {
      byMonth.set(month, [item]);
    }
  }
  return [...byMonth.entries()];
}

function monthLabel(month: string): string {
  const [year, index] = month.split("-");
  return `${year}년 ${Number(index)}월`;
}

/** 파생 회차는 id가 없다 — 종류·날짜·출처를 합쳐 키를 만든다. */
function itemKey(item: UpcomingItem): string {
  return [
    item.kind,
    item.date,
    item.transactionId ?? item.statementId ?? item.recurringId ?? 0,
    item.occurrenceDate ?? "",
    item.installmentId ?? "",
  ].join(":");
}
