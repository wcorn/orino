import {
  ArrowDown,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Copy,
  Ellipsis,
  List,
  Paperclip,
  Plane,
  Plus,
  Search,
} from "lucide-react";
import { useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { LoadingText } from "@/components/ui/loading-text";
import { Menu, MenuItem } from "@/components/ui/menu";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type {
  DateGroup,
  MonthTotals,
  TransactionView,
  UpcomingItem,
} from "@/features/ledger/api/ledger";
import { LedgerCalendar } from "@/features/ledger/components/LedgerCalendar";
import { ReceiptsModal } from "@/features/ledger/components/ReceiptsModal";
import { ScheduledRowActions } from "@/features/ledger/components/ScheduledRowActions";
import { useTransactionModal } from "@/features/ledger/components/transactionModalContext";
import { TripAttachBar } from "@/features/ledger/components/TripAttachBar";
import { useDuplicateTransaction } from "@/features/ledger/hooks/useLedgerMutations";
import {
  useLedgerSettings,
  useLedgerTransactions,
  useLedgerUpcoming,
} from "@/features/ledger/hooks/useLedgerQueries";
import {
  formatDday,
  UPCOMING_KIND_LABELS,
} from "@/features/ledger/lib/balance";
import {
  amountToneClass,
  dDayFrom,
  formatAmount,
  formatDateHeader,
  formatSigned,
} from "@/features/ledger/lib/money";
import { periodLabel, periodOf } from "@/features/ledger/lib/period";
import { useAttachExpensesToTrip } from "@/features/travel/hooks/useTripExpenses";
import { cn } from "@/lib/utils";

type StatusFilter = "ALL" | "CONFIRMED" | "SCHEDULED";

const STATUS_CHIPS: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "CONFIRMED", label: "확정만" },
  { value: "SCHEDULED", label: "예정만" },
];

/** 기본 30일. 「더 보기」로 넓힌다 — 최대 12개월이고 그 너머는 예정이 아니라 추측이다. */
const UPCOMING_RANGES = [30, 90, 366];

/** 원장에 실체화된 줄과 파생 예정 줄이 한 타임라인 위에 섞여 있다. */
type TimelineRow =
  | { key: string; kind: "LEDGER"; transaction: TransactionView }
  | { key: string; kind: "UPCOMING"; item: UpcomingItem };

interface TimelineGroup {
  date: string;
  income: number;
  expense: number;
  rows: TimelineRow[];
}

/**
 * 내역 `/ledger/transactions`.
 *
 * <p><b>오늘 기준선 하나를 두고 과거와 예정이 한 스크롤로 이어진다.</b> 두 목록으로 나누지
 * 않는다 — 「앞으로 얼마 나가나」는 이미 쓴 돈과 같은 자리에서 읽혀야 하는 질문이다
 * (확정 명세 §8.3).
 *
 * <p>예정은 <b>네 출처</b>에서 온다. 원장에 있는 것은 직접 예약뿐이고 정기 회차·카드 대금·
 * 할부 잔여는 파생이라, 원장만 그리면 「14일에 카드값 84만이 빠진다」가 이 화면에서 통째로
 * 빠진다 — 그게 이 모듈이 막으려는 바로 그 놀람이다.
 *
 * <p>월 이동·필터·검색은 <b>URL 쿼리</b>에 둔다(링크 워크스페이스 선례). 별도 상태를 두면
 * 새로고침·뒤로가기에서 화면과 주소가 어긋난다.
 */
