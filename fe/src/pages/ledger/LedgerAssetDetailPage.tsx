import { ArrowLeft, Pencil, Scale } from "lucide-react";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { LoadingText } from "@/components/ui/loading-text";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { TrendPoint, TrendRange } from "@/features/ledger/api/ledger";
import { AssetEditModal } from "@/features/ledger/components/AssetEditModal";
import { ReconcileModal } from "@/features/ledger/components/ReconcileModal";
import {
  useLedgerAssetDetail,
  useLedgerAssetTransactions,
} from "@/features/ledger/hooks/useLedgerQueries";
import {
  amountToneClass,
  formatAmount,
  formatBalance,
  formatSigned,
} from "@/features/ledger/lib/money";
import { cn } from "@/lib/utils";

const RANGE_TABS: { value: TrendRange; label: string }[] = [
  { value: "DAY", label: "일" },
  { value: "MONTH", label: "월" },
  { value: "YEAR", label: "연" },
];

/**
 * 자산 상세 `/ledger/assets/:id`.
 *
 * <p>맨 아래 내역의 <b>마지막 열이 running balance</b>다. 통장 거래내역처럼 줄마다 그 시점의
 * 잔액이 있어야 "어디서부터 어긋났나"를 눈으로 따라갈 수 있다.
 *
 * <p>연결 계좌를 보고 있다면 <b>그 계좌를 물고 있는 체크카드의 거래도 함께</b> 온다 —
 * 카드로 쓴 돈은 이 계좌에서 빠지기 때문이다(D-4). 빼고 그리면 마지막 잔액이 자산 목록과
 * 어긋난다.
 */
