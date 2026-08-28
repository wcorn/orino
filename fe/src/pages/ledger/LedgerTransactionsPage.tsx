import {
  ArrowDown,
  ChevronLeft,
  ChevronRight,
  Plus,
  Search,
} from "lucide-react";
import { useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { LoadingText } from "@/components/ui/loading-text";
import type {
  DateGroup,
  MonthTotals,
  TransactionView,
} from "@/features/ledger/api/ledger";
import { useTransactionModal } from "@/features/ledger/components/transactionModalContext";
import {
  useLedgerSettings,
  useLedgerTransactions,
} from "@/features/ledger/hooks/useLedgerQueries";
import {
  amountToneClass,
  dDayFrom,
  formatAmount,
  formatDateHeader,
  formatSigned,
} from "@/features/ledger/lib/money";
import { periodLabel, periodOf } from "@/features/ledger/lib/period";
import { cn } from "@/lib/utils";

type StatusFilter = "ALL" | "CONFIRMED" | "SCHEDULED";

const STATUS_CHIPS: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "CONFIRMED", label: "확정만" },
  { value: "SCHEDULED", label: "예정만" },
];

/**
 * 내역 `/ledger/transactions`.
 *
 * <p><b>오늘 기준선 하나를 두고 과거와 예정이 한 스크롤로 이어진다.</b> 두 목록으로 나누지
 * 않는다 — 「앞으로 얼마 나가나」는 이미 쓴 돈과 같은 자리에서 읽혀야 하는 질문이다
 * (확정 명세 §8.3).
 *
 * <p>월 이동·필터·검색은 <b>URL 쿼리</b>에 둔다(링크 워크스페이스 선례). 별도 상태를 두면
 * 새로고침·뒤로가기에서 화면과 주소가 어긋난다.
 */
export function LedgerTransactionsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { openTransactionModal } = useTransactionModal();
  const { data: settings } = useLedgerSettings();

  const offset = Number(searchParams.get("offset") ?? "0") || 0;
  const status = (searchParams.get("status") ?? "ALL") as StatusFilter;
  // 대시보드의 「정리하기」가 넘겨주는 필터. 별도 상태가 아니라 쿼리라서,
  // 정리하다 새로고침해도 같은 목록으로 돌아온다.
  const uncategorizedOnly = searchParams.get("uncategorized") === "1";
  const [query, setQuery] = useState("");

  const period = useMemo(
    () => periodOf(new Date(), settings?.monthStartDay ?? 1, offset),
    [settings?.monthStartDay, offset],
  );

  // 이번 구간이면 앞으로 30일치 예정까지 함께 본다 — 예정이 안 보이면 이 화면의 절반이 없다.
  const to = offset === 0 ? undefined : period.end;
  const { data, isPending, isError } = useLedgerTransactions(period.start, to);

  const setParam = (key: string, value: string | null) => {
    if (value === null) {
      searchParams.delete(key);
    } else {
      searchParams.set(key, value);
    }
    setSearchParams(searchParams);
  };

  const groups = filterGroups(
    data?.groups ?? [],
    status,
    query,
    uncategorizedOnly,
  );
  const empty = groups.length === 0;

  return (
    <div className="mx-auto flex max-w-[880px] flex-col gap-4">
      <PageHeader
        title="내역"
        actions={
          <Button type="button" onClick={openTransactionModal}>
            <Plus className="size-4" />
            입력 <kbd className="ml-1 text-[11px] opacity-70">N</kbd>
          </Button>
        }
      />

      <div className="flex flex-wrap items-center gap-2">
        <div className="flex items-center gap-1">
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label="이전 달"
            onClick={() => setParam("offset", String(offset - 1))}
          >
            <ChevronLeft className="size-4" />
          </Button>
          <span className="min-w-[104px] text-center text-sm font-medium">
            {periodLabel(period)}
          </span>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label="다음 달"
            onClick={() => setParam("offset", String(offset + 1))}
          >
            <ChevronRight className="size-4" />
          </Button>
        </div>

        <div className="relative min-w-[180px] flex-1">
          <Search className="text-muted-foreground pointer-events-none absolute top-2.5 left-2.5 size-3.5" />
          <Input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            aria-label="내역 검색"
            placeholder="내용·카테고리·자산 검색"
            className="pl-7"
          />
        </div>

        {STATUS_CHIPS.map((chip) => (
          <button
            key={chip.value}
            type="button"
            aria-pressed={status === chip.value}
            onClick={() =>
              setParam("status", chip.value === "ALL" ? null : chip.value)
            }
            className={cn(
              "h-8 shrink-0 rounded-lg px-3 text-[13px] transition-colors",
              status === chip.value
                ? "bg-secondary text-secondary-foreground"
                : "border-border text-muted-foreground hover:bg-muted border",
            )}
          >
            {chip.label}
          </button>
        ))}
      </div>

      {uncategorizedOnly && (
        <div className="text-muted-foreground flex items-center gap-2 text-[13px]">
          카테고리가 없는 건만 보는 중 — 채우면 목록에서 사라져요
          <Button
            type="button"
            variant="ghost"
            size="xs"
            onClick={() => setParam("uncategorized", null)}
          >
            해제
          </Button>
        </div>
      )}

      {data && <TotalsBar totals={data.monthTotals} />}

      {isPending && <LoadingText />}
      {isError && (
        <Alert variant="destructive">내역을 불러오지 못했어요.</Alert>
      )}

      {data && empty && (
        <EmptyState className="min-h-[30svh]">
          <p className="text-muted-foreground text-sm">
            이 기간에 적힌 거래가 없어요.
          </p>
          <Button type="button" onClick={openTransactionModal}>
            거래 입력
          </Button>
        </EmptyState>
      )}

      {data &&
        groups.map((group) => (
          <TransactionGroup
            key={group.date}
            group={group}
            todayLine={data.todayLine}
            // 기준선은 「전체」일 때만 그린다 — 한쪽만 보는 중이면 나눌 것이 없다.
            showTodayLine={status === "ALL"}
          />
        ))}
    </div>
  );
}

