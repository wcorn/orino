import {
  Clock,
  Ellipsis,
  Hourglass,
  Infinity as InfinityIcon,
  TrendingUp,
} from "lucide-react";
import { useState } from "react";

import { PageHeader } from "@/components/PageHeader";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingText } from "@/components/ui/loading-text";
import { Menu, MenuItem, MenuSeparator } from "@/components/ui/menu";
import { Modal } from "@/components/ui/modal";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type {
  RecurringListResponse,
  RecurringView,
} from "@/features/ledger/api/ledger";
import { OverdueAlert } from "@/features/ledger/components/OverdueAlert";
import {
  useEndRecurring,
  useOccurrenceAction,
  usePauseRecurring,
  useResumeRecurring,
} from "@/features/ledger/hooks/useLedgerMutations";
import {
  useLedgerRecurring,
  useRecurringHistory,
} from "@/features/ledger/hooks/useLedgerQueries";
import { formatAmount, formatDateHeader } from "@/features/ledger/lib/money";
import { todayIso } from "@/features/ledger/lib/period";
import {
  amountLabel,
  isEnded,
  RECURRING_KIND_LABELS,
} from "@/features/ledger/lib/recurrence";
import { cn } from "@/lib/utils";

/**
 * 정기 항목 `/ledger/recurring` — <b>목록이 아니라 점검 도구</b>.
 *
 * <p>「내가 뭘 내고 있나」에서 끝나면 정리할 계기가 없다. 오른 것·끝나가는 것·오래 손대지
 * 않은 것·기한 없는 것을 골라 놓는 것이 이 화면의 쓸모다(확정 명세 §6.6).
 *
 * <p><b>해지한 항목은 사라지지 않는다.</b> 「종료됨」으로 흐리게 남는다 — 연간 고정비 회고에
 * 「올해 이건 넉 달 냈다」가 있어야 한다.
 */
export function LedgerRecurringPage() {
  const { data, isPending, isError } = useLedgerRecurring();
  const [historyId, setHistoryId] = useState<number | null>(null);

  return (
    <div className="mx-auto flex max-w-[980px] flex-col gap-4">
      <PageHeader
        title="정기 항목"
        description="구독·보험·자동이체·고정비를 한 목록으로 봅니다"
      />

      {isPending && <LoadingText />}
      {isError && (
        <Alert variant="destructive">정기 항목을 불러오지 못했어요.</Alert>
      )}

      {data && (
        <>
          <OverdueAlert items={toOverdueItems(data)} />

          <div className="grid grid-cols-[repeat(auto-fit,minmax(160px,1fr))] gap-3">
            <Stat
              label="월 고정비"
              value={formatAmount(data.stats.monthlyFixedTotal)}
            />
            <Stat
              label="연 환산"
              value={formatAmount(data.stats.yearlyTotal)}
              hint="연간 구독은 ÷12로 정규화"
            />
            <Stat
              label="구독 개수"
              value={String(data.stats.subscriptionCount)}
            />
            <Stat
              label="살아 있는 항목"
              value={String(data.stats.activeCount)}
            />
          </div>

          <Signals data={data} />

          {data.items.length === 0 ? (
            <EmptyState className="min-h-[30svh]">
              <p className="text-muted-foreground text-sm">
                등록한 정기 항목이 없어요.
              </p>
            </EmptyState>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>이름</TableHead>
                  <TableHead>주기</TableHead>
                  <TableHead>다음 결제일</TableHead>
                  <TableHead className="text-right">금액</TableHead>
                  <TableHead className="text-right">월 환산</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.items.map((rule) => (
                  <RuleRow
                    key={rule.id}
                    rule={rule}
                    onHistory={() => setHistoryId(rule.id)}
                  />
                ))}
              </TableBody>
            </Table>
          )}

          <p className="text-muted-foreground text-[13px]">
            해지한 항목은 목록에서 사라지지 않고 「종료됨」으로 남습니다 — 연간
            고정비 회고에 필요합니다.
          </p>
        </>
      )}

      <HistoryModal id={historyId} onClose={() => setHistoryId(null)} />
    </div>
  );
}