export function LedgerAssetDetailPage() {
  const { assetId } = useParams();
  const id = Number(assetId);
  const [range, setRange] = useState<TrendRange>("MONTH");
  const [reconcileOpen, setReconcileOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);

  const detail = useLedgerAssetDetail(id, range);
  const rows = useLedgerAssetTransactions(id);

  const asset = detail.data?.asset;
  const headline =
    asset?.unpaidAmount != null
      ? -asset.unpaidAmount
      : (asset?.balance ?? null);

  return (
    <div className="mx-auto flex max-w-[880px] flex-col gap-5">
      <PageHeader
        title={asset?.name ?? "자산"}
        description={
          asset?.linkedAssetName && `${asset.linkedAssetName}에서 출금`
        }
        actions={
          <>
            {/* 잔액을 갖는 자산에만 맞출 것이 있다. 카드의 차이는 청구서로 푼다(v1.5). */}
            {asset?.balance != null && (
              <Button
                type="button"
                variant="outline"
                onClick={() => setReconcileOpen(true)}
              >
                <Scale className="size-4" />
                잔액 맞추기
              </Button>
            )}
            {/* 이름·그룹·연결 계좌를 고치고, 해지·삭제도 여기서 한다. */}
            {asset && (
              <Button
                type="button"
                variant="outline"
                onClick={() => setEditOpen(true)}
              >
                <Pencil className="size-4" />
                수정
              </Button>
            )}
            <Button
              type="button"
              variant="ghost"
              render={<Link to="/ledger/assets" />}
            >
              <ArrowLeft className="size-4" />
              자산 목록
            </Button>
          </>
        }
      />

      {detail.isPending && <LoadingText />}
      {detail.isError && (
        <Alert variant="destructive">자산을 불러오지 못했어요.</Alert>
      )}

      {detail.data && (
        <>
          <div className="flex flex-col gap-1">
            {headline === null ? (
              // 체크카드에는 잔액이라는 개념이 없다. 0을 적으면 「돈이 없다」로 읽힌다.
              <p className="text-muted-foreground text-sm">
                이 카드는 잔액을 갖지 않아요 — 결제액은 연결 계좌에서 빠집니다.
              </p>
            ) : (
              <p
                className={cn(
                  "text-[32px]/[1.1] font-semibold tracking-[-0.02em] tabular-nums",
                  headline < 0 && "text-destructive",
                )}
              >
                {formatBalance(headline)}
              </p>
            )}
          </div>

          <section className="flex flex-col gap-3">
            <Tabs
              value={range}
              onValueChange={(value) => setRange(value as TrendRange)}
            >
              <TabsList>
                {RANGE_TABS.map((tab) => (
                  <TabsTrigger key={tab.value} value={tab.value}>
                    {tab.label}
                  </TabsTrigger>
                ))}
              </TabsList>
            </Tabs>
            <TrendChart points={detail.data.trend} />
          </section>

          {detail.data.categoryShare.length > 0 && (
            <section className="flex flex-col gap-2">
              <h2 className="text-[13px] font-semibold">이 자산의 지출 분포</h2>
              <CategoryShareBars share={detail.data.categoryShare} />
              <p className="text-muted-foreground text-[13px]">
                카드 대금 이체는 지출이 아니라 분포에서 빠집니다.
              </p>
            </section>
          )}

          <section className="flex flex-col gap-2">
            <h2 className="text-[13px] font-semibold">내역</h2>
            {rows.isPending && <LoadingText />}
            {rows.data && rows.data.length === 0 && (
              <p className="text-muted-foreground text-sm">
                아직 이 자산에 적힌 거래가 없어요.
              </p>
            )}
            {rows.data && rows.data.length > 0 && (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>날짜</TableHead>
                    <TableHead>내용</TableHead>
                    <TableHead className="text-right">금액</TableHead>
                    <TableHead className="text-right">잔액</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {rows.data.map(({ transaction, runningBalance }) => {
                    // 옆 칸이 잔액이다 — 부호가 어긋나면 두 열이 서로 다른 말을 한다.
                    const flow = transaction.type;
                    return (
                      <TableRow key={transaction.id}>
                        <TableCell className="tabular-nums">
                          {transaction.occurredOn.slice(5)}
                        </TableCell>
                        <TableCell>
                          <span className="flex items-center gap-2">
                            {transaction.title ?? "제목 없음"}
                            {transaction.status === "SCHEDULED" && (
                              <span className="text-muted-foreground text-[13px]">
                                예정
                              </span>
                            )}
                          </span>
                        </TableCell>
                        <TableCell
                          className={cn(
                            "text-right tabular-nums",
                            amountToneClass(flow),
                          )}
                        >
                          {formatSigned(transaction.amount, flow)}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          {runningBalance === null ? (
                            <span className="text-muted-foreground">—</span>
                          ) : (
                            formatBalance(runningBalance)
                          )}
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            )}
          </section>
        </>
      )}

      {asset?.balance != null && (
        <ReconcileModal
          open={reconcileOpen}
          onOpenChange={setReconcileOpen}
          assetId={asset.id}
          assetName={asset.name}
          derivedBalance={asset.balance}
        />
      )}

      {asset && (
        // key로 다시 만든다 — 저장 뒤 새 값이 폼의 초기값이 되어야 한다.
        <AssetEditModal
          key={`${asset.id}:${asset.name}:${asset.hidden}`}
          open={editOpen}
          onOpenChange={setEditOpen}
          asset={asset}
          deletable={detail.data?.deletable ?? false}
          deleteBlockers={detail.data?.deleteBlockers ?? []}
        />
      )}
    </div>
  );
}

/**
 * 추이 — SVG 꺾은선.
 *
 * <p>차트 라이브러리를 들이지 않는다. 점 몇 개를 잇는 선 하나에 번들을 늘릴 이유가 없고,
 * 색도 `--primary` 하나만 쓴다(화면 설계 §1의 모노크롬 램프).
 */
function TrendChart({ points }: { points: TrendPoint[] }) {
  if (points.length < 2) {
    return (
      <p className="text-muted-foreground text-[13px]">
        추이를 그리기에는 기록이 아직 적어요.
      </p>
    );
  }
  const values = points.map((point) => point.balance);
  const min = Math.min(...values, 0);
  const max = Math.max(...values, 0);
  const span = max - min || 1;
  const step = 100 / (points.length - 1);

  const path = points
    .map((point, index) => {
      const x = index * step;
      // SVG는 위가 0이다 — 값이 클수록 y가 작아야 한다.
      const y = 40 - ((point.balance - min) / span) * 40;
      return `${index === 0 ? "M" : "L"}${x.toFixed(2)},${y.toFixed(2)}`;
    })
    .join(" ");

  return (
    <svg
      viewBox="0 0 100 40"
      preserveAspectRatio="none"
      role="img"
      aria-label="잔액 추이"
      className="border-border h-24 w-full rounded-lg border p-1"
    >
      <path
        d={path}
        fill="none"
        stroke="var(--primary)"
        strokeWidth="0.8"
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  );
}

function CategoryShareBars({
  share,
}: {
  share: {
    categoryId: number | null;
    categoryName: string | null;
    amount: number;
  }[];
}) {
  const top = share.slice(0, 4);
  const max = Math.max(...top.map((item) => item.amount), 1);
  return (
    <ul className="flex flex-col gap-1.5">
      {top.map((item) => (
        <li
          key={item.categoryId ?? "uncategorized"}
          className="flex flex-col gap-1"
        >
          <span className="flex items-center justify-between text-[13px]">
            {/* 미분류를 빼지 않는다 — 안 보이면 정리하지 않는다. */}
            <span>{item.categoryName ?? "미분류"}</span>
            <span className="tabular-nums">{formatAmount(item.amount)}</span>
          </span>
          <span
            aria-hidden
            className="bg-muted h-1.5 overflow-hidden rounded-full"
          >
            <span
              className="block h-full rounded-full"
              style={{
                width: `${(item.amount / max) * 100}%`,
                // 새 hue를 만들지 않는다 — primary 한 색의 농도만 바꾼다.
                background: "var(--primary)",
              }}
            />
          </span>
        </li>
      ))}
    </ul>
  );
}