/** 이체는 지출에도 수입에도 들어가지 않는다 — 따로 센 값을 따로 보여준다. */
function TotalsBar({ totals }: { totals: MonthTotals }) {
  return (
    <div className="bg-muted flex flex-wrap items-center gap-x-5 gap-y-1 rounded-lg px-4 py-3 text-[13px] tabular-nums">
      <span className="text-success">수입 {formatAmount(totals.income)}</span>
      <span>지출 {formatAmount(totals.expense)}</span>
      <span className="text-muted-foreground">
        이체 {formatAmount(totals.transfer)}
      </span>
      {totals.scheduledCount > 0 && (
        <span className="text-muted-foreground">
          예정 지출 {formatAmount(totals.scheduledExpense)} ·{" "}
          {totals.scheduledCount}건
        </span>
      )}
    </div>
  );
}

function TransactionGroup({
  group,
  todayLine,
  showTodayLine,
}: {
  group: DateGroup;
  todayLine: string;
  showTodayLine: boolean;
}) {
  return (
    <section className="flex flex-col">
      {/* 기준선은 오늘 <b>다음</b> 날짜 그룹 위에 온다 — 그 아래부터가 예정이다. */}
      {showTodayLine && group.date > todayLine && (
        <div className="border-y bg-[color-mix(in_oklab,var(--primary)_5%,var(--card))] py-1.5 text-center">
          <span className="text-primary text-caption inline-flex items-center gap-1 font-semibold tracking-[0.04em]">
            <ArrowDown className="size-3" />
            오늘 · 아래는 예정
          </span>
        </div>
      )}
      <div className="bg-muted flex items-center justify-between px-4 py-2.5 text-[13px]">
        <span>{formatDateHeader(group.date)}</span>
        <span className="tabular-nums">
          {group.income > 0 && (
            <span className="text-success">+{formatAmount(group.income)}</span>
          )}
          {group.income > 0 && group.expense > 0 && " · "}
          {group.expense > 0 && <span>−{formatAmount(group.expense)}</span>}
        </span>
      </div>
      <ul>
        {group.items.map((item) => (
          <li key={item.id}>
            <TransactionRow transaction={item} todayLine={todayLine} />
          </li>
        ))}
      </ul>
    </section>
  );
}