/** 점검 신호 4종. 이 화면이 목록이 아니라 점검 도구인 이유가 여기 있다. */
function Signals({ data }: { data: RecurringListResponse }) {
  const { signals } = data;
  const nameOf = (id: number) =>
    data.items.find((item) => item.id === id)?.name ?? "";

  const cards = [
    signals.priceIncreased.length > 0 && (
      <Alert key="increased" variant="warning">
        <TrendingUp />
        <AlertTitle>최근 인상 {signals.priceIncreased.length}건</AlertTitle>
        <AlertDescription>
          <p>
            {signals.priceIncreased
              .map(
                (row) =>
                  `${row.name} ${formatAmount(row.from)} → ${formatAmount(row.to)}`,
              )
              .join(" · ")}
          </p>
        </AlertDescription>
      </Alert>
    ),
    signals.trialEnding.length > 0 && (
      <Alert key="trial" variant="destructive">
        <Hourglass />
        <AlertTitle>무료 체험 종료 임박</AlertTitle>
        <AlertDescription>
          <p>
            {signals.trialEnding
              .map(
                (row) =>
                  `${row.name} ${row.endsOn}부터 ${formatAmount(row.amount)}`,
              )
              .join(" · ")}
          </p>
        </AlertDescription>
      </Alert>
    ),
    signals.longUnchanged.length > 0 && (
      <Alert key="unchanged" variant="info">
        <Clock />
        <AlertTitle>6개월 이상 손대지 않음</AlertTitle>
        <AlertDescription>
          <p>{signals.longUnchanged.map(nameOf).filter(Boolean).join(" · ")}</p>
        </AlertDescription>
      </Alert>
    ),
    signals.noEndDate.length > 0 && (
      <Alert key="no-end">
        <InfinityIcon />
        <AlertTitle>무기한 항목 {signals.noEndDate.length}개</AlertTitle>
        <AlertDescription>
          <p>{signals.noEndDate.map(nameOf).filter(Boolean).join(" · ")}</p>
        </AlertDescription>
      </Alert>
    ),
  ].filter(Boolean);

  if (cards.length === 0) {
    return null;
  }
  return (
    <div className="grid grid-cols-[repeat(auto-fit,minmax(280px,1fr))] gap-3">
      {cards}
    </div>
  );
}

function RuleRow({
  rule,
  onHistory,
}: {
  rule: RecurringView;
  onHistory: () => void;
}) {
  const pause = usePauseRecurring();
  const resume = useResumeRecurring();
  const end = useEndRecurring();
  const occurrence = useOccurrenceAction();
  const ended = isEnded(rule);

  return (
    <TableRow className={cn(ended && "opacity-55")}>
      <TableCell>
        <span className="flex items-center gap-2">
          <span className="truncate">{rule.name}</span>
          <Badge variant="secondary">{RECURRING_KIND_LABELS[rule.kind]}</Badge>
          {ended && <Badge variant="outline">종료됨</Badge>}
          {rule.status === "PAUSED" && <Badge variant="outline">정지</Badge>}
        </span>
      </TableCell>
      <TableCell className="text-muted-foreground">{rule.freqLabel}</TableCell>
      <TableCell className="tabular-nums">{rule.nextDate ?? "—"}</TableCell>
      <TableCell
        className={cn(
          "text-right tabular-nums",
          // 변동은 예상액이다 — 고지서가 오면 고쳐야 한다는 뜻으로 흐리게 둔다.
          rule.amountType === "VARIABLE" && "text-muted-foreground",
        )}
      >
        {amountLabel(rule, formatAmount(rule.amount))}
      </TableCell>
      <TableCell className="text-muted-foreground text-right tabular-nums">
        {formatAmount(rule.monthlyEquivalent)}
      </TableCell>
      <TableCell className="text-right">
        <Menu
          trigger={
            <Button
              type="button"
              variant="ghost"
              size="icon-xs"
              aria-label={`${rule.name} 메뉴`}
            >
              <Ellipsis className="size-3.5" />
            </Button>
          }
        >
          {rule.nextDate && (
            <MenuItem
              onClick={() =>
                occurrence.mutate({
                  recurringId: rule.id,
                  occurrenceDate: rule.nextDate as string,
                  action: "SKIP",
                })
              }
            >
              다음 회차 건너뛰기
            </MenuItem>
          )}
          {rule.status === "PAUSED" ? (
            <MenuItem onClick={() => resume.mutate(rule.id)}>
              다시 시작
            </MenuItem>
          ) : (
            <MenuItem
              onClick={() => pause.mutate({ id: rule.id, from: todayIso() })}
            >
              일시 정지
            </MenuItem>
          )}
          <MenuItem onClick={onHistory}>금액 이력</MenuItem>
          <MenuSeparator />
          {/*
            소급 해지가 아니라 오늘부터의 해지다. 이미 적힌 것을 되돌리는 것은
            사람이 매번 답해야 하는 일이라 기본값을 거짓으로 둔다.
          */}
          <MenuItem
            onClick={() =>
              end.mutate({
                id: rule.id,
                body: { endedOn: todayIso(), revertPostedAfter: false },
              })
            }
          >
            해지 (기록은 그대로)
          </MenuItem>
        </Menu>
      </TableCell>
    </TableRow>
  );
}

