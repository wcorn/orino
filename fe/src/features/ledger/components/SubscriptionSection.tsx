import { House, TriangleAlert } from "lucide-react";
import { useState } from "react";

import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { LoadingText } from "@/components/ui/loading-text";
import { cn } from "@/lib/utils";

import type {
  SubscriptionBaselineChange,
  SubscriptionMonth,
} from "../api/ledger";
import { useLedgerSubscription } from "../hooks/useLedgerQueries";
import { formatAmount } from "../lib/money";
import { manwon, monthLabel } from "../lib/subscription";
import { SubscriptionBaselineModal } from "./SubscriptionBaselineModal";

/** 월 · 입금 · 인정 · 비고. 비고가 가장 길어 남는 폭을 가져간다. */
const ROW =
  "grid grid-cols-[88px_1fr_1fr_minmax(0,1.4fr)] items-start gap-2 py-2";

/**
 * 청약 인정 현황(화면 설계 §14.3, `LDG-007`).
 *
 * <p><b>정본은 청약홈이다.</b> 여기 있는 숫자는 전부 기준값 + 이후 원장으로 센 추정이라,
 * 값마다 「추정」을 붙이고 어디에서도 단정하지 않는다(D-16).
 *
 * <p>기준값이 없으면 <b>숫자를 그리지 않는다.</b> 가입일부터 센 틀린 숫자보다 빈칸이 낫다 —
 * 수년째 넣은 청약의 과거는 원장에 없다.
 */
export function SubscriptionSection({ assetId }: { assetId: number }) {
  const { data, isPending, isError } = useLedgerSubscription(assetId);
  const [baselineOpen, setBaselineOpen] = useState(false);
  // 다시 맞춘 직후 한 번만 보여준다. 기준값을 조용히 덮으면 추정이 틀렸다는 사실도 사라진다.
  const [change, setChange] = useState<SubscriptionBaselineChange | null>(null);

  if (isPending) {
    return <LoadingText />;
  }
  if (isError) {
    return (
      <Alert variant="destructive">청약 인정 현황을 불러오지 못했어요.</Alert>
    );
  }

  const modal = (
    <SubscriptionBaselineModal
      // 저장한 값이 다음에 열 때의 초기값이 되어야 한다.
      key={
        data.baseline
          ? `${data.baseline.count}:${data.baseline.amount}:${data.baseline.throughMonth}`
          : "none"
      }
      open={baselineOpen}
      onOpenChange={setBaselineOpen}
      assetId={assetId}
      baseline={data.baseline}
      onSaved={setChange}
    />
  );

  if (data.baseline === null || data.estimate === null) {
    return (
      <section className="border-border flex flex-col items-start gap-3 rounded-lg border px-4 py-4">
        <h2 className="flex items-center gap-2 text-[13px] font-semibold">
          <House className="size-4 opacity-70" />
          청약 인정 현황
        </h2>
        <p className="text-muted-foreground text-sm">
          청약홈(applyhome.co.kr)에서 본 인정 회차·금액을 한 번 적으면, 그 뒤는
          가계부가 이어서 셉니다.
        </p>
        <Button
          type="button"
          variant="outline"
          onClick={() => setBaselineOpen(true)}
        >
          적기
        </Button>
        {modal}
      </section>
    );
  }

  const { baseline, estimate } = data;
  return (
    <section className="flex flex-col gap-3">
      {change?.before && (
        <Alert variant="info">
          추정 {change.before.count}회 → 청약홈 {change.after.count}회로
          맞췄어요
        </Alert>
      )}

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <EstimateStat label="인정 회차" value={`${estimate.count}회`} />
        <EstimateStat label="인정 금액" value={formatAmount(estimate.amount)} />
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-muted-foreground text-[13px]">
          청약홈 {monthLabel(baseline.throughMonth)}분까지 {baseline.count}회 +
          이후 원장 {estimate.count - baseline.count}회
        </p>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => setBaselineOpen(true)}
        >
          청약홈 값으로 다시 맞추기
        </Button>
      </div>

      <div className="flex flex-col gap-1">
        <p className="text-muted-foreground text-[13px]">
          월 인정 한도 {formatAmount(data.monthlyCap)} · 같은 달 여러 번 넣어도
          1회 · 선납·연체는 계산하지 않아요
        </p>
        {data.months.length === 0 ? (
          <p className="text-muted-foreground text-sm">
            청약홈 값 이후로 센 달이 아직 없어요.
          </p>
        ) : (
          <div role="table" aria-label="월별 인정" className="text-[13px]">
            <div
              role="row"
              className={cn(
                ROW,
                "border-border text-muted-foreground border-b",
              )}
            >
              <span role="columnheader">월</span>
              <span role="columnheader" className="text-right">
                입금
              </span>
              <span role="columnheader" className="text-right">
                인정
              </span>
              <span role="columnheader">비고</span>
            </div>
            {data.months.map((month) => (
              <div
                key={month.month}
                role="row"
                className={cn(ROW, "border-border border-b last:border-b-0")}
              >
                <span role="cell" className="tabular-nums">
                  {month.month.replace("-", ".")}
                </span>
                <span role="cell" className="text-right tabular-nums">
                  {formatAmount(month.deposited)}
                </span>
                <span role="cell" className="text-right tabular-nums">
                  {formatAmount(month.recognized)}
                </span>
                <span role="cell">
                  <MonthNote month={month} />
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
      {modal}
    </section>
  );
}

function EstimateStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-muted flex flex-col gap-0.5 rounded-lg px-4 py-3">
      <span className="text-muted-foreground text-[13px]">{label}</span>
      <span className="flex items-center gap-2">
        <span className="text-heading font-semibold tabular-nums">{value}</span>
        <Badge variant="secondary">추정</Badge>
      </span>
    </div>
  );
}

/**
 * 원장만으로는 판단할 수 없는 달. 선납·연체를 계산하지 않는 대신 <b>드러낸다</b>(D-16) —
 * 선납인지는 은행에 어떻게 지정했느냐로 갈려 원장에 없는 정보다.
 */
function MonthNote({ month }: { month: SubscriptionMonth }) {
  if (month.flag === "OVER_CAP") {
    return (
      <span className="flex items-start gap-1">
        <TriangleAlert className="text-warning mt-0.5 size-3.5 shrink-0" />
        {manwon(month.recognized)}까지만 셌어요 — 선납으로 지정했다면 청약홈
        값으로 다시 맞추세요
      </span>
    );
  }
  if (month.flag === "NO_DEPOSIT") {
    return (
      <span className="text-muted-foreground">
        입금 없음 — 인정일이 늦춰질 수 있어요
      </span>
    );
  }
  return null;
}