export function LedgerTransactionsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { openTransactionModal } = useTransactionModal();
  const duplicate = useDuplicateTransaction();
  const [receiptTarget, setReceiptTarget] = useState<TransactionView | null>(
    null,
  );
  const [upcomingDays, setUpcomingDays] = useState(UPCOMING_RANGES[0]);
  const { data: settings } = useLedgerSettings();

  const offset = Number(searchParams.get("offset") ?? "0") || 0;
  const status = (searchParams.get("status") ?? "ALL") as StatusFilter;
  const view = searchParams.get("view") === "calendar" ? "calendar" : "list";
  // 대시보드의 「정리하기」가 넘겨주는 필터. 별도 상태가 아니라 쿼리라서,
  // 정리하다 새로고침해도 같은 목록으로 돌아온다.
  const uncategorizedOnly = searchParams.get("uncategorized") === "1";
  // 여행 필터도 쿼리다. 서버가 목록과 상단 합계에 같은 필터를 걸어 준다(여행 v2.2 §18).
  const tripId = Number(searchParams.get("tripId") ?? "") || undefined;
  const [query, setQuery] = useState("");
  /** 「여행에 붙이기」 선택 모드. 켜져 있는 동안만 줄마다 체크박스가 붙는다. */
  const [selecting, setSelecting] = useState(false);
  const [selected, setSelected] = useState<number[]>([]);

  const period = useMemo(
    () => periodOf(new Date(), settings?.monthStartDay ?? 1, offset),
    [settings?.monthStartDay, offset],
  );

  // 이번 구간이면 앞으로 30일치 예정까지 함께 본다 — 예정이 안 보이면 이 화면의 절반이 없다.
  const to = offset === 0 ? undefined : period.end;
  const { data, isPending, isError } = useLedgerTransactions(period.start, to, {
    tripId,
  });
  const attachTrip = useAttachExpensesToTrip();
  // 지난 달을 보는 중에는 파생 예정이 없다 — 파생 회차는 언제나 오늘 이후다.
  const { data: upcoming } = useLedgerUpcoming(upcomingDays);

  /**
   * 줄 하나를 고르거나 뺀다.
   *
   * <p><b>이체는 여기서 손으로만 들어온다.</b> 「전체 선택」이 카드 대금 납부를 함께 담으면
   * 여행 경비가 두 번 세어진다 — §3-2가 막으려던 구멍이 그대로 되살아난다(R-15).
   */
  const toggleSelect = (id: number) =>
    setSelected((prev) =>
      prev.includes(id) ? prev.filter((v) => v !== id) : [...prev, id],
    );

  const setParam = (key: string, value: string | null) => {
    if (value === null) {
      searchParams.delete(key);
    } else {
      searchParams.set(key, value);
    }
    setSearchParams(searchParams);
  };

  const groups = useMemo(
    () =>
      buildTimeline(
        data?.groups ?? [],
        offset === 0 ? (upcoming?.items ?? []) : [],
        { status, query, uncategorizedOnly },
      ),
    [data?.groups, upcoming?.items, offset, status, query, uncategorizedOnly],
  );
  const empty = groups.length === 0;

  return (
    <div className="mx-auto flex max-w-[880px] flex-col gap-4">
      <PageHeader
        title="내역"
        actions={
          <div className="flex items-center gap-2">
            {/*
              여행에 붙이기(§18). 여행 중엔 그냥 적고 돌아와 기간으로 걸러 한 번 붙인다 —
              그래서 이 버튼은 「기간을 고른 뒤」 누르는 자리에 있다.
            */}
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setSelecting((on) => !on);
                setSelected([]);
              }}
            >
              <Plane className="size-4" />
              {selecting ? "선택 그만두기" : "여행에 붙이기"}
            </Button>
            <Button type="button" onClick={openTransactionModal}>
              <Plus className="size-4" />
              입력 <kbd className="ml-1 text-[11px] opacity-70">N</kbd>
            </Button>
          </div>
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

        <Tabs
          value={view}
          onValueChange={(value) =>
            setParam("view", value === "calendar" ? "calendar" : null)
          }
        >
          <TabsList>
            <TabsTrigger value="list">
              <List />
              리스트
            </TabsTrigger>
            <TabsTrigger value="calendar">
              <CalendarDays />
              캘린더
            </TabsTrigger>
          </TabsList>
        </Tabs>

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

      {tripId !== undefined && (
        <div className="text-muted-foreground flex items-center gap-2 text-[13px]">
          여행에 붙인 건만 보는 중 — 위 합계도 이 여행 기준이에요
          <Button
            type="button"
            variant="ghost"
            size="xs"
            onClick={() => setParam("tripId", null)}
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

      {view === "calendar" ? (
        <LedgerCalendar month={period.start.slice(0, 7)} />
      ) : (
        data && (
          <>
            {empty && (
              <EmptyState className="min-h-[30svh]">
                <p className="text-muted-foreground text-sm">
                  이 기간에 적힌 거래가 없어요.
                </p>
                <Button type="button" onClick={openTransactionModal}>
                  거래 입력
                </Button>
              </EmptyState>
            )}

            {groups.map((group) => (
              <TimelineSection
                key={group.date}
                group={group}
                todayLine={data.todayLine}
                // 기준선은 「전체」일 때만 그린다 — 한쪽만 보는 중이면 나눌 것이 없다.
                showTodayLine={status === "ALL"}
                onDuplicate={(id, useToday) =>
                  duplicate.mutate({ id, useToday })
                }
                onOpenReceipts={setReceiptTarget}
                selecting={selecting}
                selected={selected}
                onToggleSelect={toggleSelect}
              />
            ))}

            {offset === 0 &&
              UPCOMING_RANGES.some((value) => value > upcomingDays) && (
                <div className="flex justify-center pt-1">
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() =>
                      setUpcomingDays(
                        UPCOMING_RANGES.find((value) => value > upcomingDays) ??
                          upcomingDays,
                      )
                    }
                  >
                    더 보기 — 최대 12개월
                  </Button>
                </div>
              )}
          </>
        )
      )}

      {selecting && (
        <TripAttachBar
          selectedCount={selected.length}
          pending={attachTrip.isPending}
          onCancel={() => {
            setSelecting(false);
            setSelected([]);
          }}
          onApply={(trip) =>
            attachTrip.mutate(
              { tripId: trip, transactionIds: selected },
              {
                onSuccess: () => {
                  setSelecting(false);
                  setSelected([]);
                },
              },
            )
          }
        />
      )}

      <ReceiptsModal
        transactionId={receiptTarget?.id ?? null}
        title={receiptTarget?.title ?? "거래"}
        onClose={() => setReceiptTarget(null)}
      />
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

function TimelineSection({
  group,
  todayLine,
  showTodayLine,
  onDuplicate,
  onOpenReceipts,
  selecting,
  selected,
  onToggleSelect,
}: {
  group: TimelineGroup;
  todayLine: string;
  showTodayLine: boolean;
  onDuplicate: (id: number, useToday: boolean) => void;
  onOpenReceipts: (transaction: TransactionView) => void;
  selecting: boolean;
  selected: number[];
  onToggleSelect: (id: number) => void;
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
        {group.rows.map((row) => (
          <li key={row.key}>
            {row.kind === "LEDGER" ? (
              <TransactionRow
                transaction={row.transaction}
                todayLine={todayLine}
                onDuplicate={onDuplicate}
                onOpenReceipts={onOpenReceipts}
                selecting={selecting}
                checked={selected.includes(row.transaction.id)}
                onToggleSelect={onToggleSelect}
              />
            ) : (
              <DerivedRow item={row.item} />
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}

function TransactionRow({
  transaction,
  todayLine,
  onDuplicate,
  onOpenReceipts,
  selecting,
  checked,
  onToggleSelect,
}: {
  transaction: TransactionView;
  todayLine: string;
  onDuplicate: (id: number, useToday: boolean) => void;
  onOpenReceipts: (transaction: TransactionView) => void;
  selecting: boolean;
  checked: boolean;
  onToggleSelect: (id: number) => void;
}) {
  // 부호는 그 줄에서 실제로 일어난 일이다. 환불이면 돈이 돌아왔으므로 `+`다 —
  // 「지출이 줄었다」는 합계의 이야기이고, 그건 서버가 계산해 헤더에 담아 준다.
  const flow = transaction.type;
  const scheduled = transaction.status === "SCHEDULED";

  return (
    <div
      className={cn(
        "group grid grid-cols-[minmax(0,1fr)_108px] items-center gap-3 px-4 py-2.5 md:grid-cols-[minmax(0,1fr)_132px_108px]",
        // 예정은 확정과 시각적으로 구분되되 같은 타임라인 위에 남는다.
        scheduled && "bg-muted",
      )}
    >
      <span className="flex min-w-0 items-center gap-2">
        {selecting && (
          <Checkbox
            checked={checked}
            aria-label={`${transaction.title ?? "거래"} 선택`}
            onChange={() => onToggleSelect(transaction.id)}
          />
        )}
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
            {/* 직접 예약은 규칙이 만든 회차가 아니다 — 원장에 이미 행이 있다. */}
            <Badge variant="outline">직접 예약</Badge>
            <span className="text-muted-foreground shrink-0 text-[13px] tabular-nums">
              {formatDday(dDayFrom(todayLine, transaction.occurredOn))}
            </span>
          </>
        )}
        {transaction.type === "TRANSFER" && (
          <Badge variant="outline">이체</Badge>
        )}
        {/*
          고르는 동안에만 말한다. 카드 대금 납부를 여행에 붙이면 §3-2가 막으려던 이중 계산이
          그대로 되살아나므로, 못 고르게 막는 대신 왜 안 고르는지를 옆에 적는다(R-15).
        */}
        {selecting && transaction.type === "TRANSFER" && (
          <span className="text-muted-foreground shrink-0 text-[13px]">
            — 여행 경비가 아니에요
          </span>
        )}
        {/* 이미 붙어 있는 줄. 어느 여행인지는 여행 화면이 말한다. */}
        {transaction.tripId !== null && <Badge variant="info">여행</Badge>}
        {transaction.source === "REFUND" && (
          <Badge variant="outline">환불</Badge>
        )}
        {/* 미분류는 경고다 — 쌓이면 통계가 무의미해진다. */}
        {transaction.categoryId === null && transaction.type !== "TRANSFER" && (
          <Badge variant="warning">미분류</Badge>
        )}
        {scheduled && (
          <ScheduledRowActions item={fromTransaction(transaction)} />
        )}
      </span>
      <span className="text-muted-foreground hidden truncate text-[13px] md:block">
        {[transaction.categoryName, transaction.assetName]
          .filter(Boolean)
          .join(" · ")}
      </span>
      <span className="flex items-center justify-end gap-1">
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
        {/*
          복사(LDG-014) — 템플릿으로 만들 만큼 반복되진 않지만 이번 달에 두 번 나오는 지출용.
          hover에만 나타나면 터치 화면에서 닿지 않으므로 항상 둔다.
        */}
        <Menu
          trigger={
            <Button
              type="button"
              variant="ghost"
              size="icon-xs"
              aria-label={`${transaction.title ?? "거래"} 메뉴`}
            >
              <Ellipsis className="size-3.5" />
            </Button>
          }
        >
          <MenuItem onClick={() => onDuplicate(transaction.id, true)}>
            <Copy className="size-3.5" />
            오늘 날짜로 복사
          </MenuItem>
          <MenuItem onClick={() => onDuplicate(transaction.id, false)}>
            원본 날짜로 복사
          </MenuItem>
          <MenuItem onClick={() => onOpenReceipts(transaction)}>
            <Paperclip className="size-3.5" />
            영수증
          </MenuItem>
        </Menu>
      </span>
    </div>
  );
}

/** 원장에 없는 예정 — 정기 회차·카드 대금·할부 잔여. 파생이라 거래 id가 없다. */
function DerivedRow({ item }: { item: UpcomingItem }) {
  return (
    <div className="bg-muted group grid grid-cols-[minmax(0,1fr)_108px] items-center gap-3 px-4 py-2.5 md:grid-cols-[minmax(0,1fr)_132px_108px]">
      <span className="flex min-w-0 items-center gap-2">
        <span className="text-muted-foreground truncate text-sm">
          {item.title ?? UPCOMING_KIND_LABELS[item.kind]}
        </span>
        <Badge variant="secondary">예정</Badge>
        <span className="text-muted-foreground shrink-0 text-[13px] tabular-nums">
          {formatDday(item.dday)}
        </span>
        {/* 이체 성격은 소비가 아니다 — 배지를 하나 더 달아 그 사실을 표시한다. */}
        {item.isTransfer && <Badge variant="outline">이체</Badge>}
        {item.overdue && <Badge variant="destructive">미납</Badge>}
        <ScheduledRowActions item={item} />
      </span>
      <span className="text-muted-foreground hidden truncate text-[13px] md:block">
        {[UPCOMING_KIND_LABELS[item.kind], item.assetName]
          .filter(Boolean)
          .join(" · ")}
      </span>
      <span className="text-muted-foreground text-right text-sm tabular-nums">
        {formatSigned(item.amount, item.flow)}
      </span>
    </div>
  );
}

/** 직접 예약 줄도 같은 인라인 액션을 쓴다 — 가는 곳만 다르다. */
function fromTransaction(transaction: TransactionView): UpcomingItem {
  return {
    kind: "ONE_OFF",
    date: transaction.occurredOn,
    dday: 0,
    title: transaction.title,
    amount: transaction.amount,
    flow: transaction.type,
    isTransfer: transaction.type === "TRANSFER",
    overdue: false,
    estimated: transaction.estimated,
    categoryId: transaction.categoryId,
    assetId: transaction.assetId,
    assetName: transaction.assetName,
    transactionId: transaction.id,
    recurringId: null,
    occurrenceDate: null,
    statementId: null,
    installmentId: null,
    tripId: transaction.tripId,
  };
}

/**
 * 원장 줄과 파생 예정을 <b>한 타임라인</b>으로 합친다.
 *
 * <p>직접 예약은 이미 원장에 있으므로 파생 쪽에서 뺀다 — 넣으면 같은 줄이 두 번 보이고,
 * 그건 이 화면에서 「돈을 두 번 셌다」로 읽힌다.
 *
 * <p>검색·필터는 이미 받아 온 목록 위에서 건다 — 글자를 칠 때마다 서버를 다시 부르지 않는다.
 */
function buildTimeline(
  groups: DateGroup[],
  upcoming: UpcomingItem[],
  filters: {
    status: StatusFilter;
    query: string;
    uncategorizedOnly: boolean;
  },
): TimelineGroup[] {
  const keyword = filters.query.trim().toLowerCase();
  const byDate = new Map<string, TimelineGroup>();

  for (const group of groups) {
    const rows: TimelineRow[] = [];
    for (const item of group.items) {
      if (filters.status === "CONFIRMED" && item.status !== "CONFIRMED") {
        continue;
      }
      if (filters.status === "SCHEDULED" && item.status !== "SCHEDULED") {
        continue;
      }
      // 이체는 애초에 분류 대상이 아니라 「정리할 것」에서도 빠진다.
      if (
        filters.uncategorizedOnly &&
        (item.categoryId !== null || item.type === "TRANSFER")
      ) {
        continue;
      }
      if (
        keyword !== "" &&
        !matches(keyword, [item.title, item.categoryName, item.assetName])
      ) {
        continue;
      }
      rows.push({ key: `tx-${item.id}`, kind: "LEDGER", transaction: item });
    }
    if (rows.length > 0) {
      byDate.set(group.date, {
        date: group.date,
        income: group.income,
        expense: group.expense,
        rows,
      });
    }
  }

  if (filters.status !== "CONFIRMED" && !filters.uncategorizedOnly) {
    for (const item of upcoming) {
      // 직접 예약은 원장에 이미 있다. 여기서 또 넣으면 한 건이 두 줄이 된다.
      if (item.kind === "ONE_OFF") {
        continue;
      }
      const title = item.title ?? UPCOMING_KIND_LABELS[item.kind];
      if (keyword !== "" && !matches(keyword, [title, item.assetName])) {
        continue;
      }
      const group = byDate.get(item.date) ?? {
        date: item.date,
        income: 0,
        expense: 0,
        rows: [],
      };
      group.rows.push({ key: derivedKey(item), kind: "UPCOMING", item });
      byDate.set(item.date, group);
    }
  }

  return [...byDate.values()].sort((left, right) =>
    left.date < right.date ? -1 : 1,
  );
}

function matches(keyword: string, texts: (string | null)[]): boolean {
  return texts
    .filter(Boolean)
    .some((text) => (text as string).toLowerCase().includes(keyword));
}

function derivedKey(item: UpcomingItem): string {
  return [
    item.kind,
    item.date,
    item.recurringId ?? item.statementId ?? item.installmentId ?? 0,
    item.occurrenceDate ?? "",
  ].join(":");
}