function TransactionRow({
  transaction,
  todayLine,
}: {
  transaction: TransactionView;
  todayLine: string;
}) {
  // 부호는 그 줄에서 실제로 일어난 일이다. 환불이면 돈이 돌아왔으므로 `+`다 —
  // 「지출이 줄었다」는 합계의 이야기이고, 그건 서버가 계산해 헤더에 담아 준다.
  const flow = transaction.type;
  const scheduled = transaction.status === "SCHEDULED";
  const dDay = scheduled ? dDayFrom(todayLine, transaction.occurredOn) : 0;

  return (
    <div
      className={cn(
        "grid grid-cols-[minmax(0,1fr)_108px] items-center gap-3 px-4 py-2.5 md:grid-cols-[minmax(0,1fr)_132px_108px]",
        // 예정은 확정과 시각적으로 구분되되 같은 타임라인 위에 남는다.
        scheduled && "bg-muted",
      )}
    >
      <span className="flex min-w-0 items-center gap-2">
        <span
          className={cn(
            "truncate text-sm",
            scheduled && "text-muted-foreground",
          )}
        >
          {transaction.title ?? "제목 없음"}
        </span>
        {scheduled && (
          <>
            <Badge variant="secondary">예정</Badge>
            <span className="text-muted-foreground shrink-0 text-[13px] tabular-nums">
              D{dDay >= 0 ? `-${dDay}` : `+${-dDay}`}
            </span>
          </>
        )}
        {transaction.type === "TRANSFER" && (
          <Badge variant="outline">이체</Badge>
        )}
        {transaction.source === "REFUND" && (
          <Badge variant="outline">환불</Badge>
        )}
        {/* 미분류는 경고다 — 쌓이면 통계가 무의미해진다. */}
        {transaction.categoryId === null && transaction.type !== "TRANSFER" && (
          <Badge variant="warning">미분류</Badge>
        )}
      </span>
      <span className="text-muted-foreground hidden truncate text-[13px] md:block">
        {[transaction.categoryName, transaction.assetName]
          .filter(Boolean)
          .join(" · ")}
      </span>
      <span
        className={cn(
          "text-right text-sm tabular-nums",
          scheduled ? "text-muted-foreground" : amountToneClass(flow),
        )}
      >
        {formatSigned(transaction.amount, flow)}
        {/* 외화는 보조 표기다. 본문 금액은 언제나 서버가 확정한 원화다(D-13). */}
        {transaction.fx && (
          <span className="text-muted-foreground block text-[11px]">
            {transaction.fx.amount} {transaction.fx.currency}
          </span>
        )}
      </span>
    </div>
  );
}

/** 검색·필터는 이미 받아 온 목록 위에서 건다 — 글자를 칠 때마다 서버를 다시 부르지 않는다. */
function filterGroups(
  groups: DateGroup[],
  status: StatusFilter,
  query: string,
  uncategorizedOnly: boolean,
): DateGroup[] {
  const keyword = query.trim().toLowerCase();
  return groups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => {
        if (status === "CONFIRMED" && item.status !== "CONFIRMED") {
          return false;
        }
        if (status === "SCHEDULED" && item.status !== "SCHEDULED") {
          return false;
        }
        // 이체는 애초에 분류 대상이 아니라 「정리할 것」에서도 빠진다.
        if (
          uncategorizedOnly &&
          (item.categoryId !== null || item.type === "TRANSFER")
        ) {
          return false;
        }
        if (keyword === "") {
          return true;
        }
        return [item.title, item.categoryName, item.assetName]
          .filter(Boolean)
          .some((text) => (text as string).toLowerCase().includes(keyword));
      }),
    }))
    .filter((group) => group.items.length > 0);
}