/** 금액 이력 + 미발생 이력. 몇 달째 되돌리고 있는지가 여기서 보인다. */
function HistoryModal({
  id,
  onClose,
}: {
  id: number | null;
  onClose: () => void;
}) {
  const { data } = useRecurringHistory(id);
  if (id === null) {
    return null;
  }
  return (
    <Modal
      open
      onOpenChange={(next) => {
        if (!next) {
          onClose();
        }
      }}
      title="금액 이력"
      description="조용히 오르는 것이 구독 관리의 핵심 문제입니다"
    >
      <div className="flex flex-col gap-4">
        <ul className="flex flex-col">
          {(data?.amounts ?? []).map((row) => (
            <li
              key={`${row.effectiveFrom}-${row.amount}`}
              className="border-border flex items-center justify-between gap-3 border-b py-1.5 text-sm last:border-b-0"
            >
              <span>{formatDateHeader(row.effectiveFrom)}</span>
              <span className="tabular-nums">
                {row.changeFromAmount !== null &&
                  `${formatAmount(row.changeFromAmount)} → `}
                {formatAmount(row.amount)}
              </span>
            </li>
          ))}
        </ul>

        {data && data.missed.length > 0 && (
          <section className="flex flex-col gap-1">
            <h3 className="text-[13px] font-semibold">미발생 이력</h3>
            <ul className="text-muted-foreground flex flex-col text-[13px]">
              {data.missed.map((row) => (
                <li key={`${row.occurrenceDate}-${row.action}`}>
                  {row.occurrenceDate} · {MISSED_LABELS[row.action]}
                  {row.note && ` — ${row.note}`}
                </li>
              ))}
            </ul>
          </section>
        )}

        <Modal.Footer>
          <Button type="button" variant="ghost" onClick={onClose}>
            닫기
          </Button>
        </Modal.Footer>
      </div>
    </Modal>
  );
}

const MISSED_LABELS: Record<string, string> = {
  SKIP: "건너뜀",
  UNPAID: "미납",
  REVERTED: "되돌림",
  AMOUNT: "금액 수정",
  MOVE: "날짜 이동",
};

function Stat({
  label,
  value,
  hint,
}: {
  label: string;
  value: string;
  hint?: string;
}) {
  return (
    <div className="bg-card ring-foreground/10 flex flex-col gap-1 rounded-xl p-4 ring-1">
      <span className="text-muted-foreground text-[13px]">{label}</span>
      <span className="text-[19px]/[1.2] font-semibold tracking-[-0.02em] tabular-nums">
        {value}
      </span>
      {hint && (
        <span className="text-muted-foreground text-[13px]">{hint}</span>
      )}
    </div>
  );
}

/**
 * 미납 경고는 예정 화면과 <b>같은 컴포넌트</b>를 쓴다 — 화면마다 다른 문구·다른 액션이면
 * 「무시할 수 없다」는 약속이 화면마다 달라진다.
 */
function toOverdueItems(data: RecurringListResponse) {
  return data.overdue.map((row) => ({
    kind: "RECURRING" as const,
    date: row.occurrenceDate,
    dday: -row.daysOverdue,
    title: row.name,
    amount: row.amount,
    flow: "EXPENSE" as const,
    isTransfer: false,
    overdue: true,
    estimated: false,
    categoryId: null,
    assetId: null,
    assetName: null,
    transactionId: null,
    recurringId: row.recurringId,
    occurrenceDate: row.occurrenceDate,
    statementId: null,
    installmentId: null,
  }));
}
